package com.skysoft.features.screenshot

import com.skysoft.utils.gui.PixelButtonRenderer
import com.skysoft.utils.gui.PixelButtonTone
import com.skysoft.utils.gui.Rect
import com.skysoft.utils.image.RegisteredImageTexture
import com.skysoft.utils.image.drawLinearTexture
import kotlin.math.min
import kotlin.math.roundToInt
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor

internal object ScreenshotRenderStyle {
    val BORDER = 0xFF30373C.toInt()
    val IMAGE_BACKGROUND = 0xFF090B0C.toInt()
    val MUTED_TEXT = 0xFF8D99A1.toInt()
    val ERROR_TEXT = 0xFFFF777D.toInt()

    fun drawButton(
        context: GuiGraphicsExtractor,
        font: Font,
        bounds: Rect,
        label: String,
        isEnabled: Boolean,
        mouseX: Int,
        mouseY: Int,
        tone: PixelButtonTone = PixelButtonTone.NORMAL,
        alpha: Double = 1.0,
        isSelected: Boolean = false,
    ) {
        PixelButtonRenderer.draw(
            context,
            font,
            bounds,
            label,
            isSelected,
            isEnabled && bounds.contains(mouseX, mouseY),
            isEnabled,
            tone,
            alpha,
        )
    }

    fun drawCentered(
        context: GuiGraphicsExtractor,
        font: Font,
        bounds: Rect,
        text: String,
        color: Int,
    ) {
        drawCenteredAtY(context, font, bounds, bounds.y + (bounds.height - font.lineHeight) / 2, text, color)
    }

    fun drawCenteredAtY(
        context: GuiGraphicsExtractor,
        font: Font,
        bounds: Rect,
        y: Int,
        text: String,
        color: Int,
    ) {
        context.text(font, text, bounds.x + (bounds.width - font.width(text)) / 2, y, color, false)
    }

    fun drawLoadState(context: GuiGraphicsExtractor, font: Font, bounds: Rect, didLoadFail: Boolean) {
        if (didLoadFail) {
            drawCentered(context, font, bounds, "Couldn't load screenshot.", ERROR_TEXT)
        } else {
            drawCentered(context, font, bounds, "Loading...", MUTED_TEXT)
        }
    }

    fun drawTextureCover(context: GuiGraphicsExtractor, texture: RegisteredImageTexture, bounds: Rect) {
        val sourceAspect = texture.width.toFloat() / texture.height
        val boundsAspect = bounds.width.toFloat() / bounds.height
        val uInset = if (sourceAspect > boundsAspect) (1f - boundsAspect / sourceAspect) / 2f else 0f
        val vInset = if (sourceAspect < boundsAspect) (1f - sourceAspect / boundsAspect) / 2f else 0f
        context.drawLinearTexture(texture.texture, bounds, uInset, 1f - uInset, vInset, 1f - vInset)
    }

    fun drawTextureContained(context: GuiGraphicsExtractor, texture: RegisteredImageTexture, bounds: Rect) {
        val scale = min(bounds.width.toDouble() / texture.width, bounds.height.toDouble() / texture.height)
        val width = (texture.width * scale).roundToInt().coerceAtLeast(1)
        val height = (texture.height * scale).roundToInt().coerceAtLeast(1)
        context.drawLinearTexture(
            texture.texture,
            Rect(bounds.x + (bounds.width - width) / 2, bounds.y + (bounds.height - height) / 2, width, height),
        )
    }
}
