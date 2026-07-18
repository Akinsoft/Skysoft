package com.skysoft.features.misc

import kotlin.math.roundToInt

internal class ServerPingTracker(
    private val requestIntervalNanos: Long = DEFAULT_REQUEST_INTERVAL_NANOS,
    private val requestTimeoutNanos: Long = DEFAULT_REQUEST_TIMEOUT_NANOS,
) {
    private var pendingRequestId: Long? = null
    private var pendingSentAtNanos: Long? = null
    private var lastRequestAtNanos: Long? = null
    private var lastResponseAtNanos: Long? = null
    private var lastRoundTripNanos: Long? = null
    private var lastUnmatchedRequestId: Long? = null
    private var lastResult = PingSampleResult.RESET
    private var lastFailureResult: PingSampleResult? = null
    private var requestCount = 0
    private var acceptedResponseCount = 0
    private var unmatchedResponseCount = 0
    private var timeoutCount = 0

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
                lastResult = PingSampleResult.RESET_NON_MONOTONIC_TIME
                lastFailureResult = lastResult
            } else if (pendingElapsedNanos < requestTimeoutNanos) {
                return null
            } else {
                pendingRequestId = null
                pendingSentAtNanos = null
                pingMs = null
                timeoutCount++
                lastResult = PingSampleResult.TIMED_OUT
                lastFailureResult = lastResult
            }
        }

        val previousRequestAtNanos = lastRequestAtNanos
        if (previousRequestAtNanos != null) {
            val elapsedNanos = timestampNanos - previousRequestAtNanos
            if (elapsedNanos < 0L) {
                clearMeasurements()
                lastResult = PingSampleResult.RESET_NON_MONOTONIC_TIME
                lastFailureResult = lastResult
            } else if (elapsedNanos < requestIntervalNanos) {
                return null
            }
        }

        pendingRequestId = requestId
        pendingSentAtNanos = timestampNanos
        lastRequestAtNanos = timestampNanos
        requestCount++
        lastResult = PingSampleResult.REQUESTED
        return requestId
    }

    fun recordPong(requestId: Long, timestampNanos: Long): PingSampleResult {
        if (requestId != pendingRequestId) {
            lastUnmatchedRequestId = requestId
            unmatchedResponseCount++
            return PingSampleResult.IGNORED_UNMATCHED_RESPONSE
        }

        val sentAtNanos = pendingSentAtNanos
        if (sentAtNanos == null || timestampNanos < sentAtNanos) {
            clearMeasurements()
            lastResult = PingSampleResult.RESET_NON_MONOTONIC_TIME
            lastFailureResult = lastResult
            return lastResult
        }

        val roundTripNanos = timestampNanos - sentAtNanos
        pingMs = (roundTripNanos / NANOS_PER_MILLISECOND).roundToInt()
        pendingRequestId = null
        pendingSentAtNanos = null
        lastResponseAtNanos = timestampNanos
        lastRoundTripNanos = roundTripNanos
        acceptedResponseCount++
        lastResult = PingSampleResult.ACCEPTED
        return lastResult
    }

    fun reset() {
        clearMeasurements()
        lastUnmatchedRequestId = null
        requestCount = 0
        acceptedResponseCount = 0
        unmatchedResponseCount = 0
        timeoutCount = 0
        lastResult = PingSampleResult.RESET
        lastFailureResult = null
    }

    fun snapshot(): ServerPingSnapshot = ServerPingSnapshot(
        pingMs = pingMs,
        pendingRequestId = pendingRequestId,
        pendingSentAtNanos = pendingSentAtNanos,
        lastRequestAtNanos = lastRequestAtNanos,
        lastResponseAtNanos = lastResponseAtNanos,
        lastRoundTripNanos = lastRoundTripNanos,
        lastUnmatchedRequestId = lastUnmatchedRequestId,
        lastResult = lastResult,
        lastFailureResult = lastFailureResult,
        requestCount = requestCount,
        acceptedResponseCount = acceptedResponseCount,
        unmatchedResponseCount = unmatchedResponseCount,
        timeoutCount = timeoutCount,
    )

    private fun clearMeasurements() {
        pingMs = null
        pendingRequestId = null
        pendingSentAtNanos = null
        lastRequestAtNanos = null
        lastResponseAtNanos = null
        lastRoundTripNanos = null
    }

    private companion object {
        const val DEFAULT_REQUEST_INTERVAL_NANOS = 1_000_000_000L
        const val DEFAULT_REQUEST_TIMEOUT_NANOS = 5_000_000_000L
        const val NANOS_PER_MILLISECOND = 1_000_000.0
    }
}

internal enum class PingSampleResult {
    RESET,
    REQUESTED,
    ACCEPTED,
    TIMED_OUT,
    IGNORED_INACTIVE,
    IGNORED_UNMATCHED_RESPONSE,
    RESET_NON_MONOTONIC_TIME,
}

internal data class ServerPingSnapshot(
    val pingMs: Int?,
    val pendingRequestId: Long?,
    val pendingSentAtNanos: Long?,
    val lastRequestAtNanos: Long?,
    val lastResponseAtNanos: Long?,
    val lastRoundTripNanos: Long?,
    val lastUnmatchedRequestId: Long?,
    val lastResult: PingSampleResult,
    val lastFailureResult: PingSampleResult?,
    val requestCount: Int,
    val acceptedResponseCount: Int,
    val unmatchedResponseCount: Int,
    val timeoutCount: Int,
)
