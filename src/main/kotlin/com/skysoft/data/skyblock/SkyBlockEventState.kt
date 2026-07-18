package com.skysoft.data.skyblock

import com.skysoft.data.hypixel.HypixelLocationState
import com.skysoft.data.hypixel.TabListApi
import com.skysoft.utils.ActiveConsumerRegistry
import com.skysoft.utils.SkysoftClientEvents
import com.skysoft.utils.TextUtilities.cleanSkyBlockText

enum class SkyBlockEvent(val displayName: String) {
    MYTHOLOGICAL_RITUAL("Mythological Ritual"),
    CARNIVAL("Carnival"),
    FISHING_FESTIVAL("Fishing Festival"),
    MINING_FIESTA("Mining Fiesta"),
    SPOOKY_FESTIVAL("Spooky Festival"),
    GREAT_SPOOK("Great Spook"),
    HOPPITY_HUNT("Hoppity's Hunt"),
    NEW_YEAR_CELEBRATION("New Year Celebration"),
    SEASON_OF_JERRY("Season of Jerry"),
    TRAVELING_ZOO("Traveling Zoo"),
    HARVEST_FEAST("Harvest Feast"),
    ENDER_DRAGON_FIGHT("Ender Dragon Fight"),
    YEAR_OF_THE_PIG("Year of the Pig"),
    YEAR_OF_THE_SEAL("Year of the Seal"),
    YEAR_OF_THE_WITCH("Year of the Witch"),
}

object SkyBlockEventState {
    private val consumers = ActiveConsumerRegistry()
    @Volatile
    private var snapshot = SkyBlockEventSnapshot()
    private var ticks = 0
    private var wasActive = false

    @Volatile
    var version: Long = 0
        private set

    fun register() {
        TabListApi.registerConsumer("SkyBlock Event State") { consumers.hasActiveConsumers }
        MayorPerkApi.registerConsumer("SkyBlock Event State") { consumers.hasActiveConsumers }
        SkyBlockEventScheduleApi.registerConsumer("SkyBlock Event State") { consumers.hasActiveConsumers }
        SkysoftClientEvents.onEndTick(
            "SkyBlock Event state refresh",
            isActive = { consumers.hasActiveConsumers || wasActive },
        ) {
            val isActive = consumers.hasActiveConsumers
            if (!isActive) {
                if (wasActive) clear()
                wasActive = false
                ticks = 0
                return@onEndTick
            }
            if (!wasActive) {
                wasActive = true
                refresh()
                return@onEndTick
            }
            if (++ticks % REFRESH_INTERVAL_TICKS != 0) return@onEndTick
            refresh()
        }
    }

    fun registerConsumer(id: String, isActive: () -> Boolean) {
        consumers.register(id, isActive)
    }

    internal val hasActiveConsumers: Boolean
        get() = consumers.hasActiveConsumers

    fun isActive(event: SkyBlockEvent): Boolean = event in snapshot.activeEvents

    fun activeEvents(): Set<SkyBlockEvent> = snapshot.activeEvents

    internal fun availability(
        availability: SkyBlockNpcAvailability,
        nowMillis: Long = System.currentTimeMillis(),
    ): SkyBlockEventAvailability {
        if (availability.event in snapshot.activeEvents) return SkyBlockEventAvailability.ACTIVE
        SkyBlockEventScheduleApi.availability(
            availability.event,
            nowMillis,
            availability.startsBeforeMinutes,
            availability.durationMinutes,
        )?.let { return if (it) SkyBlockEventAvailability.ACTIVE else SkyBlockEventAvailability.INACTIVE }
        localAvailability(availability, nowMillis)?.let {
            return if (it) SkyBlockEventAvailability.ACTIVE else SkyBlockEventAvailability.INACTIVE
        }
        return SkyBlockEventAvailability.UNKNOWN
    }

    private fun refresh() {
        val nowMillis = System.currentTimeMillis()
        val next = resolveSkyBlockEvents(
            SkyBlockEventSignals(
                isInSkyBlock = HypixelLocationState.inSkyBlock,
                nowMillis = nowMillis,
                isMythologicalRitualPerkActive = MayorPerkApi.mythologicalRitualActive,
                isCarnivalPerkActive = MayorPerkApi.carnivalActive,
                isFishingFestivalPerkActive = MayorPerkApi.fishingFestivalActive,
                isMiningFiestaPerkActive = MayorPerkApi.miningFiestaActive,
                backendActiveEvents = SkyBlockEventScheduleApi.activeEvents(nowMillis),
                tabLines = TabListApi.skyBlockLines.map { it.cleanSkyBlockText() },
            ),
        )
        if (next == snapshot) return
        snapshot = next
        version++
    }

