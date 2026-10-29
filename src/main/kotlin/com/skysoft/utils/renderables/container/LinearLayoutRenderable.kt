package com.skysoft.utils.renderables.container

import com.skysoft.utils.gui.GuiAlignment
import com.skysoft.utils.renderables.GuiRenderable
import com.skysoft.utils.renderables.horizontalOffset
import com.skysoft.utils.renderables.renderAt
import com.skysoft.utils.renderables.verticalOffset
import net.minecraft.client.gui.GuiGraphicsExtractor

fun horizontalLayout(
    children: List<GuiRenderable>,
    spacing: Int = 0,
    horizontalAlign: GuiAlignment.HorizontalAlignment = GuiAlignment.HorizontalAlignment.LEFT,
    verticalAlign: GuiAlignment.VerticalAlignment = GuiAlignment.VerticalAlignment.TOP,
): GuiRenderable = LinearLayoutRenderable(children, isHorizontal = true, spacing, horizontalAlign, verticalAlign)

fun verticalLayout(
    children: List<GuiRenderable>,
    spacing: Int = 0,
    horizontalAlign: GuiAlignment.HorizontalAlignment = GuiAlignment.HorizontalAlignment.LEFT,
    verticalAlign: GuiAlignment.VerticalAlignment = GuiAlignment.VerticalAlignment.TOP,
): GuiRenderable = LinearLayoutRenderable(children, isHorizontal = false, spacing, horizontalAlign, verticalAlign)

private class LinearLayoutRenderable(
    private val children: List<GuiRenderable>,
    private val isHorizontal: Boolean,
    private val spacing: Int,
    override val horizontalAlign: GuiAlignment.HorizontalAlignment,
    override val verticalAlign: GuiAlignment.VerticalAlignment,
) : GuiRenderable {
    override val width: Int = if (isHorizontal) totalLength(GuiRenderable::width) else children.maxOfOrNull(GuiRenderable::width) ?: 0
    override val height: Int = if (isHorizontal) children.maxOfOrNull(GuiRenderable::height) ?: 0 else totalLength(GuiRenderable::height)

    override fun render(context: GuiGraphicsExtractor) {
        var cursor = 0
        for (child in children) {
            if (isHorizontal) {
                child.renderAt(context, cursor, child.verticalOffset(height))
                cursor += child.width + spacing
            } else {
                child.renderAt(context, child.horizontalOffset(width), cursor)
                cursor += child.height + spacing
            }
        }
    }

    private fun totalLength(size: (GuiRenderable) -> Int): Int =
        children.sumOf(size) + spacing * (children.size - 1).coerceAtLeast(0)
}
