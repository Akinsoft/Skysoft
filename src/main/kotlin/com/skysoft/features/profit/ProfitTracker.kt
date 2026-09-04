package com.skysoft.features.profit

import com.skysoft.data.skyblock.pets.PetRepository
import com.skysoft.config.ProfitTrackerConfig
import com.skysoft.config.ProfitTrackerPriceSource
import com.skysoft.config.SkysoftConfigGui
import com.skysoft.data.ProfileStorage
import com.skysoft.data.ProfileStorageApi
import com.skysoft.data.hypixel.HypixelLocationState
import com.skysoft.data.hypixel.SkyBlockProfileApi
import com.skysoft.data.skyblock.ItemListEntryKind
import com.skysoft.data.skyblock.MayorPerkApi
import com.skysoft.data.skyblock.SkyBlockAreaState
import com.skysoft.data.skyblock.SkyBlockDataRepository
import com.skysoft.data.skyblock.SkyBlockItemChangeBatch
import com.skysoft.data.skyblock.SkyBlockItemChangeSource
import com.skysoft.data.skyblock.SkyBlockItemNames
import com.skysoft.data.skyblock.SKYBLOCK_COINS
import com.skysoft.data.skyblock.SkyBlockCurrencyChanges
import com.skysoft.data.skyblock.SkyBlockItemUtilities.extraAttributes
import com.skysoft.data.skyblock.SkyBlockItemUtilities.skyBlockEnchantments
import com.skysoft.data.skyblock.SkyBlockRecipe
import com.skysoft.data.skyblock.SkyBlockSlayerType
import com.skysoft.data.skyblock.SlayerMessageParser
import com.skysoft.data.skyblock.SlayerQuestState
import com.skysoft.data.skyblock.price.BazaarPriceData
import com.skysoft.data.skyblock.price.SkyBlockPriceData
import com.skysoft.features.event.diana.DianaEventState
import com.skysoft.features.event.diana.MythologicalRitualMessageTracker
import com.skysoft.features.event.diana.UNRESOLVED_MYTHOLOGICAL_RITUAL_EVENT_KEY
import com.skysoft.features.slayer.SlayerTimeToKill
import com.skysoft.utils.MinecraftClient
import com.skysoft.utils.SkysoftClientEvents
import com.skysoft.utils.animation.TimedHighlightTracker
import com.skysoft.utils.chat.ChatEvents
import com.skysoft.utils.chat.ChatMessageVisibility
import java.time.LocalDate
import kotlin.math.roundToLong
import net.fabricmc.fabric.api.event.client.player.ClientPlayerBlockBreakEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks

object ProfitTracker {
    private val configs get() = SkysoftConfigGui.config().profitTrackers
    private val sessionStats = mutableMapOf<String, ProfileStorage.ProfitTrackerStats>()
    private val questCostCapture = SlayerQuestCostCapture()
    private var attributionPreset: ProfitTrackerPreset? = null
    private var inactiveAttributionTicks = 0
    private var durationPreset: ProfitTrackerPreset? = null
    private var previousPreset: ProfitTrackerPreset? = null
    private var previousPresetLeftAtMillis = 0L
    private val uptime = ProfitUptimeTracker<ProfitTrackerTarget>(
        pauseAfterMillis = { target -> target.config.pauseAfterMillis },
        onUptimeChanged = { target, change ->
            update(target) { stats ->
                stats.activeMillis = (stats.activeMillis + change).coerceAtLeast(0L)
            }
        },
    )
    private val itemTracking = ProfitTrackerItemTracking()
    private val craftingReconciliation = ProfitCraftingReconciliation<ProfitTrackerTarget>()
    private var dropCatalogVersion = -1L
    private var trackedItems = emptyMap<ProfitTrackerPreset, Set<String>>()
    private val pendingReplenishCosts = mutableMapOf<ReplenishCrop, Int>()
    private val foragingTreeGiftParser = ForagingTreeGiftParser()
    internal val itemQuantityHighlights = TimedHighlightTracker<Pair<String, String>>()

