package com.skysoft.data.skyblock

import com.google.gson.Gson
import com.skysoft.SkysoftMod
import com.skysoft.data.hypixel.HypixelLocationState
import com.skysoft.utils.ActiveConsumerRegistry
import com.skysoft.utils.ConsumerActivity
import com.skysoft.utils.net.AsyncRequestSlot
import com.skysoft.utils.net.PendingHttpRequests
import com.skysoft.utils.net.RefreshSchedule
import com.skysoft.utils.net.isCancellationFailure
import com.skysoft.utils.SkysoftClientEvents
import com.skysoft.utils.SkysoftErrorBoundary

object SkyBlockEventScheduleApi {
    private val gson = Gson()
    private val consumers = ActiveConsumerRegistry()
    private val requests = PendingHttpRequests()
    private val requestSlot = AsyncRequestSlot<SkyBlockEventSchedule>()
    private val refreshSchedule = RefreshSchedule()

    @Volatile
    private var schedule = SkyBlockEventSchedule()

    fun register() {
        SkysoftClientEvents.onEndTick(
            "SkyBlock Event Schedule refresh",
            isActive = { consumers.isActiveOrDeactivating },
        ) tick@{
            when (consumers.activity()) {
                ConsumerActivity.INACTIVE -> return@tick
                ConsumerActivity.DEACTIVATED -> {
                    reset()
                    return@tick
                }
                ConsumerActivity.ACTIVATED,
                ConsumerActivity.ACTIVE,
                -> Unit
            }
            if (!HypixelLocationState.inSkyBlock) {
                refreshSchedule.reset()
                return@tick
            }
            if (refreshSchedule.isDue(System.currentTimeMillis())) refresh()
        }
        SkysoftClientEvents.onClientStopping("SkyBlock Event Schedule request cancellation") {
            requestSlot.cancel()
            requests.cancelAll()
        }
    }

    fun registerConsumer(id: String, isActive: () -> Boolean) {
        consumers.register(id, isActive)
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
        requestSlot.startIfIdle(
            requestFactory = {
                requests.getString(EVENTS_URL)
                    .thenApply { gson.fromJson(it, SkyBlockEventScheduleResponse::class.java) }
                    .thenApply(::normalizeSchedule)
            },
        ) { response, error ->
            SkysoftErrorBoundary.run("SkyBlock Event Schedule async completion") {
                val now = System.currentTimeMillis()
                if (error == null && response != null) {
                    schedule = response
                    refreshSchedule.schedule(now, REFRESH_INTERVAL_MILLIS)
                } else if (error?.isCancellationFailure() != true) {
                    refreshSchedule.schedule(now, FAILURE_RETRY_MILLIS)
                    SkysoftMod.LOGGER.warn("Failed to refresh SkyBlock event schedule", error)
                }
            }
        }
    }

    private fun reset() {
        requestSlot.cancel()
        requests.cancelAll()
        schedule = SkyBlockEventSchedule()
        refreshSchedule.reset()
    }

    private const val EVENTS_URL = "https://api.findthesoft.com/skyblock/events"
    private const val REFRESH_INTERVAL_MILLIS = 5L * 60L * 1_000L
    private const val FAILURE_RETRY_MILLIS = 60_000L
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
