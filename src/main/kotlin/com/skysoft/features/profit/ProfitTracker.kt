package com.skysoft.features.profit

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
import com.skysoft.data.skyblock.SkyBlockPurseChanges
import com.skysoft.data.skyblock.SkyBlockItemUtilities.extraAttributes
import com.skysoft.data.skyblock.SkyBlockItemUtilities.getStringOrNull
import com.skysoft.data.skyblock.SkyBlockRecipe
import com.skysoft.data.skyblock.SkyBlockSlayerType
import com.skysoft.data.skyblock.SlayerQuestState
import com.skysoft.data.skyblock.price.SkyBlockPriceData
import com.skysoft.features.pets.PetRepository
import com.skysoft.utils.MinecraftClient
import com.skysoft.utils.SkysoftClientEvents
import com.skysoft.utils.chat.ChatEvents
import com.skysoft.utils.chat.ChatMessageVisibility
import java.time.LocalDate
import net.fabricmc.fabric.api.event.client.player.ClientPlayerBlockBreakEvents
import net.minecraft.client.Minecraft
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks

object ProfitTracker {
    private val config get() = SkysoftConfigGui.config().profitTracker
    private val sessionStats = mutableMapOf<String, ProfileStorage.ProfitTrackerStats>()
    private var pendingQuestStart = false
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
    private var craftingInputs = emptyMap<ProfitTrackerPreset, Set<String>>()

