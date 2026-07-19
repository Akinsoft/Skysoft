package com.skysoft.mixin

import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.renderer.state.gui.GuiRenderState
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.Mutable
import org.spongepowered.asm.mixin.gen.Accessor

@Mixin(GuiGraphicsExtractor::class)
interface GuiGraphicsExtractorAccessor {
    @Accessor("guiRenderState")
    fun skysoftGetGuiRenderState(): GuiRenderState

    @Mutable
    @Accessor("guiRenderState")
    fun skysoftSetGuiRenderState(renderState: GuiRenderState)
}
