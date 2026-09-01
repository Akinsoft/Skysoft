package com.skysoft.utils.animation

import java.util.function.LongSupplier

class AnimationClock internal constructor(
    private val nanoTime: LongSupplier,
) {
    constructor() : this(LongSupplier(System::nanoTime))
    internal constructor(nanoTime: () -> Long) : this(LongSupplier(nanoTime))

    private var startedAtNanos = Long.MIN_VALUE

    fun restart() {
        startedAtNanos = nanoTime.getAsLong()
    }

    fun stop() {
        startedAtNanos = Long.MIN_VALUE
    }

    fun hasStarted(): Boolean = startedAtNanos != Long.MIN_VALUE

    fun elapsedNanos(): Long =
        if (hasStarted()) (nanoTime.getAsLong() - startedAtNanos).coerceAtLeast(0L) else Long.MAX_VALUE

    fun progress(durationMillis: Int): Float =
        progressNanos(durationMillis.toLong() * NANOS_PER_MILLISECOND)

    fun progressNanos(durationNanos: Long): Float {
        if (!hasStarted() || durationNanos <= 0L) return 1.0f
        return (elapsedNanos() / durationNanos.toFloat()).coerceAtMost(1.0f)
    }

    fun isCompleteNanos(durationNanos: Long): Boolean = progressNanos(durationNanos) >= 1.0f

    private companion object {
        const val NANOS_PER_MILLISECOND = 1_000_000L
    }
}