    fun register() {
        ProfileStorageApi.registerConsumer("Profit Tracker") { configs.isAnyEnabled() }
        SkyBlockDataRepository.Demand.register("Profit Tracker") { configs.isAnyEnabled() }
        MayorPerkApi.registerConsumer("Profit Tracker") { configs.mythologicalRitual.enabled }
        PetRepository.registerConsumer("Profit Tracker") { configs.isAnyEnabled() }
        itemTracking.register({ configs.isAnyEnabled() }, ::recordItemChanges)
        ClientPlayerBlockBreakEvents.AFTER.register { _, _, _, state -> recordFarmingBlock(state.block) }
        SkyBlockCurrencyChanges.onChange("Profit Tracker currency changes", { configs.isAnyEnabled() }) { change ->
            questCostCapture.recordChange(change.currency, change.amount)
            if (change.currency != SKYBLOCK_COINS) return@onChange
            val preset = attributionPreset?.takeIf { presetConfig(it).enabled } ?: currentPreset
            coinTrackingTargets(change.amount, preset, uptime::lastActivityAt).forEach { target ->
                uptime.markActivity(target)
                update(target) { stats -> stats.coins += change.amount }
            }
        }
        IMMEDIATE_DROP_PRESETS.forEach { preset ->
            ChatEvents.onVisibleMessage(
                "${preset.displayName} Profit Tracker chat drops",
                { isInPresetArea(preset) },
            ) { message ->
                recordImmediateMessage(preset, message.cleanText)
                ChatMessageVisibility.SHOW
            }
        }
        ChatEvents.onVisibleMessage(
            "Profit Tracker auto-slayer bank costs",
            { configs.isAnyEnabled() },
        ) { message ->
            if (message.isSystemLike) {
                SlayerMessageParser.parseAutoSlayerBankCost(message.cleanText)?.let { cost ->
                    questCostCapture.recordCost(SKYBLOCK_COINS, cost)
                }
            }
            ChatMessageVisibility.SHOW
        }
        SlayerQuestState.onQuestStarted("Profit Tracker quest start") {
            if (configs.isAnyEnabled()) questCostCapture.questStarted()
        }
        SlayerQuestState.onQuestComplete("Profit Tracker quest completion") { quest ->
            questCostCapture.clear()
            if (!configs.isAnyEnabled()) return@onQuestComplete
            val preset = quest.slayerType?.let(ProfitTrackerPreset::fromSlayer)?.takeIf(::isInPresetArea)
                ?: return@onQuestComplete
            val target = ProfitTrackerTarget.preset(preset)
            uptime.markActivity(target)
            update(target) { stats -> stats.actions++ }
        }
        SkysoftClientEvents.onEndTick(
            "Profit Tracker activity state",
            isActive = {
                configs.isAnyEnabled() || attributionPreset != null || durationPreset != null ||
                    uptime.hasUnconfirmedUptime
            },
        ) { minecraft ->
            fishingHookPreset?.let { uptime.refreshActivity(ProfitTrackerTarget.preset(it)) }
            val locationPreset = currentPreset
            if (locationPreset != durationPreset) {
                durationPreset?.let { previous ->
                    previousPreset = previous
                    previousPresetLeftAtMillis = System.currentTimeMillis()
                }
                durationPreset = locationPreset
                uptime.resetTickProgress()
            }
            uptime.tick(activeProfitTrackerTargets(locationPreset), minecraft.isWindowActive)
            val questPreset = SlayerQuestState.slayerType?.let(ProfitTrackerPreset::fromSlayer)?.takeIf(::isInPresetArea)
            val activePreset = questPreset ?: locationPreset?.takeIf {
                it == ProfitTrackerPreset.FARMING ||
                    it == ProfitTrackerPreset.FISHING ||
                    it == ProfitTrackerPreset.MYTHOLOGICAL_RITUAL
            }
            if (activePreset != null) {
                attributionPreset = activePreset
                inactiveAttributionTicks = 0
            } else if (attributionPreset != null && ++inactiveAttributionTicks > ATTRIBUTION_GRACE_TICKS) {
                attributionPreset = null
                inactiveAttributionTicks = 0
            }
            questCostCapture.clearExpired()
            val preset = questPreset ?: return@onEndTick
            val cost = questCostCapture.take() ?: return@onEndTick
            val target = ProfitTrackerTarget.preset(preset)
            uptime.markActivity(target)
            update(target) { stats ->
                stats.costs[cost.currency] = stats.costs.getOrDefault(cost.currency, 0L) + cost.amount
            }
        }
        SkysoftClientEvents.onDisconnect("Profit Tracker disconnect reset", ::resetTransientState)
        SkyBlockProfileApi.onProfileChange("Profit Tracker profile reset", { true }) { resetTransientState() }
        registerProfitTrackerHud()
    }

    internal fun isInPresetArea(preset: ProfitTrackerPreset): Boolean =
        presetConfig(preset).enabled && locationPreset == preset

    private val currentPreset: ProfitTrackerPreset?
        get() = locationPreset?.takeIf { preset -> presetConfig(preset).enabled }

