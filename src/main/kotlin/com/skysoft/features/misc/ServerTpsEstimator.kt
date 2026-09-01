package com.skysoft.features.misc

import com.skysoft.utils.trimStartToSize
import java.util.ArrayDeque

internal class ServerTpsEstimator(private val sampleLimit: Int = DEFAULT_SAMPLE_LIMIT) {
    private val samples = ArrayDeque<Double>()
    private var previousGameTime: Long? = null
    private var previousTimestampNanos: Long? = null
    private var previousTargetTps: Double? = null

    init {
        require(sampleLimit > 0) { "TPS sample limit must be positive" }
    }

    val tps: Double?
        get() = samples.takeIf { it.isNotEmpty() }?.average()

    fun recordTimeUpdate(gameTime: Long, timestampNanos: Long, targetTps: Double): TpsSampleResult {
        if (!targetTps.isFinite() || targetTps <= 0.0) return TpsSampleResult.REJECTED_INVALID_TARGET
        if (previousTargetTps != null && previousTargetTps != targetTps) {
            clearMeasurements()
            rememberBaseline(gameTime, timestampNanos, targetTps)
            return TpsSampleResult.RESET_TARGET_CHANGED
        }

        val oldGameTime = previousGameTime
        val oldTimestampNanos = previousTimestampNanos
        if (oldGameTime == null || oldTimestampNanos == null) {
            rememberBaseline(gameTime, timestampNanos, targetTps)
            return TpsSampleResult.BASELINE
        }

        val gameTimeDelta = gameTime - oldGameTime
        val elapsedNanos = timestampNanos - oldTimestampNanos
        rememberBaseline(gameTime, timestampNanos, targetTps)
        if (gameTimeDelta <= 0L) {
            clearMeasurements()
            return TpsSampleResult.RESET_NON_MONOTONIC_TIME
        }
        if (elapsedNanos <= 0L) {
            clearMeasurements()
            return TpsSampleResult.RESET_INVALID_INTERVAL
        }

        val rawTps = gameTimeDelta * NANOS_PER_SECOND / elapsedNanos
        if (!rawTps.isFinite() || rawTps <= 0.0) {
            clearMeasurements()
            return TpsSampleResult.REJECTED_INVALID_SAMPLE
        }

        samples.addLast(rawTps.coerceAtMost(targetTps))
        samples.trimStartToSize(sampleLimit)
        return TpsSampleResult.ACCEPTED
    }

    fun reset() {
        clearMeasurements()
        previousGameTime = null
        previousTimestampNanos = null
        previousTargetTps = null
    }

    private fun rememberBaseline(gameTime: Long, timestampNanos: Long, targetTps: Double) {
        previousGameTime = gameTime
        previousTimestampNanos = timestampNanos
        previousTargetTps = targetTps
    }

    private fun clearMeasurements() {
        samples.clear()
    }

    private companion object {
        const val DEFAULT_SAMPLE_LIMIT = 5
        const val NANOS_PER_SECOND = 1_000_000_000.0
    }
}

internal enum class TpsSampleResult {
    BASELINE,
    ACCEPTED,
    RESET_TARGET_CHANGED,
    RESET_NON_MONOTONIC_TIME,
    RESET_INVALID_INTERVAL,
    REJECTED_INVALID_TARGET,
    REJECTED_INVALID_SAMPLE,
}
