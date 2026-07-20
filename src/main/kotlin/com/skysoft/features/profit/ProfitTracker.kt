package com.skysoft.features.profit

import com.skysoft.config.SkysoftConfigGui
import com.skysoft.data.ProfileStorage
import com.skysoft.data.ProfileStorageApi
import com.skysoft.data.hypixel.HypixelLocationState
import com.skysoft.data.hypixel.SkyBlockProfileApi
import com.skysoft.data.hypixel.TabListApi
import com.skysoft.data.skyblock.ItemListEntryKind
import com.skysoft.data.skyblock.SkyBlockAreaState
import com.skysoft.data.skyblock.SkyBlockDataRepository
import com.skysoft.data.skyblock.SkyBlockItemChangeBatch
import com.skysoft.data.skyblock.SkyBlockItemChangeSource
import com.skysoft.data.skyblock.SkyBlockPurseChanges
import com.skysoft.data.skyblock.SkyBlockRecipe
import com.skysoft.data.skyblock.SkyBlockSlayerType
import com.skysoft.data.skyblock.SlayerQuestState
import com.skysoft.data.skyblock.price.SkyBlockPriceData
import com.skysoft.utils.MinecraftClient
import com.skysoft.utils.SkysoftClientEvents
import java.time.LocalDate

object ProfitTracker {
    private val config get() = SkysoftConfigGui.config().profitTracker
    private val sessionStats = mutableMapOf<String, ProfileStorage.ProfitTrackerStats>()
    private var pendingQuestStart = false
    private var attributionType: SkyBlockSlayerType? = null
    private var inactiveAttributionTicks = 0
    private var durationType: SkyBlockSlayerType? = null
    private var durationTicks = 0
    private var previousPresetType: SkyBlockSlayerType? = null
    private var previousPresetLeftAtMillis = 0L
    private val lastActivityAtMillis = mutableMapOf<SkyBlockSlayerType, Long>()
    private val itemTracking = ProfitTrackerItemTracking()
    private var dropCatalogVersion = -1L
    private var slayerDrops = emptyMap<SkyBlockSlayerType, Set<String>>()

    fun register() {
        ProfileStorageApi.registerConsumer("Profit Tracker") { config.enabled }
        TabListApi.registerConsumer("Profit Tracker") { config.enabled }
        SkyBlockDataRepository.Demand.register("Profit Tracker") { config.enabled }
        itemTracking.register({ config.enabled }, ::recordItemChanges)
        SkyBlockPurseChanges.onChange("Profit Tracker mob kill coins", { config.enabled }) { change ->
            val type = attributionType ?: SlayerQuestState.slayerType?.takeIf(::isInPresetArea) ?: return@onChange
            if (MinecraftClient.screen() != null || change.amount <= TALISMAN_OF_COINS_AMOUNT ||
                change.amount >= MAXIMUM_MOB_KILL_COINS
            ) {
                return@onChange
            }
            markActivity(type)
            update(type) { stats -> stats.mobKillCoins += change.amount }
        }
        SlayerQuestState.onQuestStarted {
            if (config.enabled) pendingQuestStart = true
        }
        SlayerQuestState.onQuestComplete { quest ->
            pendingQuestStart = false
            if (!config.enabled) return@onQuestComplete
            val type = quest.slayerType?.takeIf(::isInPresetArea) ?: return@onQuestComplete
            markActivity(type)
            update(type) { stats -> stats.bosses++ }
        }
        SkysoftClientEvents.onEndTick(
            "Profit Tracker Slayer state",
            isActive = { config.enabled || attributionType != null || durationType != null },
        ) {
            val presetType = currentPresetSlayerType().takeIf { config.enabled }
            if (presetType == durationType) {
                val now = System.currentTimeMillis()
                val pauseAfterMillis = config.settings.pauseAfterSeconds.coerceIn(
                    MINIMUM_PAUSE_AFTER_SECONDS,
                    MAXIMUM_PAUSE_AFTER_SECONDS,
                ) * MILLIS_PER_SECOND
                if (presetType != null && isProfitTimerActive(lastActivityAtMillis[presetType], now, pauseAfterMillis) &&
                    ++durationTicks >= DURATION_UPDATE_TICKS
                ) {
                    durationTicks = 0
                    update(presetType) { stats -> stats.activeMillis += DURATION_UPDATE_MILLIS }
                }
            } else {
                durationType?.let { previousType ->
                    previousPresetType = previousType
                    previousPresetLeftAtMillis = System.currentTimeMillis()
                }
                durationType = presetType
                durationTicks = 0
                presetType?.let(::markActivity)
            }
            val activeType = SlayerQuestState.slayerType?.takeIf(::isInPresetArea)
            if (activeType != null) {
                attributionType = activeType
                inactiveAttributionTicks = 0
            } else if (attributionType != null && ++inactiveAttributionTicks > ATTRIBUTION_GRACE_TICKS) {
                attributionType = null
                inactiveAttributionTicks = 0
            }
            if (!pendingQuestStart) return@onEndTick
            val type = activeType ?: return@onEndTick
            val tier = SlayerQuestState.tier ?: return@onEndTick
            pendingQuestStart = false
            val cost = type.questCost(tier) ?: return@onEndTick
            markActivity(type)
            update(type) { stats ->
                stats.costs[type.costCurrency] = stats.costs.getOrDefault(type.costCurrency, 0L) + cost
            }
        }
        SkysoftClientEvents.onDisconnect("Profit Tracker session reset", ::resetSession)
        SkyBlockProfileApi.onProfileChange("Profit Tracker profile reset", { true }) { resetSession() }
        registerProfitTrackerHud()
    }

