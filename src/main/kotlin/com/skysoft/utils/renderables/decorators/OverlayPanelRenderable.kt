package com.skysoft.utils.renderables.decorators

import com.skysoft.utils.gui.OverlayPanelStyle
import com.skysoft.utils.renderables.GuiRenderable
import com.skysoft.utils.renderables.renderAt
import net.minecraft.client.gui.GuiGraphicsExtractor

class OverlayPanelRenderable(private val child: GuiRenderable) : GuiRenderable {
    override val width: Int = child.width + OverlayPanelStyle.PADDING * 2
    override val height: Int = child.height + OverlayPanelStyle.PADDING * 2

    override fun render(context: GuiGraphicsExtractor) {
        OverlayPanelStyle.draw(context, 0, 0, width, height)
        child.renderAt(context, OverlayPanelStyle.PADDING, OverlayPanelStyle.PADDING)
    }
}

fun GuiRenderable.withOverlayPanel(enabled: Boolean = true): GuiRenderable =
    if (enabled) OverlayPanelRenderable(this) else this