    private val locationPreset: ProfitTrackerPreset?
        get() = ProfitTrackerPresets.forLocation(
            HypixelLocationState.currentIsland?.displayName,
            SkyBlockAreaState.currentArea,
            ProfitTrackerPreset.MYTHOLOGICAL_RITUAL.takeIf {
                presetConfig(it).enabled &&
                    DianaEventState.isOnHub() &&
                    DianaEventState.isMythologicalRitualActive() &&
                    DianaEventState.hasSpadeInHotbar()
            }
                ?: SlayerQuestState.slayerType?.let(ProfitTrackerPreset::fromSlayer)
                ?: fishingHookPreset
                ?: activeFishingPreset,
        )

    private val fishingHookPreset: ProfitTrackerPreset?
        get() = ProfitTrackerPreset.FISHING.takeIf {
            presetConfig(it).enabled &&
                HypixelLocationState.inSkyBlock &&
                Minecraft.getInstance().player?.fishing != null
        }

    private val activeFishingPreset: ProfitTrackerPreset?
        get() {
            val preset = ProfitTrackerPreset.FISHING
            val config = presetConfig(preset)
            return preset.takeIf {
                config.enabled &&
                    isProfitTimerActive(
                        uptime.lastActivityAt(ProfitTrackerTarget.preset(preset)),
                        System.currentTimeMillis(),
                        config.pauseAfterMillis,
                    )
            }
        }

    internal fun selectedPreset(): ProfitTrackerPreset? =
        SlayerQuestState.slayerType?.let(ProfitTrackerPreset::fromSlayer)?.takeIf(::isInPresetArea)
            ?: currentPreset
            ?: ProfileStorageApi.storage.profitTracker.lastPreset
                .let { stored -> ProfitTrackerPreset.entries.firstOrNull { it.name == stored } }
                ?.takeIf(::isInPresetArea)

    internal fun stats(target: ProfitTrackerTarget): ProfileStorage.ProfitTrackerStats = when (displayPeriod(target)) {
        ProfitTrackingPeriod.SESSION -> sessionStats.getOrPut(target.storageKey, ::newProfitTrackerStats)
        ProfitTrackingPeriod.TODAY -> todayStats(target)
        ProfitTrackingPeriod.MAYOR -> requireNotNull(mythologicalRitualMayorStats(target))
        ProfitTrackingPeriod.TOTAL ->
            ProfileStorageApi.storage.profitTracker.totals.getOrPut(target.storageKey, ::newProfitTrackerStats)
    }

    internal fun isTimerPaused(target: ProfitTrackerTarget): Boolean =
        uptime.isPaused(target, Minecraft.getInstance().isWindowActive)

    internal fun displayPeriod(target: ProfitTrackerTarget): ProfitTrackingPeriod =
        ProfileStorageApi.storage.profitTracker.displayPeriods[target.storageKey]
            ?.let { period -> target.trackingPeriods.firstOrNull { it.name == period } }
            ?: if (target.preset == ProfitTrackerPreset.MYTHOLOGICAL_RITUAL) {
                ProfitTrackingPeriod.MAYOR
            } else {
                ProfitTrackingPeriod.SESSION
            }

    internal fun resetDisplayed(target: ProfitTrackerTarget) {
        itemTracking.clear()
        craftingReconciliation.clear(target)
        pendingReplenishCosts.clear()
        val period = displayPeriod(target)
        when (period) {
            ProfitTrackingPeriod.SESSION -> sessionStats[target.storageKey]?.clear()
            ProfitTrackingPeriod.TODAY -> {
                todayStats(target).clear()
                ProfileStorageApi.markDirty()
            }
            ProfitTrackingPeriod.MAYOR -> {
                mythologicalRitualMayorStats(target)?.clear()
                ProfileStorageApi.markDirty()
            }
            ProfitTrackingPeriod.TOTAL -> {
                ProfileStorageApi.storage.profitTracker.totals[target.storageKey]?.clear()
                ProfileStorageApi.markDirty()
            }
        }
        target.slayerType?.let { SlayerTimeToKill.reset(it, period) }
    }

    internal fun modifyItemAmount(target: ProfitTrackerTarget, itemId: String, amount: Long) {
        if (amount == 0L) return
        if (amount > 0L) {
            if (ProfitTrackerItemCustomizations.isExcluded(target, itemId)) {
                ProfitTrackerItemCustomizations.restore(target, itemId)
            }
            if (itemId !in trackedItemIds(target)) ProfitTrackerItemCustomizations.addCustomItem(target, itemId)
        }
        update(target, listOf(itemId)) { stats ->
            val current = stats.itemCounts.getOrDefault(itemId, 0L)
            val updated = if (amount > 0L) {
                current + amount.coerceAtMost(Long.MAX_VALUE - current)
            } else {
                (current + amount).coerceAtLeast(0L)
            }
            if (updated == 0L) stats.itemCounts.remove(itemId) else stats.itemCounts[itemId] = updated
        }
    }