    internal fun isInPresetArea(type: SkyBlockSlayerType): Boolean = currentPresetSlayerType() == type

    private fun currentPresetSlayerType(): SkyBlockSlayerType? = ProfitTrackerPresets.slayerForLocation(
        TabListApi.skyBlockAreaName ?: HypixelLocationState.currentIsland?.displayName,
        SkyBlockAreaState.currentArea,
    )

    internal fun selectedSlayerType(): SkyBlockSlayerType? =
        SlayerQuestState.slayerType?.takeIf(::isInPresetArea)
            ?: currentPresetSlayerType()
            ?: ProfileStorageApi.storage.profitTracker.lastSlayerType
                .let { stored -> SkyBlockSlayerType.entries.firstOrNull { it.name == stored } }
                ?.takeIf(::isInPresetArea)

    internal fun stats(type: SkyBlockSlayerType): ProfileStorage.ProfitTrackerStats = when (displayPeriod(type)) {
        ProfitTrackingPeriod.SESSION -> sessionStats.getOrPut(type.name, ::newStats)
        ProfitTrackingPeriod.TODAY -> todayStats(type)
        ProfitTrackingPeriod.TOTAL -> ProfileStorageApi.storage.profitTracker.totals.getOrPut(type.name, ::newStats)
    }

    internal fun isTimerPaused(type: SkyBlockSlayerType): Boolean {
        val pauseAfterMillis = config.settings.pauseAfterSeconds.coerceIn(
            MINIMUM_PAUSE_AFTER_SECONDS,
            MAXIMUM_PAUSE_AFTER_SECONDS,
        ) * MILLIS_PER_SECOND
        return !isProfitTimerActive(lastActivityAtMillis[type], System.currentTimeMillis(), pauseAfterMillis)
    }

    internal fun displayPeriod(type: SkyBlockSlayerType): ProfitTrackingPeriod =
        ProfileStorageApi.storage.profitTracker.displayPeriods[type.name]
            ?.let { period -> ProfitTrackingPeriod.entries.firstOrNull { it.name == period } }
            ?: ProfitTrackingPeriod.SESSION

    internal fun cyclePeriod(type: SkyBlockSlayerType, backwards: Boolean) {
        val periods = ProfitTrackingPeriod.entries
        val current = displayPeriod(type)
        val step = if (backwards) -1 else 1
        val next = periods[Math.floorMod(current.ordinal + step, periods.size)]
        ProfileStorageApi.storage.profitTracker.displayPeriods[type.name] = next.name
        ProfileStorageApi.markDirty()
        ProfileStorageApi.saveNow()
    }

    internal fun resetDisplayed(type: SkyBlockSlayerType) {
        when (displayPeriod(type)) {
            ProfitTrackingPeriod.SESSION -> sessionStats[type.name]?.clear()
            ProfitTrackingPeriod.TODAY -> {
                todayStats(type).clear()
                ProfileStorageApi.markDirty()
                ProfileStorageApi.saveNow()
            }
            ProfitTrackingPeriod.TOTAL -> {
                ProfileStorageApi.storage.profitTracker.totals[type.name]?.clear()
                ProfileStorageApi.markDirty()
                ProfileStorageApi.saveNow()
            }
        }
    }

    internal fun unitValue(itemId: String): Double? =
        SkyBlockPriceData.getBazaarPrice(itemId)?.instantSellPrice?.takeIf { it > 0.0 }
            ?: SkyBlockPriceData.getLowestBin(itemId)?.toDouble()?.takeIf { it > 0.0 }
            ?: SkyBlockPriceData.getNpcSellPrice(itemId)?.takeIf { it > 0.0 }

    internal fun trackedItemIds(type: SkyBlockSlayerType): Set<String> {
        if (dropCatalogVersion != SkyBlockDataRepository.snapshotVersion) rebuildDropCatalog()
        return slayerDrops[type].orEmpty()
    }

