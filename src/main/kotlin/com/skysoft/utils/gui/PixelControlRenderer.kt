package com.skysoft.utils.gui

import com.skysoft.utils.ColorUtilities.withScaledAlpha
import kotlin.math.roundToInt
import net.minecraft.client.gui.GuiGraphicsExtractor

object PixelControlColors {
    val HEADER = 0x801B2530.toInt()
    val TEXT = 0xFFFFFFFF.toInt()
    val MUTED_TEXT = 0xFF9AA4AE.toInt()
    val SLIDER_TRACK = 0xFF30363B.toInt()
    val SLIDER_KNOB = 0xFFB9C2CA.toInt()
    val ACCENT = 0xFF45A3FF.toInt()
}

object PixelControlPanelRenderer {
    fun draw(context: GuiGraphicsExtractor, bounds: Rect, headerHeight: Int) {
        OverlayPanelStyle.draw(context, bounds.x, bounds.y, bounds.width, bounds.height)
        context.fill(
            bounds.x + BORDER,
            bounds.y + BORDER,
            bounds.x + bounds.width - BORDER,
            bounds.y + headerHeight,
            PixelControlColors.HEADER,
        )
    }

    private const val BORDER = 1
}

object PixelSliderRenderer {
    fun valueAt(pointerX: Int, track: Rect, range: IntRange, step: Int): Int {
        if (range.first >= range.last) return range.first
        val progress = ((pointerX - track.x).toDouble() / track.width.coerceAtLeast(1)).coerceIn(0.0, 1.0)
        val raw = range.first + (range.last - range.first) * progress
        return (raw / step).roundToInt().times(step).coerceIn(range)
    }

    fun draw(context: GuiGraphicsExtractor, track: Rect, progress: Float, isHovered: Boolean, opacity: Double = 1.0) {
        val fillWidth = (track.width * progress.coerceIn(0f, 1f)).roundToInt()
        context.fill(
            track.x,
            track.y,
            track.x + track.width,
            track.y + track.height,
            PixelControlColors.SLIDER_TRACK.withScaledAlpha(opacity),
        )
        context.fill(
            track.x,
            track.y,
            track.x + fillWidth,
            track.y + track.height,
            PixelControlColors.ACCENT.withScaledAlpha(opacity),
        )
        val knobX = (track.x + fillWidth).coerceIn(track.x, track.x + track.width)
        context.fill(
            knobX - KNOB_HALF_WIDTH,
            track.y - KNOB_OVERHANG,
            knobX + KNOB_HALF_WIDTH,
            track.y + track.height + KNOB_OVERHANG,
            (if (isHovered) PixelControlColors.TEXT else PixelControlColors.SLIDER_KNOB).withScaledAlpha(opacity),
        )
    }

    private const val KNOB_HALF_WIDTH = 2
    private const val KNOB_OVERHANG = 2
}
