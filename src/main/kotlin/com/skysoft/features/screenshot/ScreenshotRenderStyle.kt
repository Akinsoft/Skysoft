package com.skysoft.features.screenshot

import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.textures.FilterMode
import com.skysoft.utils.gui.PixelButtonRenderer
import com.skysoft.utils.gui.PixelButtonTone
import com.skysoft.utils.gui.Rect
import com.skysoft.utils.image.RegisteredImageTexture
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
        context.text(
            font,
            text,
            bounds.x + (bounds.width - font.width(text)) / 2,
            bounds.y + (bounds.height - font.lineHeight) / 2,
            color,
            false,
        )
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

    fun drawTextureCover(context: GuiGraphicsExtractor, texture: RegisteredImageTexture, bounds: Rect) {
        val sourceAspect = texture.width.toFloat() / texture.height
        val boundsAspect = bounds.width.toFloat() / bounds.height
        val uInset = if (sourceAspect > boundsAspect) (1f - boundsAspect / sourceAspect) / 2f else 0f
        val vInset = if (sourceAspect < boundsAspect) (1f - sourceAspect / boundsAspect) / 2f else 0f
        context.blit(
            texture.texture.textureView,
            RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR),
            bounds.x,
            bounds.y,
            bounds.x + bounds.width,
            bounds.y + bounds.height,
            uInset,
            1f - uInset,
            vInset,
            1f - vInset,
        )
    }

    fun drawTextureContained(context: GuiGraphicsExtractor, texture: RegisteredImageTexture, bounds: Rect) {
        val scale = min(bounds.width.toDouble() / texture.width, bounds.height.toDouble() / texture.height)
        val width = (texture.width * scale).roundToInt().coerceAtLeast(1)
        val height = (texture.height * scale).roundToInt().coerceAtLeast(1)
        val x = bounds.x + (bounds.width - width) / 2
        val y = bounds.y + (bounds.height - height) / 2
        context.blit(
            texture.texture.textureView,
            RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR),
            x,
            y,
            x + width,
            y + height,
            0f,
            1f,
            0f,
            1f,
        )
    }
}
