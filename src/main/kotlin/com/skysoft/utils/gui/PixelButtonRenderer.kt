package com.skysoft.utils.gui

import com.skysoft.utils.ColorUtilities.withScaledAlpha
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor

enum class PixelButtonTone {
    NORMAL,
    DANGER,
    CONFIRM,
}

object PixelButtonRenderer {
    fun draw(
        context: GuiGraphicsExtractor,
        font: Font,
        bounds: Rect,
        label: String,
        selected: Boolean,
        hovered: Boolean,
        enabled: Boolean,
        tone: PixelButtonTone = PixelButtonTone.NORMAL,
        alpha: Double = 1.0,
    ) {
        val palette = when (tone) {
            PixelButtonTone.NORMAL -> PixelButtonColors.NORMAL
            PixelButtonTone.DANGER -> PixelButtonColors.DANGER
            PixelButtonTone.CONFIRM -> PixelButtonColors.CONFIRM
        }
        val fill = when {
            !enabled -> PixelButtonColors.DISABLED
            selected -> palette.selected
            hovered -> palette.hovered
            else -> palette.normal
        }

        drawChamferedLayer(context, bounds, PixelButtonColors.BORDER.withScaledAlpha(alpha))
        val inner = Rect(bounds.x + 1, bounds.y + 1, bounds.width - 2, bounds.height - 2)
        drawChamferedLayer(context, inner, fill.withScaledAlpha(alpha))
        drawBevel(context, inner, selected, enabled, alpha)
        if (selected && enabled) {
            context.fill(
                inner.x + 1,
                inner.y + inner.height - 2,
                inner.x + inner.width - 1,
                inner.y + inner.height - 1,
                palette.accent.withScaledAlpha(alpha),
            )
        }
        val textPosition = labelPosition(bounds, font.width(label), font.lineHeight)
        context.text(
            font,
            label,
            textPosition.x,
            textPosition.y,
            (if (enabled) PixelButtonColors.TEXT else PixelButtonColors.DISABLED_TEXT).withScaledAlpha(alpha),
            false,
        )
    }

    internal fun labelPosition(bounds: Rect, textWidth: Int, lineHeight: Int): Point = Point(
        x = bounds.x + (bounds.width - textWidth + 1) / 2,
        y = bounds.y + (bounds.height - lineHeight + 2) / 2,
    )

    fun drawIcon(
        context: GuiGraphicsExtractor,
        bounds: Rect,
        icon: List<String>,
        scale: Int,
        enabled: Boolean,
    ) {
        val iconWidth = icon.maxOf(String::length) * scale
        val iconHeight = icon.size * scale
        val startX = bounds.x + (bounds.width - iconWidth) / 2
        val startY = bounds.y + (bounds.height - iconHeight) / 2
        for ((row, pixels) in icon.withIndex()) {
            for ((column, pixel) in pixels.withIndex()) {
                if (pixel != ICON_PIXEL) continue
                val x = startX + column * scale
                val y = startY + row * scale
                context.fill(
                    x,
                    y,
                    x + scale,
                    y + scale,
                    if (enabled) PixelButtonColors.TEXT else PixelButtonColors.DISABLED_TEXT,
                )
            }
        }
    }

    private fun drawChamferedLayer(context: GuiGraphicsExtractor, bounds: Rect, color: Int) {
        context.fill(bounds.x + 1, bounds.y, bounds.x + bounds.width - 1, bounds.y + bounds.height, color)
        context.fill(bounds.x, bounds.y + 1, bounds.x + bounds.width, bounds.y + bounds.height - 1, color)
    }

    private fun drawBevel(
        context: GuiGraphicsExtractor,
        bounds: Rect,
        selected: Boolean,
        enabled: Boolean,
        alpha: Double,
    ) {
        if (!enabled) return
        val topLeft = if (selected) PixelButtonColors.INSET else PixelButtonColors.HIGHLIGHT
        val bottomRight = if (selected) PixelButtonColors.HIGHLIGHT else PixelButtonColors.INSET
        context.fill(bounds.x + 1, bounds.y, bounds.x + bounds.width - 1, bounds.y + 1, topLeft.withScaledAlpha(alpha))
        context.fill(bounds.x, bounds.y + 1, bounds.x + 1, bounds.y + bounds.height - 1, topLeft.withScaledAlpha(alpha))
        context.fill(
            bounds.x + 1,
            bounds.y + bounds.height - 1,
            bounds.x + bounds.width - 1,
            bounds.y + bounds.height,
            bottomRight.withScaledAlpha(alpha),
        )
        context.fill(
            bounds.x + bounds.width - 1,
            bounds.y + 1,
            bounds.x + bounds.width,
            bounds.y + bounds.height - 1,
            bottomRight.withScaledAlpha(alpha),
        )
    }

    private const val ICON_PIXEL = 'X'
}

private data class PixelButtonPalette(
    val normal: Int,
    val hovered: Int,
    val selected: Int,
    val accent: Int,
)

private object PixelButtonColors {
    val NORMAL = PixelButtonPalette(
        normal = 0xFF3B4147.toInt(),
        hovered = 0xFF4B5963.toInt(),
        selected = 0xFF286B98.toInt(),
        accent = 0xFF65C2FF.toInt(),
    )
    val DANGER = PixelButtonPalette(
        normal = 0xFF493638.toInt(),
        hovered = 0xFF6C3C40.toInt(),
        selected = 0xFF7A343A.toInt(),
        accent = 0xFFFF7379.toInt(),
    )
    val CONFIRM = PixelButtonPalette(
        normal = 0xFF35483B.toInt(),
        hovered = 0xFF45634D.toInt(),
        selected = 0xFF3D6B49.toInt(),
        accent = 0xFF70D98A.toInt(),
    )
    val BORDER = 0xFF111315.toInt()
    val DISABLED = 0xFF24272A.toInt()
    val HIGHLIGHT = 0xFF6F7880.toInt()
    val INSET = 0xFF1D2226.toInt()
    val TEXT = 0xFFFFFFFF.toInt()
    val DISABLED_TEXT = 0xFF606870.toInt()
}
