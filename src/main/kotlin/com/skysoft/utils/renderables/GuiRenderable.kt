package com.skysoft.utils.renderables

import com.skysoft.config.core.HudPosition
import com.skysoft.utils.gui.GuiAlignment
import net.minecraft.client.gui.GuiGraphicsExtractor
import kotlin.math.roundToInt

interface GuiRenderable {
    val width: Int
    val height: Int
    val horizontalAlign: GuiAlignment.HorizontalAlignment get() = GuiAlignment.HorizontalAlignment.LEFT
    val verticalAlign: GuiAlignment.VerticalAlignment get() = GuiAlignment.VerticalAlignment.TOP

    fun render(context: GuiGraphicsExtractor)
}

fun GuiRenderable.renderAt(context: GuiGraphicsExtractor, x: Int, y: Int) {
    renderAt(context, x.toFloat(), y.toFloat())
}

fun GuiRenderable.renderAt(context: GuiGraphicsExtractor, x: Float, y: Float) {
    context.withIsolatedPose {
        pose().translate(x, y)
        render(context)
    }
}

fun HudPosition.renderRenderable(context: GuiGraphicsExtractor, renderable: GuiRenderable) {
    val scaledWidth = (renderable.width * effectiveScale).roundToInt()
    val scaledHeight = (renderable.height * effectiveScale).roundToInt()
    val x = getAbsX0AllowingOverflow(scaledWidth)
    val y = getAbsY0AllowingOverflow(scaledHeight)
    context.withIsolatedPose {
        pose().translate(x.toFloat(), y.toFloat())
        pose().scale(effectiveScale, effectiveScale)
        renderable.render(context)
    }
}

inline fun <T> GuiGraphicsExtractor.withIsolatedPose(block: GuiGraphicsExtractor.() -> T): T {
    pose().pushMatrix()
    return try {
        block()
    } finally {
        pose().popMatrix()
    }
}

class AnchoredRenderable(
    val renderable: GuiRenderable,
    val anchorX: Int,
    val anchorY: Int,
    val anchorWidth: Int,
    val anchorHeight: Int,
)

fun GuiRenderable.anchorToSelf(): AnchoredRenderable =
    AnchoredRenderable(this, 0, 0, width, height)

fun GuiRenderable.horizontalOffset(containerWidth: Int): Int = when (horizontalAlign) {
    GuiAlignment.HorizontalAlignment.LEFT, GuiAlignment.HorizontalAlignment.DONT_ALIGN -> 0
    GuiAlignment.HorizontalAlignment.CENTER -> (containerWidth - width) / 2
    GuiAlignment.HorizontalAlignment.RIGHT -> containerWidth - width
}

fun GuiRenderable.verticalOffset(containerHeight: Int): Int = when (verticalAlign) {
    GuiAlignment.VerticalAlignment.TOP, GuiAlignment.VerticalAlignment.DONT_ALIGN -> 0
    GuiAlignment.VerticalAlignment.CENTER -> (containerHeight - height) / 2
    GuiAlignment.VerticalAlignment.BOTTOM -> containerHeight - height
}