    internal fun deleteCustomTrackerData(target: ProfitTrackerTarget) {
        require(target.custom != null)
        val key = target.storageKey
        sessionStats.remove(key)
        craftingReconciliation.clear(target)
        uptime.clear(target)
        val storage = ProfileStorageApi.allStorage
        val profiles = storage.profiles.values + storage.players.values.flatMap { it.profiles.values }
        profiles.forEach { profile ->
            with(profile.profitTracker) {
                totals.remove(key)
                today.remove(key)
                displayPeriods.remove(key)
                itemCustomizations.remove(key)
            }
        }
        ProfileStorageApi.markDirty()
    }

    private fun recordFarmingBlock(block: Block) {
        if (!presetConfig(ProfitTrackerPreset.FARMING).enabled ||
            currentPreset != ProfitTrackerPreset.FARMING || !isFarmingCropBlock(block)
        ) return
        uptime.markActivity(ProfitTrackerTarget.preset(ProfitTrackerPreset.FARMING))
        val minecraft = Minecraft.getInstance()
        if (minecraft.player?.mainHandItem?.extraAttributes()?.skyBlockEnchantments()?.containsKey("replenish") != true) {
            return
        }
        replenishCrop(block, minecraft.level?.gameTime ?: 0L)?.let { crop ->
            pendingReplenishCosts[crop] = pendingReplenishCosts.getOrDefault(crop, 0) + 1
        }
    }

    private fun recordImmediateMessage(preset: ProfitTrackerPreset, message: String) {
        val activityDrop = when (preset) {
            ProfitTrackerPreset.FARMING -> parseFarmingChatDrop(message)
            ProfitTrackerPreset.FORAGING -> foragingTreeGiftParser.parse(message)
            ProfitTrackerPreset.MINING -> parseMiningChatDrop(message)
            else -> null
        }
        val playerName = Minecraft.getInstance().player?.gameProfile?.name
        val dyeDrop = playerName?.let { parseDyeChatDrop(message, it) }
        val drop = activityDrop ?: dyeDrop
        drop?.let {
            val itemId = SkyBlockItemNames.itemId(it.displayName) ?: return@let
            val presetTarget = ProfitTrackerTarget.preset(preset)
            val targets = buildList {
                if (activityDrop != null || itemId in trackedItemIds(presetTarget)) add(presetTarget)
                addAll(matchingCustomTrackerTargets().filter { target -> itemId in trackedItemIds(target) })
            }
            if (targets.isEmpty()) return@let
            itemTracking.suppressGain(itemId, it.amount)
            targets.forEach { target ->
                uptime.markActivity(target)
                update(target, listOf(itemId)) { stats -> applyTrackedItemChanges(stats, mapOf(itemId to it.amount)) }
            }
        }
        val pest = if (preset == ProfitTrackerPreset.FARMING) parseCountedPestKill(message) else null
        val actionOccurred = when (preset) {
            ProfitTrackerPreset.FARMING -> pest != null
            ProfitTrackerPreset.MYTHOLOGICAL_RITUAL -> MythologicalRitualMessageTracker.isBurrowMessage(message)
            else -> false
        }
        if (actionOccurred) {
            val target = ProfitTrackerTarget.preset(preset)
            uptime.markActivity(target)
            update(target) { stats ->
                stats.actions++
                if (pest != null) stats.pestKills.merge(pest, 1L, Long::plus)
            }
        }
    }

    internal fun unitValue(target: ProfitTrackerTarget, itemId: String): Double? {
        val sourcePrice = profitTrackerSourcePrice(
            SkyBlockPriceData.getBazaarPrice(itemId),
            SkyBlockPriceData.getNpcSellPrices(itemId).coins,
            ProfitTrackerItemCustomizations.priceSource(target, itemId),
        )
        return sourcePrice?.takeIf { it > 0.0 }
            ?: SkyBlockPriceData.getLowestBin(itemId)?.toDouble()?.takeIf { it > 0.0 }
    }

    internal fun trackedItemIds(target: ProfitTrackerTarget): Set<String> {
        if (target.custom != null) return ProfitTrackerItemCustomizations.customItems(target)
        if (dropCatalogVersion != SkyBlockDataRepository.snapshotVersion) rebuildDropCatalog()
        return trackedItems[target.preset].orEmpty() + ProfitTrackerItemCustomizations.customItems(target)
    }

