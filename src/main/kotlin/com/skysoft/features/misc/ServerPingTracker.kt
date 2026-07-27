package com.skysoft.features.misc

import kotlin.math.roundToInt

internal class ServerPingTracker(
    private val requestIntervalNanos: Long = DEFAULT_REQUEST_INTERVAL_NANOS,
    private val requestTimeoutNanos: Long = DEFAULT_REQUEST_TIMEOUT_NANOS,
) {
    private var pendingRequestId: Long? = null
    private var pendingSentAtNanos: Long? = null
    private var lastRequestAtNanos: Long? = null

    var pingMs: Int? = null
        private set

    init {
        require(requestIntervalNanos > 0L) { "Ping request interval must be positive" }
        require(requestTimeoutNanos >= requestIntervalNanos) {
            "Ping request timeout must be at least the request interval"
        }
    }

    fun requestForTick(timestampNanos: Long, requestId: Long): Long? {
        val pendingTimestamp = pendingSentAtNanos
        if (pendingTimestamp != null) {
            val pendingElapsedNanos = timestampNanos - pendingTimestamp
            if (pendingElapsedNanos < 0L) {
                clearMeasurements()
            } else if (pendingElapsedNanos < requestTimeoutNanos) {
                return null
            } else {
                pendingRequestId = null
                pendingSentAtNanos = null
                pingMs = null
            }
        }

        val previousRequestAtNanos = lastRequestAtNanos
        if (previousRequestAtNanos != null) {
            val elapsedNanos = timestampNanos - previousRequestAtNanos
            if (elapsedNanos < 0L) {
                clearMeasurements()
            } else if (elapsedNanos < requestIntervalNanos) {
                return null
            }
        }

        pendingRequestId = requestId
        pendingSentAtNanos = timestampNanos
        lastRequestAtNanos = timestampNanos
        return requestId
    }

    fun recordPong(requestId: Long, timestampNanos: Long): PingSampleResult {
        if (requestId != pendingRequestId) return PingSampleResult.IGNORED_UNMATCHED_RESPONSE

        val sentAtNanos = pendingSentAtNanos
        if (sentAtNanos == null || timestampNanos < sentAtNanos) {
            clearMeasurements()
            return PingSampleResult.RESET_NON_MONOTONIC_TIME
        }

        val roundTripNanos = timestampNanos - sentAtNanos
        pingMs = (roundTripNanos / NANOS_PER_MILLISECOND).roundToInt()
        pendingRequestId = null
        pendingSentAtNanos = null
        return PingSampleResult.ACCEPTED
    }

    fun reset() = clearMeasurements()

    private fun clearMeasurements() {
        pingMs = null
        pendingRequestId = null
        pendingSentAtNanos = null
        lastRequestAtNanos = null
    }

    private companion object {
        const val DEFAULT_REQUEST_INTERVAL_NANOS = 1_000_000_000L
        const val DEFAULT_REQUEST_TIMEOUT_NANOS = 5_000_000_000L
        const val NANOS_PER_MILLISECOND = 1_000_000.0
    }
}

internal enum class PingSampleResult {
    ACCEPTED,
    IGNORED_INACTIVE,
    IGNORED_UNMATCHED_RESPONSE,
    RESET_NON_MONOTONIC_TIME,
}
