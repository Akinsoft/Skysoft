package com.skysoft.features.misc

import com.skysoft.utils.trimStartToSize
import java.util.ArrayDeque

internal class ServerTpsEstimator {
    private val samples = ArrayDeque<Double>()
    private var baseline: Baseline? = null

    val tps: Double?
        get() = samples.takeIf { it.isNotEmpty() }?.average()

    fun recordTimeUpdate(gameTime: Long, timestampNanos: Long, targetTps: Double) {
        if (!targetTps.isFinite() || targetTps <= 0.0) return
        val previous = baseline
        baseline = Baseline(gameTime, timestampNanos, targetTps)
        if (previous == null) return
        if (previous.targetTps != targetTps) {
            samples.clear()
            return
        }

        val gameTimeDelta = gameTime - previous.gameTime
        val elapsedNanos = timestampNanos - previous.timestampNanos
        val rawTps = gameTimeDelta * NANOS_PER_SECOND / elapsedNanos
        if (gameTimeDelta <= 0L || elapsedNanos <= 0L || !rawTps.isFinite() || rawTps <= 0.0) {
            samples.clear()
            return
        }

        samples.addLast(rawTps.coerceAtMost(targetTps))
        samples.trimStartToSize(SAMPLE_LIMIT)
    }

    fun reset() {
        samples.clear()
        baseline = null
    }

    private data class Baseline(val gameTime: Long, val timestampNanos: Long, val targetTps: Double)

    private companion object {
        const val SAMPLE_LIMIT = 5
        const val NANOS_PER_SECOND = 1_000_000_000.0
    }
}
