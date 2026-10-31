package com.skysoft.data.skyblock

import com.google.gson.Gson
import com.skysoft.data.hypixel.HypixelLocationState
import com.skysoft.utils.ActiveConsumerRegistry
import com.skysoft.utils.net.PeriodicRefresh

object SkyBlockEventScheduleApi {
    private val gson = Gson()
    private val consumers = ActiveConsumerRegistry()
    private val request = PeriodicRefresh(
        name = "SkyBlock Event Schedule",
        description = "SkyBlock event schedule",
        url = EVENTS_URL,
        refreshIntervalMillis = REFRESH_INTERVAL_MILLIS,
        failureRetryMillis = FAILURE_RETRY_MILLIS,
        parse = { normalizeSchedule(gson.fromJson(it, SkyBlockEventScheduleResponse::class.java)) },
        onSuccess = { schedule = it },
    )

    @Volatile
    private var schedule = SkyBlockEventSchedule()

    fun register() {
        consumers.onEndTick("SkyBlock Event Schedule refresh", onDeactivated = ::reset) {
            if (HypixelLocationState.inSkyBlock) request.refreshIfDue() else request.resetSchedule()
        }
        request.register()
    }

    fun registerConsumer(id: String, isActive: () -> Boolean) {
        consumers.register(id, isActive)
    }

    fun activeEvents(nowMillis: Long): Set<SkyBlockEvent> {
        val current = schedule
        if (!current.isFreshAt(nowMillis, MAX_SCHEDULE_AGE_MILLIS)) return emptySet()
        return current.windows.asSequence()
            .filter { nowMillis in it.startsAt until it.endsAt }
            .mapTo(mutableSetOf()) { it.event }
    }

    internal fun availability(
        event: SkyBlockEvent,
        nowMillis: Long,
        startsBeforeMinutes: Int,
        durationMinutes: Int,
    ): Boolean? {
        val current = schedule
        if (!current.isFreshAt(nowMillis, MAX_SCHEDULE_AGE_MILLIS)) return null
        val windows = current.windows.filter { it.event == event }
        if (windows.isEmpty()) return null
        return windows.any { window ->
            SkyBlockCalendar.isAvailableAt(nowMillis, window.startsAt, window.endsAt, startsBeforeMinutes, durationMinutes)
        }
    }

    private fun reset() {
        request.reset()
        schedule = SkyBlockEventSchedule()
    }

    private const val EVENTS_URL = "https://api.findthesoft.com/skyblock/events"
    private const val REFRESH_INTERVAL_MILLIS = 5L * 60L * 1_000L
    private const val FAILURE_RETRY_MILLIS = 60_000L
    private const val MAX_SCHEDULE_AGE_MILLIS = 30 * 60 * 1_000L
}

internal data class SkyBlockEventSchedule(
    val fetchedAt: Long = 0L,
    val windows: List<SkyBlockEventWindow> = emptyList(),
)

internal data class SkyBlockEventWindow(
    val event: SkyBlockEvent,
    val startsAt: Long,
    val endsAt: Long,
)

internal data class SkyBlockEventScheduleResponse(
    val success: Boolean = false,
    val cause: String? = null,
    val fetchedAt: Long = 0L,
    val events: List<SkyBlockEventWindowResponse> = emptyList(),
)

internal data class SkyBlockEventWindowResponse(
    val id: String = "",
    val startsAt: Long = 0L,
    val endsAt: Long = 0L,
)

private fun normalizeSchedule(response: SkyBlockEventScheduleResponse): SkyBlockEventSchedule {
    check(response.success) { "Skysoft event schedule failed: ${response.cause ?: "unknown cause"}" }
    check(response.fetchedAt > 0L) { "Skysoft event schedule has no fetch timestamp" }
    val windows = response.events.mapNotNull { window ->
        val event = SkyBlockEvent.entries.firstOrNull { it.name == window.id } ?: return@mapNotNull null
        check(window.startsAt < window.endsAt) { "Invalid ${window.id} event window" }
        SkyBlockEventWindow(event, window.startsAt, window.endsAt)
    }
    return SkyBlockEventSchedule(response.fetchedAt, windows)
}

private fun SkyBlockEventSchedule.isFreshAt(nowMillis: Long, maximumAgeMillis: Long): Boolean =
    nowMillis - fetchedAt in 0..maximumAgeMillis
