package com.skysoft.gui

import com.skysoft.config.core.HudPosition
import com.skysoft.utils.gui.Rect
import com.skysoft.utils.renderables.withIsolatedPose
import kotlin.math.roundToInt
import net.minecraft.client.gui.GuiGraphicsExtractor

class HudTransform(val x: Int, val y: Int, val scale: Float) {
    fun localX(screenX: Int): Int = OverlayControlMouse.localCoordinate(screenX, x, scale)

    fun localY(screenY: Int): Int = OverlayControlMouse.localCoordinate(screenY, y, scale)

    fun screenBounds(bounds: Rect): Rect = Rect(
        x = x + (bounds.x * scale).roundToInt(),
        y = y + (bounds.y * scale).roundToInt(),
        width = (bounds.width * scale).roundToInt().coerceAtLeast(1),
        height = (bounds.height * scale).roundToInt().coerceAtLeast(1),
    )

    fun fitsRight(width: Int, extraWidth: Int, screenWidth: Int): Boolean =
        x + ((width + extraWidth) * scale).roundToInt() <= screenWidth

    inline fun <T> render(context: GuiGraphicsExtractor, draw: GuiGraphicsExtractor.() -> T): T =
        context.withIsolatedPose {
            pose().translate(x.toFloat(), y.toFloat())
            pose().scale(scale, scale)
            draw()
        }
}

fun HudPosition.transform(width: Int, height: Int, scale: Float = effectiveScale): HudTransform = HudTransform(
    getAbsX0AllowingOverflow((width * scale).roundToInt()),
    getAbsY0AllowingOverflow((height * scale).roundToInt()),
    scale,
)
