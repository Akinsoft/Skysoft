package com.skysoft.utils.render

import com.skysoft.mixin.GuiGraphicsExtractorAccessor
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.renderer.state.gui.GuiRenderState

internal object GuiRenderStateAccess {
    fun get(context: GuiGraphicsExtractor): GuiRenderState =
        (context as GuiGraphicsExtractorAccessor).skysoftGetGuiRenderState()
}
