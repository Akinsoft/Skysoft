package com.skysoft.features.profit

import com.skysoft.config.ProfitTrackerConfig
import com.skysoft.config.ProfitTrackerPriceSource
import com.skysoft.config.SkysoftConfigGui
import com.skysoft.data.ProfileStorage
import com.skysoft.data.ProfileStorageApi
import com.skysoft.data.SkyBlockIsland
import com.skysoft.data.hypixel.HypixelLocationState
import com.skysoft.data.hypixel.SkyBlockProfileApi
import com.skysoft.data.hypixel.TabListApi
import com.skysoft.data.skyblock.ItemListEntryKind
import com.skysoft.data.skyblock.SkyBlockAreaState
import com.skysoft.data.skyblock.SkyBlockDataRepository
import com.skysoft.data.skyblock.SkyBlockItemChangeBatch
import com.skysoft.data.skyblock.SkyBlockItemChangeSource
import com.skysoft.data.skyblock.SkyBlockItemNames
import com.skysoft.data.skyblock.SKYBLOCK_COINS
import com.skysoft.data.skyblock.SkyBlockCurrencyChanges
import com.skysoft.data.skyblock.SkyBlockItemUtilities.extraAttributes
import com.skysoft.data.skyblock.SkyBlockItemUtilities.getStringOrNull
import com.skysoft.data.skyblock.SkyBlockItemUtilities.skyBlockEnchantments
import com.skysoft.data.skyblock.SkyBlockRecipe
import com.skysoft.data.skyblock.SkyBlockSlayerType
import com.skysoft.data.skyblock.SlayerMessageParser
import com.skysoft.data.skyblock.SlayerQuestState
import com.skysoft.data.skyblock.price.BazaarPriceData
import com.skysoft.data.skyblock.price.SkyBlockPriceData
import com.skysoft.features.pets.PetRepository
import com.skysoft.utils.MinecraftClient
import com.skysoft.utils.SkysoftClientEvents
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
    private var durationTicks = 0
    private var previousPreset: ProfitTrackerPreset? = null
    private var previousPresetLeftAtMillis = 0L
    private val lastActivityAtMillis = mutableMapOf<ProfitTrackerPreset, Long>()
    private val itemTracking = ProfitTrackerItemTracking()
    private val craftingReconciliation = ProfitCraftingReconciliation()
    private var dropCatalogVersion = -1L
    private var trackedItems = emptyMap<ProfitTrackerPreset, Set<String>>()
    private val pendingReplenishCosts = mutableMapOf<ReplenishCrop, Int>()

    fun register() {
        ProfileStorageApi.registerConsumer("Profit Tracker") { configs.isAnyEnabled() }
        TabListApi.registerConsumer("Profit Tracker") { configs.isAnyEnabled() }
        SkyBlockDataRepository.Demand.register("Profit Tracker") { configs.isAnyEnabled() }
        PetRepository.registerConsumer("Profit Tracker") { configs.isAnyEnabled() }
        itemTracking.register({ configs.isAnyEnabled() }, ::recordItemChanges)
        ClientPlayerBlockBreakEvents.AFTER.register { _, _, _, state -> recordFarmingBlock(state.block) }
        SkyBlockCurrencyChanges.onChange("Profit Tracker currency changes", { configs.isAnyEnabled() }) { change ->
            questCostCapture.recordChange(change.currency, change.amount)
            if (change.currency != SKYBLOCK_COINS) return@onChange
            val preset = attributionPreset?.takeIf { presetConfig(it).enabled } ?: currentPreset ?: return@onChange
            if (!shouldTrackCoinGain(preset, change.amount, lastActivityAtMillis[preset])) return@onChange
            markActivity(preset)
            update(preset) { stats -> stats.coins += change.amount }
        }
        ChatEvents.onVisibleMessage(
            "Profit Tracker pest kills",
            { presetConfig(ProfitTrackerPreset.FARMING).enabled && currentPreset == ProfitTrackerPreset.FARMING },
        ) { message ->
            recordFarmingMessage(message.cleanText)
            ChatMessageVisibility.SHOW
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
        SlayerQuestState.onQuestStarted {
            if (configs.isAnyEnabled()) questCostCapture.questStarted()
        }
        SlayerQuestState.onQuestComplete { quest ->
            questCostCapture.clear()
            if (!configs.isAnyEnabled()) return@onQuestComplete
            val preset = quest.slayerType?.let(ProfitTrackerPreset::fromSlayer)?.takeIf(::isInPresetArea)
                ?: return@onQuestComplete
            markActivity(preset)
            update(preset) { stats -> stats.actions++ }
        }
        SkysoftClientEvents.onEndTick(
            "Profit Tracker activity state",
            isActive = { configs.isAnyEnabled() || attributionPreset != null || durationPreset != null },
        ) {
            val locationPreset = currentPreset
            if (locationPreset == durationPreset) {
                if (locationPreset != null) {
                    val now = System.currentTimeMillis()
                    val pauseAfterMillis = presetConfig(locationPreset).settings.pauseAfterSeconds.coerceIn(
                        MINIMUM_PAUSE_AFTER_SECONDS,
                        MAXIMUM_PAUSE_AFTER_SECONDS,
                    ) * MILLIS_PER_SECOND
                    if (isProfitTimerActive(lastActivityAtMillis[locationPreset], now, pauseAfterMillis) &&
                        ++durationTicks >= DURATION_UPDATE_TICKS
                    ) {
                        durationTicks = 0
                        update(locationPreset) { stats -> stats.activeMillis += DURATION_UPDATE_MILLIS }
                    }
                }
            } else {
                durationPreset?.let { previous ->
                    previousPreset = previous
                    previousPresetLeftAtMillis = System.currentTimeMillis()
                }
                durationPreset = locationPreset
                durationTicks = 0
                locationPreset?.takeUnless { it == ProfitTrackerPreset.FARMING }?.let(::markActivity)
            }
            val questPreset = SlayerQuestState.slayerType?.let(ProfitTrackerPreset::fromSlayer)?.takeIf(::isInPresetArea)
            val activePreset = questPreset ?: locationPreset?.takeIf { it == ProfitTrackerPreset.FARMING }
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
            markActivity(preset)
            update(preset) { stats ->
                stats.costs[cost.currency] = stats.costs.getOrDefault(cost.currency, 0L) + cost.amount
            }
        }
        SkysoftClientEvents.onDisconnect("Profit Tracker session reset", ::resetSession)
        SkyBlockProfileApi.onProfileChange("Profit Tracker profile reset", { true }) { resetSession() }
        registerProfitTrackerHud()
    }

    internal fun isInPresetArea(preset: ProfitTrackerPreset): Boolean =
        presetConfig(preset).enabled && locationPreset == preset

    private val currentPreset: ProfitTrackerPreset?
        get() = locationPreset?.takeIf { preset -> presetConfig(preset).enabled }

    private val locationPreset: ProfitTrackerPreset?
        get() = ProfitTrackerPresets.forLocation(
            TabListApi.skyBlockAreaName ?: HypixelLocationState.currentIsland?.displayName,
            SkyBlockAreaState.currentArea,
        )

    internal fun selectedPreset(): ProfitTrackerPreset? =
        SlayerQuestState.slayerType?.let(ProfitTrackerPreset::fromSlayer)?.takeIf(::isInPresetArea)
            ?: currentPreset
            ?: ProfileStorageApi.storage.profitTracker.lastPreset
                .let { stored -> ProfitTrackerPreset.entries.firstOrNull { it.name == stored } }
                ?.takeIf(::isInPresetArea)

    internal fun stats(preset: ProfitTrackerPreset): ProfileStorage.ProfitTrackerStats = when (displayPeriod(preset)) {
        ProfitTrackingPeriod.SESSION -> sessionStats.getOrPut(preset.name, ::newProfitTrackerStats)
        ProfitTrackingPeriod.TODAY -> todayStats(preset)
        ProfitTrackingPeriod.TOTAL -> ProfileStorageApi.storage.profitTracker.totals.getOrPut(preset.name, ::newProfitTrackerStats)
    }

    internal fun isTimerPaused(preset: ProfitTrackerPreset): Boolean {
        val pauseAfterMillis = presetConfig(preset).settings.pauseAfterSeconds.coerceIn(
            MINIMUM_PAUSE_AFTER_SECONDS,
            MAXIMUM_PAUSE_AFTER_SECONDS,
        ) * MILLIS_PER_SECOND
        return !isProfitTimerActive(lastActivityAtMillis[preset], System.currentTimeMillis(), pauseAfterMillis)
    }

    internal fun displayPeriod(preset: ProfitTrackerPreset): ProfitTrackingPeriod =
        ProfileStorageApi.storage.profitTracker.displayPeriods[preset.name]
            ?.let { period -> ProfitTrackingPeriod.entries.firstOrNull { it.name == period } }
            ?: ProfitTrackingPeriod.SESSION

    internal fun cyclePeriod(preset: ProfitTrackerPreset, backwards: Boolean) {
        val periods = ProfitTrackingPeriod.entries
        val current = displayPeriod(preset)
        val step = if (backwards) -1 else 1
        val next = periods[Math.floorMod(current.ordinal + step, periods.size)]
        ProfileStorageApi.storage.profitTracker.displayPeriods[preset.name] = next.name
        ProfileStorageApi.markDirty()
        ProfileStorageApi.saveNow()
    }

    internal fun resetDisplayed(preset: ProfitTrackerPreset) {
        itemTracking.clear()
        craftingReconciliation.clear(preset)
        pendingReplenishCosts.clear()
        when (displayPeriod(preset)) {
            ProfitTrackingPeriod.SESSION -> sessionStats[preset.name]?.clear()
            ProfitTrackingPeriod.TODAY -> {
                todayStats(preset).clear()
                ProfileStorageApi.markDirty()
                ProfileStorageApi.saveNow()
            }
            ProfitTrackingPeriod.TOTAL -> {
                ProfileStorageApi.storage.profitTracker.totals[preset.name]?.clear()
                ProfileStorageApi.markDirty()
                ProfileStorageApi.saveNow()
            }
        }
    }

    private fun recordFarmingBlock(block: Block) {
        if (!presetConfig(ProfitTrackerPreset.FARMING).enabled ||
            currentPreset != ProfitTrackerPreset.FARMING || !isFarmingCropBlock(block)
        ) return
        markActivity(ProfitTrackerPreset.FARMING)
        val minecraft = Minecraft.getInstance()
        if (minecraft.player?.mainHandItem?.extraAttributes()?.skyBlockEnchantments()?.containsKey("replenish") != true) {
            return
        }
        replenishCrop(block, minecraft.level?.gameTime ?: 0L)?.let { crop ->
            pendingReplenishCosts[crop] = pendingReplenishCosts.getOrDefault(crop, 0) + 1
        }
    }

    private fun recordFarmingMessage(message: String) {
        parseFarmingChatDrop(message)?.let { drop ->
            markActivity(ProfitTrackerPreset.FARMING)
            val itemId = SkyBlockItemNames.itemId(drop.displayName) ?: return@let
            itemTracking.suppressGain(itemId, drop.amount)
            update(ProfitTrackerPreset.FARMING) { stats ->
                applyTrackedItemChanges(stats, mapOf(itemId to drop.amount))
            }
        }
        if (isCountedPestKillMessage(message)) {
            markActivity(ProfitTrackerPreset.FARMING)
            update(ProfitTrackerPreset.FARMING) { stats -> stats.actions++ }
        }
    }

    internal fun unitValue(preset: ProfitTrackerPreset, itemId: String): Double? {
        val sourcePrice = profitTrackerSourcePrice(
            SkyBlockPriceData.getBazaarPrice(itemId),
            SkyBlockPriceData.getNpcSellPrices(itemId).coins,
            ProfitTrackerItemCustomizations.priceSource(preset, itemId),
        )
        return sourcePrice?.takeIf { it > 0.0 }
            ?: SkyBlockPriceData.getLowestBin(itemId)?.toDouble()?.takeIf { it > 0.0 }
    }

    internal fun trackedItemIds(preset: ProfitTrackerPreset): Set<String> {
        if (dropCatalogVersion != SkyBlockDataRepository.snapshotVersion) rebuildDropCatalog()
        return trackedItems[preset].orEmpty() + ProfitTrackerItemCustomizations.customItems(preset)
    }

    private fun recordItemChanges(batch: SkyBlockItemChangeBatch) {
        val unsuppressedChanges = itemTracking.consume(batch)
        val preset = itemAttributionPreset(batch)
        val allowedItems = preset?.let(::trackedItemIds).orEmpty()
        val changes = when {
            preset == null -> emptyMap()
            batch.source == SkyBlockItemChangeSource.INVENTORY &&
                MinecraftClient.screen() is AbstractContainerScreen<*> -> emptyMap()
            else -> craftingReconciliation.reconcile(preset, batch.source, unsuppressedChanges, allowedItems)
                .withReplenishCosts(preset)
        }
        if (preset == null || changes.isEmpty()) return
        markActivity(preset)
        update(preset) { stats -> applyTrackedItemChanges(stats, changes) }
    }

    private fun Map<String, Int>.withReplenishCosts(preset: ProfitTrackerPreset): Map<String, Int> {
        if (preset != ProfitTrackerPreset.FARMING || pendingReplenishCosts.isEmpty()) return this
        val costs = pendingReplenishCosts.filterKeys { crop -> getOrDefault(crop.harvestItemId, 0) > 0 }
        if (costs.isEmpty()) return this
        pendingReplenishCosts.keys.removeAll(costs.keys)
        return toMutableMap().apply {
            costs.forEach { (crop, amount) -> merge(crop.costItemId, -amount, Int::plus) }
        }.filterValues { it != 0 }
    }

    private fun update(preset: ProfitTrackerPreset, action: (ProfileStorage.ProfitTrackerStats) -> Unit) {
        action(sessionStats.getOrPut(preset.name, ::newProfitTrackerStats))
        action(todayStats(preset))
        action(ProfileStorageApi.storage.profitTracker.totals.getOrPut(preset.name, ::newProfitTrackerStats))
        ProfileStorageApi.storage.profitTracker.lastPreset = preset.name
        ProfileStorageApi.markDirty()
    }

    private fun todayStats(preset: ProfitTrackerPreset): ProfileStorage.ProfitTrackerStats {
        val tracker = ProfileStorageApi.storage.profitTracker
        val today = LocalDate.now().toEpochDay()
        if (didRollProfitTrackerToday(tracker, today)) ProfileStorageApi.markDirty()
        return tracker.today.getOrPut(preset.name, ::newProfitTrackerStats)
    }

    private fun rebuildDropCatalog() {
        trackedItems = ProfitTrackerPreset.entries.associateWith { preset ->
            val slayerType = preset.slayerType
            val directDrops = if (slayerType == null) {
                emptySet()
            } else {
                SkyBlockDataRepository.entries.asSequence()
                    .filter { entry -> entry.key.kind == ItemListEntryKind.SKYBLOCK }
                    .filter { entry ->
                        SkyBlockDataRepository.info(entry.key)?.dropSources.orEmpty().any { source ->
                            SkyBlockSlayerType.fromBossEntityId(source.entityId)?.first == slayerType
                        }
                    }
                    .map { entry -> entry.key.id }
                    .toSet()
            }
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

    private fun markActivity(preset: ProfitTrackerPreset) {
        lastActivityAtMillis[preset] = System.currentTimeMillis()
    }

    private fun resetSession() {
        sessionStats.clear()
        questCostCapture.clear()
        attributionPreset = null
        inactiveAttributionTicks = 0
        durationPreset = null
        durationTicks = 0
        previousPreset = null
        previousPresetLeftAtMillis = 0L
        lastActivityAtMillis.clear()
        itemTracking.clear()
        craftingReconciliation.clear()
        pendingReplenishCosts.clear()
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
            ProfitTrackerPreset.ZOMBIE -> zombie
            ProfitTrackerPreset.SPIDER -> spider
            ProfitTrackerPreset.WOLF -> wolf
            ProfitTrackerPreset.ENDERMAN -> enderman
            ProfitTrackerPreset.BLAZE -> blaze
            ProfitTrackerPreset.VAMPIRE -> vampire
        }
    }

private fun newProfitTrackerStats() = ProfileStorage.ProfitTrackerStats()

internal fun isProfitTimerActive(lastActivityAtMillis: Long?, now: Long, pauseAfterMillis: Int): Boolean =
    lastActivityAtMillis != null && now - lastActivityAtMillis <= pauseAfterMillis

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
private const val DURATION_UPDATE_TICKS = 20
private const val DURATION_UPDATE_MILLIS = 1_000L
private const val MILLIS_PER_SECOND = 1_000
private const val MINIMUM_PAUSE_AFTER_SECONDS = 15
private const val MAXIMUM_PAUSE_AFTER_SECONDS = 900
private const val TALISMAN_OF_COINS_AMOUNT = 1.0
private const val MAXIMUM_COIN_GAIN = 100_000.0
private const val BOUNTIFUL_ATTRIBUTION_MILLIS = 2_000L
private const val MINECRAFT_DAY_TICKS = 24_000L
private const val MINECRAFT_NIGHT_START_TICK = 12_000L

private fun shouldTrackCoinGain(
    preset: ProfitTrackerPreset,
    amount: Double,
    lastActivityAtMillis: Long?,
): Boolean {
    if (MinecraftClient.screen() != null || amount <= TALISMAN_OF_COINS_AMOUNT || amount >= MAXIMUM_COIN_GAIN) {
        return false
    }
    if (preset != ProfitTrackerPreset.FARMING) return preset.slayerType != null
    val recentlyFarmed = lastActivityAtMillis?.let {
        System.currentTimeMillis() - it <= BOUNTIFUL_ATTRIBUTION_MILLIS
    } == true
    val modifier = Minecraft.getInstance().player?.mainHandItem?.extraAttributes()?.getStringOrNull("modifier")
    return HypixelLocationState.currentIsland == SkyBlockIsland.GARDEN && recentlyFarmed && modifier == "bountiful"
}

internal data class FarmingChatDrop(val displayName: String, val amount: Int)

internal fun parseFarmingChatDrop(message: String): FarmingChatDrop? {
    val match = FARMING_DROP_PATTERNS.firstNotNullOfOrNull { it.matchEntire(message) } ?: return null
    val itemName = match.groups["item"]?.value ?: return null
    val amount = runCatching { match.groups["amount"]?.value }.getOrNull()
        ?.replace(",", "")
        ?.toIntOrNull()
        ?: 1
    return FarmingChatDrop(itemName, amount)
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

internal fun isFarmingCropBlock(block: Block): Boolean = when (block) {
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

internal fun isCountedPestKillMessage(message: String): Boolean {
    val match = PEST_KILL_PATTERN.matchEntire(message) ?: return false
    return when (match.groups["pest"]?.value) {
        "Field Mouse" -> match.groups["item"]?.value == "Dung"
        "Lunar Moth" -> match.groups["item"]?.value == "Enchanted Sunflower"
        else -> match.groups["item"]?.value != "Overclocker 3000"
    }
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

enum class ProfitTrackingPeriod(val displayName: String) {
    SESSION("Session"),
    TODAY("Today"),
    TOTAL("Total"),
}
