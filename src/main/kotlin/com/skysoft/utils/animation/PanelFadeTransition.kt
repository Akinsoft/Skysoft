package com.skysoft.utils.animation

import com.skysoft.utils.EasingUtilities

internal class PanelFadeTransition(
    nanoTime: () -> Long = System::nanoTime,
    private val durationNanos: Long = DEFAULT_DURATION_NANOS,
) {
    private var phase = Phase.HIDDEN
    private val clock = AnimationClock(nanoTime)

    val isVisible: Boolean
        get() = phase != Phase.HIDDEN

    val isClosing: Boolean
        get() = phase == Phase.CLOSING

    val isInteractive: Boolean
        get() = phase == Phase.SHOWN

    fun show() {
        phase = Phase.OPENING
        clock.restart()
    }

    fun hide() {
        if (!isVisible || isClosing) return
        phase = Phase.CLOSING
        clock.restart()
    }

    fun reset() {
        phase = Phase.HIDDEN
        clock.stop()
    }

    fun opacity(): Double {
        val progress = clock.progressNanos(durationNanos).toDouble()
        val linearOpacity = when (phase) {
            Phase.HIDDEN -> return 0.0
            Phase.SHOWN -> return 1.0
            Phase.OPENING -> progress
            Phase.CLOSING -> 1.0 - progress
        }
        if (progress >= 1.0) {
            phase = if (phase == Phase.OPENING) Phase.SHOWN else Phase.HIDDEN
        }
        return EasingUtilities.smoothStep(linearOpacity)
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