    private fun clear() {
        if (snapshot == SkyBlockEventSnapshot()) return
        snapshot = SkyBlockEventSnapshot()
        version++
    }

    private const val REFRESH_INTERVAL_TICKS = 20
}

internal data class SkyBlockEventSignals(
    val isInSkyBlock: Boolean,
    val nowMillis: Long,
    val isMythologicalRitualPerkActive: Boolean = false,
    val isCarnivalPerkActive: Boolean = false,
    val isFishingFestivalPerkActive: Boolean = false,
    val isMiningFiestaPerkActive: Boolean = false,
    val backendActiveEvents: Set<SkyBlockEvent> = emptySet(),
    val tabLines: List<String> = emptyList(),
)

internal data class SkyBlockEventSnapshot(
    val activeEvents: Set<SkyBlockEvent> = emptySet(),
    val sources: Map<SkyBlockEvent, Set<SkyBlockEventSource>> = emptyMap(),
)

internal enum class SkyBlockEventSource {
    MAYOR_PERK,
    SKYBLOCK_CALENDAR,
    TAB_LIST,
    BACKEND_SCHEDULE,
}

internal enum class SkyBlockEventAvailability {
    ACTIVE,
    INACTIVE,
    UNKNOWN,
}

internal fun resolveSkyBlockEvents(signals: SkyBlockEventSignals): SkyBlockEventSnapshot {
    if (!signals.isInSkyBlock) return SkyBlockEventSnapshot()
    val sources = mutableMapOf<SkyBlockEvent, MutableSet<SkyBlockEventSource>>()

    fun activate(event: SkyBlockEvent, source: SkyBlockEventSource) {
        sources.getOrPut(event, ::mutableSetOf) += source
    }

    if (signals.isMythologicalRitualPerkActive) {
        activate(SkyBlockEvent.MYTHOLOGICAL_RITUAL, SkyBlockEventSource.MAYOR_PERK)
    }
    if (signals.isCarnivalPerkActive) {
        activate(SkyBlockEvent.CARNIVAL, SkyBlockEventSource.MAYOR_PERK)
    }

    val date = SkyBlockCalendar.dateAt(signals.nowMillis)
    if (signals.isFishingFestivalPerkActive && date.day in FISHING_FESTIVAL_DAYS) {
        activate(SkyBlockEvent.FISHING_FESTIVAL, SkyBlockEventSource.SKYBLOCK_CALENDAR)
    }
    if (
        signals.isMiningFiestaPerkActive &&
        date.month in MINING_FIESTA_MONTHS &&
        date.day in MINING_FIESTA_DAYS
    ) {
        activate(SkyBlockEvent.MINING_FIESTA, SkyBlockEventSource.SKYBLOCK_CALENDAR)
    }
    if (date.month == SPOOKY_FESTIVAL_MONTH && date.day in SPOOKY_FESTIVAL_DAYS) {
        activate(SkyBlockEvent.SPOOKY_FESTIVAL, SkyBlockEventSource.SKYBLOCK_CALENDAR)
    }
    when (date.year % YEAR_EVENT_CYCLE) {
        YEAR_OF_THE_PIG_OFFSET -> activate(SkyBlockEvent.YEAR_OF_THE_PIG, SkyBlockEventSource.SKYBLOCK_CALENDAR)
        YEAR_OF_THE_SEAL_OFFSET -> activate(SkyBlockEvent.YEAR_OF_THE_SEAL, SkyBlockEventSource.SKYBLOCK_CALENDAR)
        YEAR_OF_THE_WITCH_OFFSET -> activate(SkyBlockEvent.YEAR_OF_THE_WITCH, SkyBlockEventSource.SKYBLOCK_CALENDAR)
    }

    signals.backendActiveEvents.forEach { activate(it, SkyBlockEventSource.BACKEND_SCHEDULE) }
    activeTabEvents(signals.tabLines).forEach { activate(it, SkyBlockEventSource.TAB_LIST) }
    return SkyBlockEventSnapshot(
        activeEvents = sources.keys.toSet(),
        sources = sources.mapValues { it.value.toSet() },
    )
}

internal object SkyBlockCalendar {
    fun dateAt(nowMillis: Long): SkyBlockDate {
        val elapsed = (nowMillis - EPOCH_MILLIS).coerceAtLeast(0L)
        val year = (elapsed / YEAR_MILLIS).toInt() + 1
        val withinYear = elapsed % YEAR_MILLIS
        val month = (withinYear / MONTH_MILLIS).toInt() + 1
        val day = ((withinYear % MONTH_MILLIS) / DAY_MILLIS).toInt() + 1
        return SkyBlockDate(year, month, day)
    }

    fun startOf(year: Int, month: Int = 1, day: Int = 1): Long =
        EPOCH_MILLIS +
            (year - 1L) * YEAR_MILLIS +
            (month - 1L) * MONTH_MILLIS +
            (day - 1L) * DAY_MILLIS