    private fun recordItemChanges(batch: SkyBlockItemChangeBatch) {
        val unsuppressedChanges = itemTracking.consume(batch)
        if (batch.source == SkyBlockItemChangeSource.INVENTORY && MinecraftClient.screen() is AbstractContainerScreen<*>) {
            return
        }
        val targets = buildList {
            itemAttributionPreset(batch)?.let { add(ProfitTrackerTarget.preset(it)) }
            addAll(matchingCustomTrackerTargets())
        }.distinct()
        targets.forEach { target ->
            val allowedItems = trackedItemIds(target)
            val changes = craftingReconciliation
                .reconcile(target, batch.source, unsuppressedChanges, allowedItems)
                .withReplenishCosts(target)
            if (changes.isEmpty()) return@forEach
            uptime.markActivity(target)
            update(target, changes.keys) { stats -> applyTrackedItemChanges(stats, changes) }
        }
    }

    private fun Map<String, Int>.withReplenishCosts(target: ProfitTrackerTarget): Map<String, Int> {
        if (target.preset != ProfitTrackerPreset.FARMING || pendingReplenishCosts.isEmpty()) return this
        val costs = pendingReplenishCosts.filterKeys { crop -> getOrDefault(crop.harvestItemId, 0) > 0 }
        if (costs.isEmpty()) return this
        pendingReplenishCosts.keys.removeAll(costs.keys)
        return toMutableMap().apply {
            costs.forEach { (crop, amount) -> merge(crop.costItemId, -amount, Int::plus) }
        }.filterValues { it != 0 }
    }

    private fun update(
        target: ProfitTrackerTarget,
        changedItemIds: Iterable<String> = emptyList(),
        action: (ProfileStorage.ProfitTrackerStats) -> Unit,
    ) {
        action(sessionStats.getOrPut(target.storageKey, ::newProfitTrackerStats))
        action(todayStats(target))
        mythologicalRitualMayorStats(target)?.let(action)
        action(ProfileStorageApi.storage.profitTracker.totals.getOrPut(target.storageKey, ::newProfitTrackerStats))
        target.preset?.let { ProfileStorageApi.storage.profitTracker.lastPreset = it.name }
        ProfileStorageApi.markDirty()
        if (target.config.details.highlightChanges) {
            changedItemIds.forEach { itemId -> itemQuantityHighlights.highlight(target.storageKey to itemId) }
        }
    }

    private fun todayStats(target: ProfitTrackerTarget): ProfileStorage.ProfitTrackerStats {
        val tracker = ProfileStorageApi.storage.profitTracker
        val today = LocalDate.now().toEpochDay()
        if (didRollProfitTrackerToday(tracker, today)) ProfileStorageApi.markDirty()
        return tracker.today.getOrPut(target.storageKey, ::newProfitTrackerStats)
    }

    private fun rebuildDropCatalog() {
        trackedItems = ProfitTrackerPreset.entries.associateWith { preset ->
            val slayerType = preset.slayerType
            val directDrops = SkyBlockDataRepository.entries.asSequence()
                .filter { entry -> entry.key.kind == ItemListEntryKind.SKYBLOCK }
                .filter { entry ->
                    SkyBlockDataRepository.info(entry.key)?.dropSources.orEmpty().any { source ->
                        val entityType = SkyBlockDataRepository.entity(source.entityId)?.type
                        when (preset) {
                            ProfitTrackerPreset.FISHING ->
                                entityType.equals(SEA_CREATURE_ENTITY_TYPE, ignoreCase = true)
                            ProfitTrackerPreset.MYTHOLOGICAL_RITUAL ->
                                entityType.equals(MYTHOLOGICAL_CREATURE_ENTITY_TYPE, ignoreCase = true)
                            else ->
                                slayerType != null &&
                                    SkyBlockSlayerType.fromBossEntityId(source.entityId)?.first == slayerType
                        }
                    }
                }
                .map { entry -> entry.key.id }
                .toSet()
            val presetItems = directDrops + ProfitTrackerPresets.get(preset).additionalItems
            val compactedDrops = SkyBlockDataRepository.entries.asSequence()
                .filter { entry -> entry.key.kind == ItemListEntryKind.SKYBLOCK }
                .filter { entry ->
                    SkyBlockDataRepository.recipesFor(entry.key)
                        .filterIsInstance<SkyBlockRecipe.Crafting>()
                        .any { recipe -> recipe.ingredients.map { it.id }.distinct().singleOrNull() in presetItems }
                }
                .map { entry -> entry.key.id }
            presetItems + compactedDrops
        }
        dropCatalogVersion = SkyBlockDataRepository.snapshotVersion
    }

