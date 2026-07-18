package com.skysoft.features.misc

import com.skysoft.config.SkysoftConfigGui
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor

object CenteredCrosshairFix {
    @JvmStatic
    fun isEnabled(): Boolean = SkysoftConfigGui.config().fixes.isCenteredCrosshairFixEnabled

    @JvmStatic
    fun renderCentered(
        graphics: GuiGraphicsExtractor,
        vanillaX: Int,
        vanillaY: Int,
        width: Int,
        height: Int,
        drawVanillaCrosshair: () -> Unit,
    ) {
        val window = Minecraft.getInstance().window
        val offset = centeredCrosshairOffset(
            framebufferWidth = window.width,
            framebufferHeight = window.height,
            guiScale = window.guiScale.toDouble(),
            vanillaX = vanillaX,
            vanillaY = vanillaY,
            spriteWidth = width,
            spriteHeight = height,
        )
        if (offset.x == 0f && offset.y == 0f) {
            drawVanillaCrosshair()
            return
        }

        graphics.pose().pushMatrix()
        try {
            graphics.pose().translate(offset.x, offset.y)
            drawVanillaCrosshair()
        } finally {
            graphics.pose().popMatrix()
        }
    }
}

internal fun centeredCrosshairOffset(
    framebufferWidth: Int,
    framebufferHeight: Int,
    guiScale: Double,
    vanillaX: Int,
    vanillaY: Int,
    spriteWidth: Int,
    spriteHeight: Int,
): CrosshairOffset {
    require(guiScale > 0.0) { "GUI scale must be positive" }
    val centeredX = (framebufferWidth / guiScale - spriteWidth) / 2.0
    val centeredY = (framebufferHeight / guiScale - spriteHeight) / 2.0
    return CrosshairOffset(
        x = (centeredX - vanillaX).toFloat(),
        y = (centeredY - vanillaY).toFloat(),
    )
}

internal data class CrosshairOffset(val x: Float, val y: Float)
