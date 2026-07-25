package com.skysoft.utils.animation

internal class PanelFadeTransition(
    private val nanoTime: () -> Long = System::nanoTime,
    private val durationNanos: Long = DEFAULT_DURATION_NANOS,
) {
    private var phase = Phase.HIDDEN
    private var transitionStartedAt = 0L

    val isVisible: Boolean
        get() = phase != Phase.HIDDEN

    val isClosing: Boolean
        get() = phase == Phase.CLOSING

    val isInteractive: Boolean
        get() = phase == Phase.SHOWN

    fun show() {
        phase = Phase.OPENING
        transitionStartedAt = nanoTime()
    }

    fun hide() {
        if (!isVisible || isClosing) return
        phase = Phase.CLOSING
        transitionStartedAt = nanoTime()
    }

    fun reset() {
        phase = Phase.HIDDEN
        transitionStartedAt = 0L
    }

    fun opacity(): Double {
        val progress = ((nanoTime() - transitionStartedAt).toDouble() / durationNanos).coerceIn(0.0, 1.0)
        val linearOpacity = when (phase) {
            Phase.HIDDEN -> return 0.0
            Phase.SHOWN -> return 1.0
            Phase.OPENING -> progress
            Phase.CLOSING -> 1.0 - progress
        }
        if (progress >= 1.0) {
            phase = if (phase == Phase.OPENING) Phase.SHOWN else Phase.HIDDEN
        }
        return smoothStep(linearOpacity)
    }

    private enum class Phase {
        HIDDEN,
        OPENING,
        SHOWN,
        CLOSING,
    }

    private companion object {
        const val DEFAULT_DURATION_NANOS = 160_000_000L
    }
}

private fun smoothStep(value: Double): Double = value * value * (SMOOTH_STEP_MAX - SMOOTH_STEP_FACTOR * value)

private const val SMOOTH_STEP_MAX = 3.0
private const val SMOOTH_STEP_FACTOR = 2.0