    fun register() {
        ProfileStorageApi.registerConsumer("Profit Tracker") { config.enabled }
        TabListApi.registerConsumer("Profit Tracker") { config.enabled }
        SkyBlockDataRepository.Demand.register("Profit Tracker") { config.enabled }
        PetRepository.registerConsumer("Profit Tracker") { config.enabled }
        itemTracking.register(
            { config.enabled },
            { (attributionPreset ?: currentPreset) != ProfitTrackerPreset.FARMING },
            ::recordItemChanges,
        )
        ClientPlayerBlockBreakEvents.AFTER.register { _, _, _, state -> recordFarmingBlock(state.block) }
        SkyBlockPurseChanges.onChange("Profit Tracker coins", { config.enabled }) { change ->
            val preset = attributionPreset ?: currentPreset ?: return@onChange
            if (!shouldTrackCoinGain(preset, change.amount, lastActivityAtMillis[preset])) return@onChange
            markActivity(preset)
            update(preset) { stats -> stats.coins += change.amount }
        }
        ChatEvents.onVisibleMessage(
            "Profit Tracker pest kills",
            { config.enabled && currentPreset == ProfitTrackerPreset.FARMING },
        ) { message ->
            recordFarmingMessage(message.cleanText)
            ChatMessageVisibility.SHOW
        }
        SlayerQuestState.onQuestStarted {
            if (config.enabled) pendingQuestStart = true
        }
        SlayerQuestState.onQuestComplete { quest ->
            pendingQuestStart = false
            if (!config.enabled) return@onQuestComplete
            val preset = quest.slayerType?.let(ProfitTrackerPreset::fromSlayer)?.takeIf(::isInPresetArea)
                ?: return@onQuestComplete
            markActivity(preset)
            update(preset) { stats -> stats.actions++ }
        }
        SkysoftClientEvents.onEndTick(
            "Profit Tracker activity state",
            isActive = { config.enabled || attributionPreset != null || durationPreset != null },
        ) {
            val locationPreset = currentPreset.takeIf { config.enabled }
            if (locationPreset == durationPreset) {
                val now = System.currentTimeMillis()
                val pauseAfterMillis = config.settings.pauseAfterSeconds.coerceIn(
                    MINIMUM_PAUSE_AFTER_SECONDS,
                    MAXIMUM_PAUSE_AFTER_SECONDS,
                ) * MILLIS_PER_SECOND
                if (locationPreset != null &&
                    isProfitTimerActive(lastActivityAtMillis[locationPreset], now, pauseAfterMillis) &&
                    ++durationTicks >= DURATION_UPDATE_TICKS
                ) {
                    durationTicks = 0
                    update(locationPreset) { stats -> stats.activeMillis += DURATION_UPDATE_MILLIS }
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
            if (!pendingQuestStart) return@onEndTick
            val preset = questPreset ?: return@onEndTick
            val type = preset.slayerType ?: return@onEndTick
            val tier = SlayerQuestState.tier ?: return@onEndTick
            pendingQuestStart = false
            val cost = type.questCost(tier) ?: return@onEndTick
            markActivity(preset)
            update(preset) { stats ->
                stats.costs[type.costCurrency] = stats.costs.getOrDefault(type.costCurrency, 0L) + cost
            }
        }
        SkysoftClientEvents.onDisconnect("Profit Tracker session reset", ::resetSession)
        SkyBlockProfileApi.onProfileChange("Profit Tracker profile reset", { true }) { resetSession() }
        registerProfitTrackerHud()
    }

    internal fun isInPresetArea(preset: ProfitTrackerPreset): Boolean = currentPreset == preset

    private val currentPreset: ProfitTrackerPreset?
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
        val pauseAfterMillis = config.settings.pauseAfterSeconds.coerceIn(
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
        if (config.enabled && currentPreset == ProfitTrackerPreset.FARMING && isFarmingCropBlock(block)) {
            markActivity(ProfitTrackerPreset.FARMING)
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

    internal fun unitValue(itemId: String): Double? =
        SkyBlockPriceData.getBazaarPrice(itemId)?.instantSellPrice?.takeIf { it > 0.0 }
            ?: SkyBlockPriceData.getLowestBin(itemId)?.toDouble()?.takeIf { it > 0.0 }
            ?: SkyBlockPriceData.getNpcSellPrice(itemId)?.takeIf { it > 0.0 }

    internal fun trackedItemIds(preset: ProfitTrackerPreset): Set<String> {
        if (dropCatalogVersion != SkyBlockDataRepository.snapshotVersion) rebuildDropCatalog()
        return trackedItems[preset].orEmpty()
    }

    private fun recordItemChanges(batch: SkyBlockItemChangeBatch) {
        val unsuppressedChanges = itemTracking.consume(batch)
        val preset = itemAttributionPreset(batch)
        val allowedItems = preset?.let(::trackedItemIds).orEmpty()
        val changes = when (preset) {
            ProfitTrackerPreset.FARMING -> trackedItemChanges(
                unsuppressedChanges,
                allowedItems,
                craftingInputs[ProfitTrackerPreset.FARMING].orEmpty(),
            )
            null -> emptyMap()
            else -> craftingReconciliation.reconcile(preset, batch.source, unsuppressedChanges, allowedItems)
        }
        ProfitTrackerDebug.record(batch, unsuppressedChanges, preset, changes)
        if (preset == null || changes.isEmpty()) return
        markActivity(preset)
        update(preset) { stats -> applyTrackedItemChanges(stats, changes) }
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
        craftingInputs = trackedItems.mapValues { (_, items) ->
            items.asSequence().flatMap { outputId ->
                SkyBlockDataRepository.recipesFor(SkyBlockDataRepository.itemKey(outputId)).asSequence()
                    .filterIsInstance<SkyBlockRecipe.Crafting>()
                    .mapNotNull { recipe -> recipe.ingredients.map { it.id }.distinct().singleOrNull() }
            }.filter(items::contains).toSet()
        }
        dropCatalogVersion = SkyBlockDataRepository.snapshotVersion
    }

    private fun itemAttributionPreset(batch: SkyBlockItemChangeBatch): ProfitTrackerPreset? {
        val current = attributionPreset ?: currentPreset
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
        pendingQuestStart = false
        attributionPreset = null
        inactiveAttributionTicks = 0
        durationPreset = null
        durationTicks = 0
        previousPreset = null
        previousPresetLeftAtMillis = 0L
        lastActivityAtMillis.clear()
        itemTracking.clear()
        craftingReconciliation.clear()
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

private const val ATTRIBUTION_GRACE_TICKS = 2
private const val DURATION_UPDATE_TICKS = 20
private const val DURATION_UPDATE_MILLIS = 1_000L
private const val MILLIS_PER_SECOND = 1_000
private const val MINIMUM_PAUSE_AFTER_SECONDS = 15
private const val MAXIMUM_PAUSE_AFTER_SECONDS = 900
private const val TALISMAN_OF_COINS_AMOUNT = 1.0
private const val MAXIMUM_COIN_GAIN = 100_000.0
private const val BOUNTIFUL_ATTRIBUTION_MILLIS = 2_000L

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
