package com.skysoft.utils

object EasingUtilities {
    fun easeOutCubic(progress: Double): Double {
        val inverse = 1.0 - progress.coerceIn(0.0, 1.0)
        return 1.0 - inverse * inverse * inverse
    }

    fun easeOutCubic(progress: Float): Float = easeOutCubic(progress.toDouble()).toFloat()

    fun smoothStep(progress: Double): Double {
        val clamped = progress.coerceIn(0.0, 1.0)
        return clamped * clamped * (SMOOTHSTEP_QUADRATIC_COEFFICIENT - SMOOTHSTEP_CUBIC_COEFFICIENT * clamped)
    }

    fun smoothStep(progress: Float): Float = smoothStep(progress.toDouble()).toFloat()

    private const val SMOOTHSTEP_QUADRATIC_COEFFICIENT = 3.0
    private const val SMOOTHSTEP_CUBIC_COEFFICIENT = 2.0
}
