package com.skysoft.mixin

import com.skysoft.features.inventory.RarityHighlightRenderer
import com.skysoft.utils.SkysoftErrorBoundary
import net.minecraft.client.gui.render.GuiItemAtlas
import net.minecraft.client.gui.render.GuiRenderer
import net.minecraft.client.renderer.state.gui.GuiItemRenderState
import net.minecraft.client.renderer.state.gui.GuiRenderState
import org.spongepowered.asm.mixin.Final
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.Shadow
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

@Mixin(GuiRenderer::class)
abstract class GuiRendererMixin {
    @Shadow
    @Final
    private lateinit var renderState: GuiRenderState

    @Inject(method = ["submitBlitFromItemAtlas"], at = [At("TAIL")])
    protected fun skysoftSubmitRarityContour(
        itemState: GuiItemRenderState,
        slotView: GuiItemAtlas.SlotView,
        ci: CallbackInfo,
    ) {
        SkysoftErrorBoundary.run("Rarity Highlight contour rendering") {
            RarityHighlightRenderer.renderContour(renderState, itemState, slotView)
        }
    }
}
