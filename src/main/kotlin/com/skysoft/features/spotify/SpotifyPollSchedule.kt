package com.skysoft.features.spotify

import kotlin.math.max
import kotlin.math.min

internal class SpotifyPollSchedule {
    private var consecutiveFailures = 0
    private var consecutiveRateLimits = 0
    private var apiLimitDeadlineNanos: Long? = null

    fun isDue(nowNanos: Long, deadlineNanos: Long?): Boolean =
        deadlineNanos == null || nowNanos - deadlineNanos >= 0L

    fun requestedAt(nowNanos: Long): Long = enforceApiLimit(nowNanos, nowNanos)

    fun nextAfterPlayback(
        nowNanos: Long,
        remainingMillis: Long,
        isPlaying: Boolean,
    ): Long {
        recordSuccess(nowNanos)
        val delay = if (isPlaying) playingDelay(remainingMillis) else PAUSED_POLL_INTERVAL_MILLIS
        return enforceApiLimit(nowNanos, deadline(nowNanos, delay))
    }

    fun nextAfterNoPlayback(nowNanos: Long): Long {
        recordSuccess(nowNanos)
        return enforceApiLimit(nowNanos, deadline(nowNanos, NO_PLAYBACK_POLL_INTERVAL_MILLIS))
    }

    fun nextAfterFailure(nowNanos: Long): Long {
        consecutiveFailures++
        val delay = exponentialDelay(
            FAILURE_RETRY_INTERVAL_MILLIS,
            consecutiveFailures,
            MAXIMUM_FAILURE_RETRY_MILLIS,
        )
        return enforceApiLimit(nowNanos, deadline(nowNanos, delay))
    }

    fun nextAfterRateLimit(nowNanos: Long, retryAfterMillis: Long?): Long {
        consecutiveFailures = 0
        consecutiveRateLimits++
        val delay = max(
            retryAfterMillis?.coerceAtLeast(0L) ?: 0L,
            exponentialDelay(
                RATE_LIMIT_RETRY_INTERVAL_MILLIS,
                consecutiveRateLimits,
                MAXIMUM_RATE_LIMIT_RETRY_MILLIS,
            ),
        )
        return recordApiLimit(nowNanos, delay)
    }

    fun nextAfterQuotaLimit(nowNanos: Long, retryAfterMillis: Long?): Long {
        consecutiveFailures = 0
        val delay = max(retryAfterMillis?.coerceAtLeast(0L) ?: 0L, QUOTA_RETRY_INTERVAL_MILLIS)
        return recordApiLimit(nowNanos, delay)
    }

    fun reset() {
        consecutiveFailures = 0
        consecutiveRateLimits = 0
        apiLimitDeadlineNanos = null
    }

    private fun playingDelay(remainingMillis: Long): Long {
        val transitionDelay = remainingMillis.coerceAtLeast(0L)
            .coerceAtMost(PLAYING_POLL_INTERVAL_MILLIS) + TRACK_TRANSITION_GRACE_MILLIS
        return min(
            PLAYING_POLL_INTERVAL_MILLIS,
            max(MINIMUM_PLAYING_POLL_INTERVAL_MILLIS, transitionDelay),
        )
    }

    private fun recordSuccess(nowNanos: Long) {
        consecutiveFailures = 0
        val limit = apiLimitDeadlineNanos ?: return
        if (isDue(nowNanos, limit)) {
            apiLimitDeadlineNanos = null
            consecutiveRateLimits = 0
        }
    }

    private fun recordApiLimit(nowNanos: Long, delayMillis: Long): Long {
        val candidate = deadline(nowNanos, delayMillis)
        val current = apiLimitDeadlineNanos
        val next = if (current == null || candidate - current >= 0L) candidate else current
        apiLimitDeadlineNanos = next
        return next
    }

    private fun enforceApiLimit(nowNanos: Long, candidate: Long): Long {
        val limit = apiLimitDeadlineNanos ?: return candidate
        if (isDue(nowNanos, limit)) return candidate
        return if (candidate - limit >= 0L) candidate else limit
    }

    private fun deadline(nowNanos: Long, delayMillis: Long): Long =
        nowNanos + delayMillis.coerceAtMost(MAXIMUM_SAFE_DELAY_MILLIS) * NANOS_PER_MILLISECOND

    private fun exponentialDelay(baseMillis: Long, attempt: Int, maximumMillis: Long): Long {
        val shift = (attempt - 1).coerceIn(0, MAXIMUM_BACKOFF_SHIFT)
        val multiplier = 1L shl shift
        return if (baseMillis > maximumMillis / multiplier) maximumMillis else baseMillis * multiplier
    }

    private companion object {
        const val PLAYING_POLL_INTERVAL_MILLIS = 15_000L
        const val MINIMUM_PLAYING_POLL_INTERVAL_MILLIS = 5_000L
        const val TRACK_TRANSITION_GRACE_MILLIS = 1_000L
        const val PAUSED_POLL_INTERVAL_MILLIS = 30_000L
        const val NO_PLAYBACK_POLL_INTERVAL_MILLIS = 30_000L
        const val FAILURE_RETRY_INTERVAL_MILLIS = 15_000L
        const val MAXIMUM_FAILURE_RETRY_MILLIS = 5L * 60L * 1_000L
        const val RATE_LIMIT_RETRY_INTERVAL_MILLIS = 30_000L
        const val MAXIMUM_RATE_LIMIT_RETRY_MILLIS = 15L * 60L * 1_000L
        const val QUOTA_RETRY_INTERVAL_MILLIS = 60L * 60L * 1_000L
        const val NANOS_PER_MILLISECOND = 1_000_000L
        const val MAXIMUM_SAFE_DELAY_MILLIS = Long.MAX_VALUE / NANOS_PER_MILLISECOND / 2L
        const val MAXIMUM_BACKOFF_SHIFT = 30
    }
}
