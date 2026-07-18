package com.skysoft.features.chat

import io.github.notenoughupdates.moulconfig.ChromaColour
import net.minecraft.network.chat.Style

internal object ChatNotifyChromaRendering {
    fun apply(style: Style, colour: ChromaColour): Style {
        val highlighted = style.withColor(colour.getEffectiveColourRGB())
        if (colour.timeForFullRotationInMillis > 0) {
            val textColor: Any = highlighted.color ?: return highlighted
            (textColor as? ChromaTextColor)?.skysoftUseChromaColour(colour)
        }
        return highlighted
    }

    fun resolve(fallbackRgb: Int, colour: ChromaColour?): Int =
        colour?.getEffectiveColourRGB()?.and(RGB_MASK) ?: fallbackRgb

    private const val RGB_MASK = 0xFFFFFF
}

interface ChromaTextColor {
    fun skysoftUseChromaColour(colour: ChromaColour)
}