    fun isWithinAnnualWindows(
        nowMillis: Long,
        windows: List<SkyBlockAnnualWindow>,
        startsBeforeMinutes: Int,
        durationMinutes: Int,
    ): Boolean {
        val year = dateAt(nowMillis).year
        return windows.any { window ->
            val eventStart = startOf(year, window.month, window.day)
            val availableStart = eventStart - startsBeforeMinutes * REAL_MINUTE_MILLIS
            val availableEnd = if (durationMinutes > 0) {
                availableStart + durationMinutes * REAL_MINUTE_MILLIS
            } else {
                eventStart + window.durationMinutes * REAL_MINUTE_MILLIS
            }
            nowMillis in availableStart until availableEnd
        }
    }

    private const val EPOCH_MILLIS = 1_559_829_300_000L
    private const val REAL_MINUTE_MILLIS = 60_000L
    private const val DAY_MILLIS = 20 * 60 * 1_000L
    private const val MONTH_MILLIS = DAY_MILLIS * 31
    private const val YEAR_MILLIS = MONTH_MILLIS * 12
}

internal data class SkyBlockDate(val year: Int, val month: Int, val day: Int)
internal data class SkyBlockAnnualWindow(val month: Int, val day: Int, val durationMinutes: Int)

private fun localAvailability(availability: SkyBlockNpcAvailability, nowMillis: Long): Boolean? {
    val windows = ANNUAL_EVENT_WINDOWS[availability.event]
    if (windows != null) {
        return SkyBlockCalendar.isWithinAnnualWindows(
            nowMillis,
            windows,
            availability.startsBeforeMinutes,
            availability.durationMinutes,
        )
    }
    if (availability.startsBeforeMinutes > 0 || availability.durationMinutes > 0) return null
    return when (availability.event) {
        SkyBlockEvent.YEAR_OF_THE_PIG,
        SkyBlockEvent.YEAR_OF_THE_SEAL,
        SkyBlockEvent.YEAR_OF_THE_WITCH,
        -> availability.event in resolveSkyBlockEvents(
            SkyBlockEventSignals(isInSkyBlock = true, nowMillis = nowMillis),
        ).activeEvents
        else -> null
    }
}

private fun activeTabEvents(lines: List<String>): Set<SkyBlockEvent> = buildSet {
    lines.forEachIndexed { index, line ->
        val cleanLine = line.trim()
        val eventName = cleanLine.removePrefix(EVENT_PREFIX)
            .takeIf { cleanLine.startsWith(EVENT_PREFIX) }
            ?: return@forEachIndexed
        val hasEndTime = lines.drop(index + 1).take(TAB_EVENT_DETAIL_LINES).any {
            it.trim().startsWith(EVENT_END_PREFIX)
        }
        if (!hasEndTime) return@forEachIndexed
        SkyBlockEvent.entries.firstOrNull { it.displayName.equals(eventName, ignoreCase = true) }?.let(::add)
    }
}

private val FISHING_FESTIVAL_DAYS = 1..3
private val MINING_FIESTA_MONTHS = 5..9
private val MINING_FIESTA_DAYS = 1..7
private const val SPOOKY_FESTIVAL_MONTH = 8
private val SPOOKY_FESTIVAL_DAYS = 29..31
private const val YEAR_EVENT_CYCLE = 12
private const val YEAR_OF_THE_PIG_OFFSET = 0
private const val YEAR_OF_THE_SEAL_OFFSET = 6
private const val YEAR_OF_THE_WITCH_OFFSET = 8
private const val EVENT_PREFIX = "Event: "
private const val EVENT_END_PREFIX = "Ends In:"
private const val TAB_EVENT_DETAIL_LINES = 3
private const val ONE_HOUR_MINUTES = 60
private const val HOPPITY_HUNT_DURATION_MINUTES = 31 * ONE_HOUR_MINUTES
private val ANNUAL_EVENT_WINDOWS = mapOf(
    SkyBlockEvent.SPOOKY_FESTIVAL to listOf(SkyBlockAnnualWindow(8, 29, ONE_HOUR_MINUTES)),
    SkyBlockEvent.TRAVELING_ZOO to listOf(
        SkyBlockAnnualWindow(4, 1, ONE_HOUR_MINUTES),
        SkyBlockAnnualWindow(10, 1, ONE_HOUR_MINUTES),
    ),
    SkyBlockEvent.NEW_YEAR_CELEBRATION to listOf(SkyBlockAnnualWindow(12, 29, ONE_HOUR_MINUTES)),
    SkyBlockEvent.HOPPITY_HUNT to listOf(SkyBlockAnnualWindow(1, 1, HOPPITY_HUNT_DURATION_MINUTES)),
)
