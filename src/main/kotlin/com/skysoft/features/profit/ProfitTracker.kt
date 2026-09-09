package com.skysoft.features.profit

import com.skysoft.config.ProfitTrackerConfig
import com.skysoft.config.ProfitTrackerPriceSource
import com.skysoft.config.SkysoftConfigGui
import com.skysoft.data.ProfileStorage
import com.skysoft.data.ProfileStorageView
import com.skysoft.data.ProfileStorageApi
import com.skysoft.data.hypixel.HypixelLocationState
import com.skysoft.data.hypixel.SkyBlockProfileApi
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
import com.skysoft.data.skyblock.SlayerMessageParser
import com.skysoft.data.skyblock.SlayerQuestState
import com.skysoft.data.skyblock.price.BazaarPriceData
import com.skysoft.data.skyblock.price.SkyBlockPriceData
import com.skysoft.features.event.diana.DianaEventState
import com.skysoft.features.event.diana.MythologicalRitualMessageTracker
import com.skysoft.features.slayer.SlayerTimeToKill
import com.skysoft.utils.MinecraftClient
import com.skysoft.utils.SkysoftClientEvents
import com.skysoft.utils.animation.TimedHighlightTracker
import com.skysoft.utils.chat.ChatEvents
import com.skysoft.utils.chat.ChatMessageVisibility
import net.fabricmc.fabric.api.event.client.player.ClientPlayerBlockBreakEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks

object ProfitTracker {
    private val configs get() = SkysoftConfigGui.config().profitTrackers
    private val statistics = ProfitTrackerStatistics()
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
    private val dropCatalog = ProfitDropCatalog()
    private val replenishCosts = ProfitReplenishCosts()
    private val foragingTreeGiftParser = ForagingTreeGiftParser()
    internal val itemQuantityHighlights = TimedHighlightTracker<Pair<String, String>>()

    fun register() {
        ProfileStorageApi.registerConsumer("Profit Tracker") { configs.isAnyEnabled() }
        SkyBlockDataRepository.Demand.register("Profit Tracker") { configs.isAnyEnabled() }
        MayorPerkApi.registerConsumer("Profit Tracker") { configs.farming.enabled || configs.mythologicalRitual.enabled }
        itemTracking.register({ configs.isAnyEnabled() }, ::recordItemChanges)
        ClientPlayerBlockBreakEvents.AFTER.register { _, _, _, state -> recordFarmingBlock(state.block) }
        SkyBlockCurrencyChanges.onChange("Profit Tracker currency changes", { configs.isAnyEnabled() }) { change ->
            questCostCapture.recordChange(change.currency, change.amount)
            if (change.currency != SKYBLOCK_COINS) return@onChange
            val preset = currentAttributionPreset
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
            val preset = quest.slayerType?.let(ProfitTrackerPreset::fromSlayer)?.takeIf { presetConfig(it).enabled }
                ?: return@onQuestComplete
            val locationPreset = ProfitTrackerPresets.forLocation(
                HypixelLocationState.currentIsland?.displayName,
                SkyBlockAreaState.currentArea,
                preset,
            )
            if (locationPreset != preset) return@onQuestComplete
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
            statistics.synchronizePeriods(configs.mythologicalRitual.enabled)
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

    private val currentAttributionPreset: ProfitTrackerPreset?
        get() = currentPreset ?: attributionPreset?.takeIf(::canTrackPreset)

    private fun canTrackPreset(preset: ProfitTrackerPreset): Boolean =
        presetConfig(preset).enabled && ProfitTrackerPresets.forLocation(
            HypixelLocationState.currentIsland?.displayName,
            SkyBlockAreaState.currentArea,
            preset,
        ) == preset

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
                ?: SlayerQuestState.scoreboardSlayerType?.let(ProfitTrackerPreset::fromSlayer)
                ?: fishingHookPreset
                ?: activeFishingPreset,
        )

    private val fishingHookPreset: ProfitTrackerPreset?
        get() = ProfitTrackerPreset.FISHING.takeIf {
            canTrackPreset(it) &&
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

    internal fun stats(target: ProfitTrackerTarget): ProfileStorageView.ProfitTrackerStats = statistics.stats(target)

    internal fun isTimerPaused(target: ProfitTrackerTarget): Boolean =
        uptime.isPaused(target, Minecraft.getInstance().isWindowActive)

    internal fun displayPeriod(target: ProfitTrackerTarget): ProfitTrackingPeriod = statistics.displayPeriod(target)

    internal fun resetDisplayed(target: ProfitTrackerTarget) {
        itemTracking.clear()
        craftingReconciliation.clear(target)
        replenishCosts.clear()
        val period = displayPeriod(target)
        statistics.reset(target, period)
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
        craftingReconciliation.clear(target)
        uptime.clear(target)
        statistics.deleteCustomTracker(target)
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
        replenishCosts.record(block, minecraft.level?.gameTime ?: 0L)
    }

    private fun recordImmediateMessage(preset: ProfitTrackerPreset, message: String) {
        if (preset == ProfitTrackerPreset.FARMING && message == KERNEL_DONATION_MESSAGE) {
            val target = ProfitTrackerTarget.preset(preset)
            uptime.markActivity(target)
            update(target) { stats -> stats.kernels++ }
            return
        }
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
        return dropCatalog.items(target.preset) + ProfitTrackerItemCustomizations.customItems(target)
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
                .let { changes ->
                    if (target.preset == ProfitTrackerPreset.FARMING) replenishCosts.applyTo(changes) else changes
                }
            if (changes.isEmpty()) return@forEach
            uptime.markActivity(target)
            update(target, changes.keys) { stats -> applyTrackedItemChanges(stats, changes) }
        }
    }

    private fun update(
        target: ProfitTrackerTarget,
        changedItemIds: Iterable<String> = emptyList(),
        action: (ProfileStorage.ProfitTrackerStats) -> Unit,
    ) {
        statistics.update(target, action)
        if (target.config.details.highlightChanges) {
            changedItemIds.forEach { itemId -> itemQuantityHighlights.highlight(target.storageKey to itemId) }
        }
    }

    private fun itemAttributionPreset(batch: SkyBlockItemChangeBatch): ProfitTrackerPreset? {
        val current = currentAttributionPreset
        if (current != null) return current
        if (batch.source != SkyBlockItemChangeSource.SACKS) return null
        val windowMillis = (batch.sackWindowSeconds ?: return null) * MILLIS_PER_SECOND
        return previousPreset?.takeIf {
            canTrackPreset(it) && System.currentTimeMillis() - previousPresetLeftAtMillis <= windowMillis
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
        replenishCosts.clear()
        foragingTreeGiftParser.clear()
        itemQuantityHighlights.clear()
        resetProfitTrackerHud()
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

private const val ATTRIBUTION_GRACE_TICKS = 2
private const val MILLIS_PER_SECOND = 1_000
private const val MINIMUM_PAUSE_AFTER_SECONDS = 15
private const val MAXIMUM_PAUSE_AFTER_SECONDS = 900
private const val KERNEL_DONATION_MESSAGE =
    "[NPC] Feast Chef Ted: Thanks for the donation! I've added a Kernel to your purse."

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

private val IMMEDIATE_DROP_PRESETS = ProfitTrackerPreset.entries