    private fun itemAttributionPreset(batch: SkyBlockItemChangeBatch): ProfitTrackerPreset? {
        val current = attributionPreset?.takeIf { presetConfig(it).enabled } ?: currentPreset
        if (current != null) return current
        if (batch.source != SkyBlockItemChangeSource.SACKS) return null
        val windowMillis = (batch.sackWindowSeconds ?: return null) * MILLIS_PER_SECOND
        return previousPreset?.takeIf {
            System.currentTimeMillis() - previousPresetLeftAtMillis <= windowMillis
        }
    }

    private fun resetTransientState() {
        questCostCapture.clear()
        attributionPreset = null
        inactiveAttributionTicks = 0
        durationPreset = null
        previousPreset = null
        previousPresetLeftAtMillis = 0L
        uptime.clear()
        itemTracking.clear()
        craftingReconciliation.clear()
        pendingReplenishCosts.clear()
        foragingTreeGiftParser.clear()
        itemQuantityHighlights.clear()
    }
}

private fun mythologicalRitualMayorStats(target: ProfitTrackerTarget): ProfileStorage.ProfitTrackerStats? {
    if (target.preset != ProfitTrackerPreset.MYTHOLOGICAL_RITUAL) return null
    val tracker = ProfileStorageApi.storage.profitTracker
    val eventKey = MayorPerkApi.mythologicalRitualEventKey
        ?: tracker.mythologicalRitualMayorKey.takeIf(String::isNotBlank)
        ?: UNRESOLVED_MYTHOLOGICAL_RITUAL_EVENT_KEY
    if (tracker.mythologicalRitualMayorKey != eventKey) {
        val preserveStats = tracker.mythologicalRitualMayorKey == UNRESOLVED_MYTHOLOGICAL_RITUAL_EVENT_KEY
        tracker.mythologicalRitualMayorKey = eventKey
        if (!preserveStats) tracker.mythologicalRitualMayor.clear()
        ProfileStorageApi.markDirty()
    }
    return tracker.mythologicalRitualMayor
}

private val ProfitTrackerTarget.trackingPeriods: List<ProfitTrackingPeriod>
    get() = if (preset == ProfitTrackerPreset.MYTHOLOGICAL_RITUAL) {
        MYTHOLOGICAL_RITUAL_TRACKING_PERIODS
    } else {
        STANDARD_TRACKING_PERIODS
    }

private class ForagingTreeGiftParser {
    private var isBonusGiftPending = false

    fun parse(message: String): ParsedItemAmount? {
        val cleanMessage = message.trim()
        if (FORAGING_BONUS_GIFT_HEADER.matches(cleanMessage)) {
            isBonusGiftPending = true
            return null
        }
        if (!isBonusGiftPending) return null
        isBonusGiftPending = false
        return parseForagingChatDrop(cleanMessage)
    }

    fun clear() {
        isBonusGiftPending = false
    }
}

internal data class SlayerQuestCost(val currency: String, val amount: Long)

internal class SlayerQuestCostCapture(
    private val currentTimeMillis: () -> Long = System::currentTimeMillis,
) {
    private var questStartedAtMillis: Long? = null
    private var cost: SlayerQuestCost? = null

    fun questStarted() {
        val now = currentTimeMillis()
        if (questStartedAtMillis?.let { now - it in 0..QUEST_COST_CAPTURE_MILLIS } != true) cost = null
        questStartedAtMillis = now
    }

    fun recordChange(currency: String, change: Double) {
        val startedAt = questStartedAtMillis ?: return
        if (cost != null || change >= 0.0 || currentTimeMillis() - startedAt !in 0..QUEST_COST_CAPTURE_MILLIS) return
        recordCost(currency, (-change).roundToLong())
    }

    fun recordCost(currency: String, amount: Long) {
        if (amount <= 0L) return
        questStartedAtMillis = currentTimeMillis()
        cost = SlayerQuestCost(currency, amount)
    }

    fun take(): SlayerQuestCost? = cost?.also { clear() }

    fun clearExpired() {
        val startedAt = questStartedAtMillis ?: return
        if (cost == null && currentTimeMillis() - startedAt > QUEST_COST_CAPTURE_MILLIS) clear()
    }

    fun clear() {
        questStartedAtMillis = null
        cost = null
    }
}

