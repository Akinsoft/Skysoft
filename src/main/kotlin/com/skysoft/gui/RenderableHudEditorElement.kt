package com.skysoft.gui

import com.skysoft.config.SkysoftConfigGui
import com.skysoft.config.core.HudPosition
import com.skysoft.utils.renderables.GuiRenderable
import com.skysoft.utils.renderables.renderRenderable
import net.minecraft.client.gui.GuiGraphicsExtractor

interface RenderableHudEditorElement : HudEditorElement {
    fun hudRenderable(): GuiRenderable?

    override fun width(): Int = hudRenderable()?.width ?: 0

    override fun height(): Int = hudRenderable()?.height ?: 0

    override fun renderEditor(context: GuiGraphicsExtractor) {
        hudRenderable()?.render(context)
    }
}

fun registerRenderableHud(
    id: String,
    label: String,
    position: () -> HudPosition,
    contexts: Set<GuiOverlayContextType> = TabDataOverlays.contexts,
    visible: (GuiOverlayContext) -> Boolean = { true },
    hasEditorBackground: () -> Boolean = { true },
    renderable: () -> GuiRenderable?,
) {
    GuiOverlayRegistry.registerHud(
        GuiOverlay(
            id = id,
            layer = GuiOverlayLayer.BELOW_SCREEN,
            contexts = contexts,
            visible = visible,
            render = { context, _ -> renderable()?.let { position().renderRenderable(context, it) } },
        ),
        RenderableHud(id, label, position, hasEditorBackground, renderable),
    )
}

private class RenderableHud(
    override val id: String,
    override val label: String,
    private val currentPosition: () -> HudPosition,
    private val showsEditorBackground: () -> Boolean,
    private val renderable: () -> GuiRenderable?,
) : RenderableHudEditorElement {
    override val position get() = currentPosition()
    override val hasEditorBackground get() = showsEditorBackground()
    override fun hudRenderable() = renderable()
    override fun isVisible() = renderable() != null
    override fun openConfig() = SkysoftConfigGui.open(label)
}
