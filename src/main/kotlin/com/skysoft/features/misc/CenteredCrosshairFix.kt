package com.skysoft.features.misc

import com.skysoft.config.SkysoftConfigGui
import com.skysoft.utils.renderables.withIsolatedPose
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
        val offset = centeredCrosshairOffset(vanillaX, vanillaY, width, height)
        if (offset.x == 0f && offset.y == 0f) {
            drawVanillaCrosshair()
            return
        }

        graphics.withIsolatedPose {
            graphics.pose().translate(offset.x, offset.y)
            drawVanillaCrosshair()
        }
    }
}

internal fun centeredCrosshairOffset(vanillaX: Int, vanillaY: Int, spriteWidth: Int, spriteHeight: Int): CrosshairOffset {
    val window = Minecraft.getInstance().window
    val guiScale = window.guiScale.toDouble()
    require(guiScale > 0.0) { "GUI scale must be positive" }
    val centeredX = (window.width / guiScale - spriteWidth) / 2.0
    val centeredY = (window.height / guiScale - spriteHeight) / 2.0
    return CrosshairOffset(
        x = (centeredX - vanillaX).toFloat(),
        y = (centeredY - vanillaY).toFloat(),
    )
}

internal data class CrosshairOffset(val x: Float, val y: Float)