internal fun profitTrackerSourcePrice(
    bazaarPrice: BazaarPriceData?,
    npcSellPrice: Double?,
    source: ProfitTrackerPriceSource,
): Double? = when (source) {
    ProfitTrackerPriceSource.INSTANT_SELL -> bazaarPrice?.instantSellPrice
    ProfitTrackerPriceSource.SELL_ORDER -> bazaarPrice?.sellOrderPrice
    ProfitTrackerPriceSource.BUY_ORDER -> bazaarPrice?.buyOrderPrice
    ProfitTrackerPriceSource.NPC_SELL -> npcSellPrice
}

internal fun presetConfig(preset: ProfitTrackerPreset): ProfitTrackerConfig =
    with(SkysoftConfigGui.config().profitTrackers) {
        when (preset) {
            ProfitTrackerPreset.FARMING -> farming
            ProfitTrackerPreset.FISHING -> fishing
            ProfitTrackerPreset.FORAGING -> foraging
            ProfitTrackerPreset.MINING -> mining
            ProfitTrackerPreset.MYTHOLOGICAL_RITUAL -> mythologicalRitual
            ProfitTrackerPreset.ZOMBIE -> zombie
            ProfitTrackerPreset.SPIDER -> spider
            ProfitTrackerPreset.WOLF -> wolf
            ProfitTrackerPreset.ENDERMAN -> enderman
            ProfitTrackerPreset.BLAZE -> blaze
            ProfitTrackerPreset.VAMPIRE -> vampire
        }
    }

private val ProfitTrackerConfig.pauseAfterMillis: Int?
    get() = settings.pauseAfterSeconds.coerceIn(MINIMUM_PAUSE_AFTER_SECONDS, MAXIMUM_PAUSE_AFTER_SECONDS)
        .times(MILLIS_PER_SECOND)
        .takeIf { settings.pauseAfter }

private fun newProfitTrackerStats() = ProfileStorage.ProfitTrackerStats()

internal fun didRollProfitTrackerToday(tracker: ProfileStorage.ProfitTrackerData, epochDay: Long): Boolean {
    if (tracker.todayEpochDay == epochDay) return false
    tracker.todayEpochDay = epochDay
    tracker.today.clear()
    return true
}

internal fun trackedItemChanges(
    changes: Map<String, Int>,
    allowedItems: Set<String>,
    transformationInputs: Set<String> = emptySet(),
): Map<String, Int> = changes.filter { (itemId, amount) ->
    itemId in allowedItems && (amount > 0 || itemId in transformationInputs)
}

internal fun applyTrackedItemChanges(stats: ProfileStorage.ProfitTrackerStats, changes: Map<String, Int>) {
    changes.forEach { (itemId, amount) ->
        val updated = (stats.itemCounts.getOrDefault(itemId, 0L) + amount).coerceAtLeast(0L)
        if (updated == 0L) stats.itemCounts.remove(itemId) else stats.itemCounts[itemId] = updated
    }
}

private const val QUEST_COST_CAPTURE_MILLIS = 1_500L
private const val ATTRIBUTION_GRACE_TICKS = 2
private const val MILLIS_PER_SECOND = 1_000
private const val MINIMUM_PAUSE_AFTER_SECONDS = 15
private const val MAXIMUM_PAUSE_AFTER_SECONDS = 900
private const val MINECRAFT_DAY_TICKS = 24_000L
private const val MINECRAFT_NIGHT_START_TICK = 12_000L
private const val SEA_CREATURE_ENTITY_TYPE = "Sea Creature"
private const val MYTHOLOGICAL_CREATURE_ENTITY_TYPE = "Mythological Creature"

internal fun parseFarmingChatDrop(message: String): ParsedItemAmount? {
    val match = FARMING_DROP_PATTERNS.firstNotNullOfOrNull { it.matchEntire(message) } ?: return null
    return match.parsedItemAmount()
}

internal fun parseMiningChatDrop(message: String): ParsedItemAmount? {
    val match = MINING_DROP_PATTERNS.firstNotNullOfOrNull { it.matchEntire(message) } ?: return null
    return match.parsedItemAmount()
}

internal fun parseForagingChatDrop(message: String): ParsedItemAmount? =
    FORAGING_BONUS_GIFT_PATTERN.matchEntire(message.trim())?.parsedItemAmount()

private fun MatchResult.parsedItemAmount(): ParsedItemAmount? {
    val itemName = groups["item"]?.value ?: return null
    val amount = runCatching { groups["amount"]?.value }.getOrNull()
        ?.replace(",", "")
        ?.toIntOrNull()
        ?: 1
    return ParsedItemAmount(itemName, amount)
}

internal data class ReplenishCrop(val harvestItemId: String, val costItemId: String)

