package com.skysoft.utils

import io.github.notenoughupdates.moulconfig.ChromaColour
import java.awt.Color
import kotlin.math.roundToInt

object ColorUtilities {
    const val ARGB_ALPHA_SHIFT = 24
    const val RGB_MASK = 0x00FFFFFF
    const val COLOR_CHANNEL_MIN = 0
    const val COLOR_CHANNEL_MAX = 255

    fun Color.addAlpha(alpha: Int): Color = Color(red, green, blue, alpha)
    fun Color.toChromaColor(alpha: Int = this.alpha): ChromaColour = ChromaColour.fromRGB(red, green, blue, 0, alpha)
    fun ChromaColour.toColor(): Color = getEffectiveColour()
    fun ChromaColour.withOpacity(opacity: Float): ChromaColour {
        val color = toColor()
        return Color(
            color.red,
            color.green,
            color.blue,
            (color.alpha * opacity).roundToInt().coerceIn(COLOR_CHANNEL_MIN, COLOR_CHANNEL_MAX),
        ).toChromaColor()
    }

    fun Color.toPackedArgb(alphaScale: Double): Int {
        val scaledAlpha = (alpha * alphaScale).roundToInt().coerceIn(COLOR_CHANNEL_MIN, COLOR_CHANNEL_MAX)
        return rgb.withAlpha(scaledAlpha)
    }

    fun Int.hasVisibleAlpha(): Boolean =
        (this ushr ARGB_ALPHA_SHIFT) != COLOR_CHANNEL_MIN

    fun Int.withAlpha(alpha: Int): Int =
        (alpha.coerceIn(COLOR_CHANNEL_MIN, COLOR_CHANNEL_MAX) shl ARGB_ALPHA_SHIFT) or (this and RGB_MASK)

    fun Int.withScaledAlpha(alphaScale: Double): Int {
        val scaledAlpha = ((this ushr ARGB_ALPHA_SHIFT) * alphaScale.coerceIn(0.0, 1.0))
            .roundToInt()
            .coerceIn(COLOR_CHANNEL_MIN, COLOR_CHANNEL_MAX)
        return withAlpha(scaledAlpha)
    }
}
