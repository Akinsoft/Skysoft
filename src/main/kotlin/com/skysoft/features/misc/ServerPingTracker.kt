package com.skysoft.features.misc

import kotlin.math.roundToInt

internal class ServerPingTracker {
    private var pendingRequest: PendingPing? = null
    private var lastRequestAtNanos: Long? = null

    var pingMs: Int? = null
        private set

    fun requestForTick(timestampNanos: Long, requestId: Long): Long? {
        val pendingTimestamp = pendingRequest?.sentAtNanos
        if (pendingTimestamp != null) {
            val pendingElapsedNanos = timestampNanos - pendingTimestamp
            if (pendingElapsedNanos < 0L) {
                clearMeasurements()
            } else if (pendingElapsedNanos < REQUEST_TIMEOUT_NANOS) {
                return null
            } else {
                pendingRequest = null
                pingMs = null
            }
        }

        val previousRequestAtNanos = lastRequestAtNanos
        if (previousRequestAtNanos != null) {
            val elapsedNanos = timestampNanos - previousRequestAtNanos
            if (elapsedNanos < 0L) {
                clearMeasurements()
            } else if (elapsedNanos < REQUEST_INTERVAL_NANOS) {
                return null
            }
        }

        pendingRequest = PendingPing(requestId, timestampNanos)
        lastRequestAtNanos = timestampNanos
        return requestId
    }

    fun recordPong(requestId: Long, timestampNanos: Long) {
        val pending = pendingRequest?.takeIf { it.requestId == requestId } ?: return
        if (timestampNanos < pending.sentAtNanos) {
            clearMeasurements()
            return
        }
        pingMs = ((timestampNanos - pending.sentAtNanos) / NANOS_PER_MILLISECOND).roundToInt()
        pendingRequest = null
    }

    fun reset() = clearMeasurements()

    private fun clearMeasurements() {
        pingMs = null
        pendingRequest = null
        lastRequestAtNanos = null
    }

    private data class PendingPing(val requestId: Long, val sentAtNanos: Long)

    private companion object {
        const val REQUEST_INTERVAL_NANOS = 1_000_000_000L
        const val REQUEST_TIMEOUT_NANOS = 5_000_000_000L
        const val NANOS_PER_MILLISECOND = 1_000_000.0
    }
}