internal fun replenishCrop(block: Block, dayTime: Long = 0L): ReplenishCrop? = when (block) {
    Blocks.WHEAT -> ReplenishCrop("WHEAT", "SEEDS")
    Blocks.CARROTS -> ReplenishCrop("CARROT_ITEM", "CARROT_ITEM")
    Blocks.POTATOES -> ReplenishCrop("POTATO_ITEM", "POTATO_ITEM")
    Blocks.NETHER_WART -> ReplenishCrop("NETHER_STALK", "NETHER_STALK")
    Blocks.COCOA -> ReplenishCrop("INK_SACK-3", "INK_SACK-3")
    Blocks.ROSE_BUSH -> ReplenishCrop("WILD_ROSE", "WILD_ROSE")
    Blocks.SUNFLOWER -> if (dayTime % MINECRAFT_DAY_TICKS >= MINECRAFT_NIGHT_START_TICK) {
        ReplenishCrop("MOONFLOWER", "MOONFLOWER")
    } else {
        ReplenishCrop("DOUBLE_PLANT", "DOUBLE_PLANT")
    }
    else -> null
}

private fun isFarmingCropBlock(block: Block): Boolean = when (block) {
    Blocks.WHEAT,
    Blocks.CARROTS,
    Blocks.POTATOES,
    Blocks.NETHER_WART,
    Blocks.PUMPKIN,
    Blocks.CARVED_PUMPKIN,
    Blocks.MELON,
    Blocks.COCOA,
    Blocks.SUGAR_CANE,
    Blocks.CACTUS,
    Blocks.RED_MUSHROOM,
    Blocks.BROWN_MUSHROOM,
    Blocks.RED_MUSHROOM_BLOCK,
    Blocks.BROWN_MUSHROOM_BLOCK,
    Blocks.SUNFLOWER,
    Blocks.ROSE_BUSH,
    -> true

    else -> false
}

internal fun parseCountedPestKill(message: String): String? {
    val match = PEST_KILL_PATTERN.matchEntire(message) ?: return null
    val pest = match.groups["pest"]?.value ?: return null
    val counted = when (pest) {
        "Field Mouse" -> match.groups["item"]?.value == "Dung"
        "Lunar Moth" -> match.groups["item"]?.value == "Enchanted Sunflower"
        else -> match.groups["item"]?.value != "Overclocker 3000"
    }
    return pest.takeIf { counted }
}

private val PEST_KILL_PATTERN = Regex(
    "^You received (?<amount>\\d+)x (?<item>.+) for killing an? (?<pest>.+)!$",
)
private val FARMING_DROP_PATTERNS = listOf(
    Regex("^BLESSED! You found an? (?<item>.+)!$"),
    Regex("^(?:VERY )?RARE CROP! (?<item>.+?)(?: \\(.*)?$"),
    Regex("^[\\w ]+! You dropped (?<amount>[\\d,]+)x (?<item>[\\w ]+)!$"),
    PEST_KILL_PATTERN,
    Regex("^ABOUT TIME! You find an? (?<item>.+?) \\(.*\\)!$"),
    Regex("^OVERFLOW! Your .+ has just dropped an? (?<item>Tool Exp Capsule)!$"),
    Regex("^(?:RARE|PET) DROP! (?<item>.+?)(?: x(?<amount>\\d+))? \\(.*\\)!?$"),
)
private val MINING_DROP_PATTERNS = listOf(
    Regex("^PRISTINE! You found (?<item>\\S Flawed [\\w ]+ Gemstone) x(?<amount>[\\d,]+)!$"),
    Regex("^COMPACT! You found an? (?<item>.+)!$"),
)
private val FORAGING_BONUS_GIFT_HEADER = Regex("^BONUS GIFT(?: \\(\\d+\\))?$")
private val FORAGING_BONUS_GIFT_PATTERN = Regex(
    "^(?<item>.+?) \\(\\d+(?:\\.\\d+)?%(?: - \\d+(?:\\.\\d+)?%)?\\)(?: \\(\\d+\\))?$",
)
private val IMMEDIATE_DROP_PRESETS = ProfitTrackerPreset.entries

enum class ProfitTrackingPeriod(val displayName: String) {
    SESSION("Session"),
    TODAY("Today"),
    MAYOR("Mayor"),
    TOTAL("Total"),
}

private val STANDARD_TRACKING_PERIODS = listOf(
    ProfitTrackingPeriod.SESSION,
    ProfitTrackingPeriod.TODAY,
    ProfitTrackingPeriod.TOTAL,
)
private val MYTHOLOGICAL_RITUAL_TRACKING_PERIODS = ProfitTrackingPeriod.entries
