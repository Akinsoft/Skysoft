package com.skysoft.data.skyblock

import com.google.gson.Gson
import com.skysoft.SkysoftMod
import com.skysoft.data.hypixel.HypixelLocationState
import com.skysoft.utils.net.PendingHttpRequests
import com.skysoft.utils.SkysoftClientEvents
import com.skysoft.utils.SkysoftErrorBoundary
import java.util.concurrent.atomic.AtomicBoolean

object SkyBlockEventScheduleApi {
    private val gson = Gson()
    private val requests = PendingHttpRequests()
    private val loading = AtomicBoolean(false)

    @Volatile
    private var schedule = SkyBlockEventSchedule()
    private var ticksUntilRefresh = 0

    fun register() {
        SkysoftClientEvents.onEndTick("SkyBlock Event Schedule refresh") tick@{
            if (!HypixelLocationState.inSkyBlock) {
                ticksUntilRefresh = 0
                return@tick
            }
            if (ticksUntilRefresh-- > 0) return@tick
            ticksUntilRefresh = REFRESH_INTERVAL_TICKS
            refresh()
        }
        SkysoftClientEvents.onClientStopping("SkyBlock Event Schedule request cancellation") {
            requests.cancelAll()
        }
    }

    fun activeEvents(nowMillis: Long): Set<SkyBlockEvent> {
        val current = schedule
        if (nowMillis - current.fetchedAt > MAX_SCHEDULE_AGE_MILLIS) return emptySet()
        return current.windows.asSequence()
            .filter { nowMillis in it.startsAt until it.endsAt }
            .mapTo(mutableSetOf()) { it.event }
    }

    internal fun availability(
        event: SkyBlockEvent,
        nowMillis: Long,
        startsBeforeMinutes: Int,
        durationMinutes: Int,
    ): Boolean? = scheduleAvailability(
        schedule,
        event,
        nowMillis,
        startsBeforeMinutes,
        durationMinutes,
        MAX_SCHEDULE_AGE_MILLIS,
    )

    private fun refresh() {
        if (!loading.compareAndSet(false, true)) return
        requests.getString(EVENTS_URL)
            .thenApply { gson.fromJson(it, SkyBlockEventScheduleResponse::class.java) }
            .thenApply(::normalizeSchedule)
            .whenComplete { response, error ->
                SkysoftErrorBoundary.run("SkyBlock Event Schedule async completion") {
                    try {
                        if (error == null && response != null) {
                            schedule = response
                        } else {
                            SkysoftMod.LOGGER.warn("Failed to refresh SkyBlock event schedule", error)
                        }
                    } finally {
                        loading.set(false)
                    }
                }
            }
    }

    private const val EVENTS_URL = "https://api.findthesoft.com/skyblock/events"
    private const val REFRESH_INTERVAL_TICKS = 20 * 60 * 5
    private const val MAX_SCHEDULE_AGE_MILLIS = 30 * 60 * 1_000L
}

internal data class SkyBlockEventSchedule(
    val fetchedAt: Long = 0L,
    val windows: List<SkyBlockEventWindow> = emptyList(),
    val unknownEventIds: Set<String> = emptySet(),
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

internal fun normalizeSchedule(response: SkyBlockEventScheduleResponse): SkyBlockEventSchedule {
    check(response.success) { "Skysoft event schedule failed: ${response.cause ?: "unknown cause"}" }
    check(response.fetchedAt > 0L) { "Skysoft event schedule has no fetch timestamp" }
    val unknownEventIds = mutableSetOf<String>()
    val windows = response.events.mapNotNull { window ->
        val event = runCatching { SkyBlockEvent.valueOf(window.id) }.getOrNull() ?: run {
            unknownEventIds += window.id
            return@mapNotNull null
        }
        check(window.startsAt < window.endsAt) { "Invalid ${window.id} event window" }
        SkyBlockEventWindow(event, window.startsAt, window.endsAt)
    }
    return SkyBlockEventSchedule(response.fetchedAt, windows, unknownEventIds)
}

internal fun scheduleAvailability(
    schedule: SkyBlockEventSchedule,
    event: SkyBlockEvent,
    nowMillis: Long,
    startsBeforeMinutes: Int,
    durationMinutes: Int,
    maximumAgeMillis: Long,
): Boolean? {
    if (nowMillis - schedule.fetchedAt !in 0..maximumAgeMillis) return null
    val windows = schedule.windows.filter { it.event == event }
    if (windows.isEmpty()) return null
    return windows.any { window ->
        val start = window.startsAt - startsBeforeMinutes * MILLIS_PER_MINUTE
        val end = if (durationMinutes > 0) start + durationMinutes * MILLIS_PER_MINUTE else window.endsAt
        nowMillis in start until end
    }
}

private const val MILLIS_PER_MINUTE = 60_000L
