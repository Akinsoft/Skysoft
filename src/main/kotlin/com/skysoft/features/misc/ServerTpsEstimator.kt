package com.skysoft.features.misc

internal class ServerTpsEstimator(private val sampleLimit: Int = DEFAULT_SAMPLE_LIMIT) {
    private val samples = ArrayDeque<Double>()
    private var previousGameTime: Long? = null
    private var previousTimestampNanos: Long? = null
    private var previousTargetTps: Double? = null
    private var lastGameTimeDelta: Long? = null
    private var lastElapsedNanos: Long? = null
    private var lastRawTps: Double? = null
    private var lastResult = TpsSampleResult.RESET
    private var lastRejectedResult: TpsSampleResult? = null
    private var lastResetResult = TpsSampleResult.RESET
    private var acceptedSampleCount = 0
    private var rejectedSampleCount = 0
    private var resetCount = 0

    init {
        require(sampleLimit > 0) { "TPS sample limit must be positive" }
    }

    val tps: Double?
        get() = samples.takeIf { it.isNotEmpty() }?.average()

    fun recordTimeUpdate(gameTime: Long, timestampNanos: Long, targetTps: Double): TpsSampleResult {
        if (!targetTps.isFinite() || targetTps <= 0.0) {
            rejectedSampleCount++
            lastResult = TpsSampleResult.REJECTED_INVALID_TARGET
            lastRejectedResult = lastResult
            return lastResult
        }
        if (previousTargetTps != null && previousTargetTps != targetTps) {
            clearMeasurements()
            rememberBaseline(gameTime, timestampNanos, targetTps)
            resetCount++
            lastResult = TpsSampleResult.RESET_TARGET_CHANGED
            lastResetResult = lastResult
            return lastResult
        }

        val oldGameTime = previousGameTime
        val oldTimestampNanos = previousTimestampNanos
        if (oldGameTime == null || oldTimestampNanos == null) {
            rememberBaseline(gameTime, timestampNanos, targetTps)
            lastResult = TpsSampleResult.BASELINE
            return lastResult
        }

        val gameTimeDelta = gameTime - oldGameTime
        val elapsedNanos = timestampNanos - oldTimestampNanos
        lastGameTimeDelta = gameTimeDelta
        lastElapsedNanos = elapsedNanos
        rememberBaseline(gameTime, timestampNanos, targetTps)
        if (gameTimeDelta <= 0L) {
            clearMeasurements()
            rejectedSampleCount++
            resetCount++
            lastResult = TpsSampleResult.RESET_NON_MONOTONIC_TIME
            lastRejectedResult = lastResult
            lastResetResult = lastResult
            return lastResult
        }
        if (elapsedNanos <= 0L) {
            clearMeasurements()
            rejectedSampleCount++
            resetCount++
            lastResult = TpsSampleResult.RESET_INVALID_INTERVAL
            lastRejectedResult = lastResult
            lastResetResult = lastResult
            return lastResult
        }

        val rawTps = gameTimeDelta * NANOS_PER_SECOND / elapsedNanos
        if (!rawTps.isFinite() || rawTps <= 0.0) {
            clearMeasurements()
            rejectedSampleCount++
            lastResult = TpsSampleResult.REJECTED_INVALID_SAMPLE
            lastRejectedResult = lastResult
            return lastResult
        }

        lastRawTps = rawTps
        samples.addLast(rawTps.coerceAtMost(targetTps))
        while (samples.size > sampleLimit) samples.removeFirst()
        acceptedSampleCount++
        lastResult = TpsSampleResult.ACCEPTED
        return lastResult
    }

    fun reset() {
        clearMeasurements()
        previousGameTime = null
        previousTimestampNanos = null
        previousTargetTps = null
        lastGameTimeDelta = null
        lastElapsedNanos = null
        lastRawTps = null
        acceptedSampleCount = 0
        rejectedSampleCount = 0
        resetCount = 1
        lastRejectedResult = null
        lastResult = TpsSampleResult.RESET
        lastResetResult = lastResult
    }

    fun snapshot(): ServerTpsSnapshot = ServerTpsSnapshot(
        tps = tps,
        samples = samples.toList(),
        previousGameTime = previousGameTime,
        previousTimestampNanos = previousTimestampNanos,
        targetTps = previousTargetTps,
        lastGameTimeDelta = lastGameTimeDelta,
        lastElapsedNanos = lastElapsedNanos,
        lastRawTps = lastRawTps,
        lastResult = lastResult,
        lastRejectedResult = lastRejectedResult,
        lastResetResult = lastResetResult,
        acceptedSampleCount = acceptedSampleCount,
        rejectedSampleCount = rejectedSampleCount,
        resetCount = resetCount,
    )

    private fun rememberBaseline(gameTime: Long, timestampNanos: Long, targetTps: Double) {
        previousGameTime = gameTime
        previousTimestampNanos = timestampNanos
        previousTargetTps = targetTps
    }

    private fun clearMeasurements() {
        samples.clear()
        lastRawTps = null
    }

    private companion object {
        const val DEFAULT_SAMPLE_LIMIT = 5
        const val NANOS_PER_SECOND = 1_000_000_000.0
    }
}

internal enum class TpsSampleResult {
    RESET,
    BASELINE,
    ACCEPTED,
    RESET_TARGET_CHANGED,
    RESET_NON_MONOTONIC_TIME,
    RESET_INVALID_INTERVAL,
    REJECTED_INVALID_TARGET,
    REJECTED_INVALID_SAMPLE,
}

internal data class ServerTpsSnapshot(
    val tps: Double?,
    val samples: List<Double>,
    val previousGameTime: Long?,
    val previousTimestampNanos: Long?,
    val targetTps: Double?,
    val lastGameTimeDelta: Long?,
    val lastElapsedNanos: Long?,
    val lastRawTps: Double?,
    val lastResult: TpsSampleResult,
    val lastRejectedResult: TpsSampleResult?,
    val lastResetResult: TpsSampleResult,
    val acceptedSampleCount: Int,
    val rejectedSampleCount: Int,
    val resetCount: Int,
)
