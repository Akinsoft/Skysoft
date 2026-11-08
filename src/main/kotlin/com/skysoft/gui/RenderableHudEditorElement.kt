package com.skysoft.gui

import com.skysoft.utils.renderables.GuiRenderable
import net.minecraft.client.gui.GuiGraphicsExtractor

interface RenderableHudEditorElement : HudEditorElement {
    fun hudRenderable(): GuiRenderable?

    override fun width(): Int = hudRenderable()?.width ?: 0

    override fun height(): Int = hudRenderable()?.height ?: 0

    override fun renderEditor(context: GuiGraphicsExtractor) {
        hudRenderable()?.render(context)
    }
}
