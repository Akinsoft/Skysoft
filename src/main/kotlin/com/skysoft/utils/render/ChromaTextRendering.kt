package com.skysoft.utils.render

import com.skysoft.utils.ColorUtilities.RGB_MASK
import io.github.notenoughupdates.moulconfig.ChromaColour
import net.minecraft.network.chat.Style

object ChromaTextRendering {
    fun apply(style: Style, colour: ChromaColour): Style {
        if (colour.timeForFullRotationInMillis <= 0) return style.withColor(colour.getEffectiveColourRGB())
        val baseRgb = (style.color?.value ?: colour.getEffectiveColourRGB()).and(RGB_MASK)
        val highlighted = style.withColor(baseRgb xor 1)
        val textColor: Any = highlighted.color ?: return highlighted
        (textColor as? ChromaTextColor)?.skysoftUseChromaColour(colour, baseRgb)
        return highlighted
    }

    fun resolveGlyph(
        fallbackRgb: Int,
        colour: ChromaColour?,
        x: Float,
        y: Float,
        screenWidth: Int,
    ): Int = colour
        ?.getEffectiveColourRGB((x - y) / (screenWidth.coerceAtLeast(1) * CHROMA_SIZE))
        ?.and(RGB_MASK)
        ?: fallbackRgb

    private const val CHROMA_SIZE = 0.3f
}

interface ChromaTextColor {
    fun skysoftUseChromaColour(colour: ChromaColour, baseRgb: Int)
    fun skysoftChromaColour(): ChromaColour?
}