    private fun recordItemChanges(batch: SkyBlockItemChangeBatch) {
        val unsuppressedChanges = itemTracking.consume(batch)
        val type = itemAttributionType(batch) ?: return
        val allowedItems = trackedItemIds(type)
        if (allowedItems.isEmpty()) return
        val transformationInputs = craftingTransformationInputs(unsuppressedChanges, allowedItems)
        val changes = trackedItemChanges(unsuppressedChanges, allowedItems, transformationInputs)
        if (changes.isEmpty()) return
        markActivity(type)
        update(type) { stats -> applyTrackedItemChanges(stats, changes) }
    }

    private fun update(type: SkyBlockSlayerType, action: (ProfileStorage.ProfitTrackerStats) -> Unit) {
        action(sessionStats.getOrPut(type.name, ::newStats))
        action(todayStats(type))
        action(ProfileStorageApi.storage.profitTracker.totals.getOrPut(type.name, ::newStats))
        ProfileStorageApi.storage.profitTracker.lastSlayerType = type.name
        ProfileStorageApi.markDirty()
    }

    private fun todayStats(type: SkyBlockSlayerType): ProfileStorage.ProfitTrackerStats {
        val tracker = ProfileStorageApi.storage.profitTracker
        val today = LocalDate.now().toEpochDay()
        if (didRollProfitTrackerToday(tracker, today)) ProfileStorageApi.markDirty()
        return tracker.today.getOrPut(type.name, ::newStats)
    }

    private fun rebuildDropCatalog() {
        slayerDrops = SkyBlockSlayerType.entries.associateWith { type ->
            val directDrops = SkyBlockDataRepository.entries.asSequence()
                .filter { entry -> entry.key.kind == ItemListEntryKind.SKYBLOCK }
                .filter { entry ->
                    SkyBlockDataRepository.info(entry.key)?.dropSources.orEmpty().any { source ->
                        SkyBlockSlayerType.fromBossEntityId(source.entityId)?.first == type
                    }
                }
                .map { entry -> entry.key.id }
                .toSet()
            val compactedDrops = SkyBlockDataRepository.entries.asSequence()
                .filter { entry -> entry.key.kind == ItemListEntryKind.SKYBLOCK }
                .filter { entry ->
                    SkyBlockDataRepository.recipesFor(entry.key)
                        .filterIsInstance<SkyBlockRecipe.Crafting>()
                        .any { recipe -> recipe.ingredients.map { it.id }.distinct().singleOrNull() in directDrops }
                }
                .map { entry -> entry.key.id }
            directDrops + compactedDrops + ProfitTrackerPresets.slayer(type).additionalItems
        }
        dropCatalogVersion = SkyBlockDataRepository.snapshotVersion
    }

    private fun itemAttributionType(batch: SkyBlockItemChangeBatch): SkyBlockSlayerType? {
        val current = attributionType ?: SlayerQuestState.slayerType?.takeIf(::isInPresetArea)
        if (current != null) return current
        if (batch.source != SkyBlockItemChangeSource.SACKS) return null
        val windowMillis = (batch.sackWindowSeconds ?: return null) * MILLIS_PER_SECOND
        return previousPresetType?.takeIf {
            System.currentTimeMillis() - previousPresetLeftAtMillis <= windowMillis
        }
    }

    private fun craftingTransformationInputs(changes: Map<String, Int>, allowedItems: Set<String>): Set<String> =
        changes.asSequence()
            .filter { (itemId, amount) -> amount > 0 && itemId in allowedItems }
            .flatMap { (itemId, outputAmount) ->
                SkyBlockDataRepository.recipesFor(SkyBlockDataRepository.itemKey(itemId)).asSequence()
                    .filterIsInstance<SkyBlockRecipe.Crafting>()
                    .filter { recipe -> outputAmount % recipe.result.count == 0L }
                    .filter { recipe ->
                        val crafts = outputAmount / recipe.result.count
                        recipe.ingredients.groupingBy { it.id }.fold(0L) { total, ingredient -> total + ingredient.count }
                            .all { (ingredientId, amount) ->
                                val removed = -(changes[ingredientId] ?: 0).coerceAtMost(0)
                                removed in 1..(amount * crafts)
                            }
                    }
                    .flatMap { recipe -> recipe.ingredients.asSequence().map { it.id } }
            }
            .filter(allowedItems::contains)
            .toSet()

    private fun markActivity(type: SkyBlockSlayerType) {
        lastActivityAtMillis[type] = System.currentTimeMillis()
    }

    private fun resetSession() {
        sessionStats.clear()
        pendingQuestStart = false
        attributionType = null
        inactiveAttributionTicks = 0
        durationType = null
        durationTicks = 0
        previousPresetType = null
        previousPresetLeftAtMillis = 0L
        lastActivityAtMillis.clear()
        itemTracking.clear()
    }

    private fun newStats() = ProfileStorage.ProfitTrackerStats()
}

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
private const val MAXIMUM_MOB_KILL_COINS = 100_000.0

enum class ProfitTrackingPeriod(val displayName: String) {
    SESSION("Session"),
    TODAY("Today"),
    TOTAL("Total"),
}
