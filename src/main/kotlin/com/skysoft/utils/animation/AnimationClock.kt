package com.skysoft.utils.animation

import com.skysoft.utils.NANOS_PER_MILLISECOND

class AnimationClock {
    private var startedAtNanos = Long.MIN_VALUE

    fun restart() {
        startedAtNanos = System.nanoTime()
    }

    fun stop() {
        startedAtNanos = Long.MIN_VALUE
    }

    fun hasStarted(): Boolean = startedAtNanos != Long.MIN_VALUE

    fun progress(durationMillis: Int): Float =
        progressNanos(durationMillis.toLong() * NANOS_PER_MILLISECOND)

    fun progressNanos(durationNanos: Long): Float {
        if (!hasStarted() || durationNanos <= 0L) return 1.0f
        val elapsedNanos = (System.nanoTime() - startedAtNanos).coerceAtLeast(0L)
        return (elapsedNanos / durationNanos.toFloat()).coerceAtMost(1.0f)
    }

    fun isCompleteNanos(durationNanos: Long): Boolean = progressNanos(durationNanos) >= 1.0f
}
