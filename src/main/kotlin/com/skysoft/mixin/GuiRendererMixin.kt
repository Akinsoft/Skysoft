package com.skysoft.mixin

import com.skysoft.features.inventory.RarityHighlightRenderer
import com.skysoft.gui.scale.GuiScaleController
import com.skysoft.utils.MinecraftClient
import com.skysoft.utils.SkysoftErrorBoundary
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.render.GuiItemAtlas
import net.minecraft.client.gui.render.GuiRenderer
import net.minecraft.client.renderer.state.gui.GuiItemRenderState
import net.minecraft.client.renderer.state.gui.GuiRenderState
import org.spongepowered.asm.mixin.Final
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.Shadow
import org.spongepowered.asm.mixin.Unique
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable

@Mixin(GuiRenderer::class)
abstract class GuiRendererMixin {
    @Shadow
    @Final
    private lateinit var renderState: GuiRenderState

    @Shadow
    private var itemAtlas: GuiItemAtlas? = null

    @Shadow
    private var cachedGuiScale = 0

    @field:Unique
    private val skysoftItemAtlasesByScale = mutableMapOf<Int, GuiItemAtlas>()

    @field:Unique
    private var skysoftIsChangingCachedItemAtlasScale = false

    @Inject(method = ["getGuiScaleInvalidatingItemAtlasIfChanged"], at = [At("HEAD")])
    protected fun skysoftBeginItemAtlasScaleResolution(cir: CallbackInfoReturnable<Int>) {
        val minecraft = Minecraft.getInstance()
        skysoftIsChangingCachedItemAtlasScale = GuiScaleController.hasDistinctInventoryScale(
            MinecraftClient.screen(minecraft),
            minecraft.window,
        )
        if (!skysoftIsChangingCachedItemAtlasScale) skysoftCloseInactiveItemAtlases()
    }

    @Inject(method = ["invalidateItemAtlas"], at = [At("HEAD")], cancellable = true)
    protected fun skysoftPreserveItemAtlasForScale(ci: CallbackInfo) {
        if (!skysoftIsChangingCachedItemAtlasScale) return
        val atlas = itemAtlas ?: return
        check(cachedGuiScale !in skysoftItemAtlasesByScale) {
            "An item atlas is already cached for GUI scale $cachedGuiScale"
        }
        skysoftItemAtlasesByScale[cachedGuiScale] = atlas
        itemAtlas = null
        ci.cancel()
    }

    @Inject(method = ["getGuiScaleInvalidatingItemAtlasIfChanged"], at = [At("RETURN")])
    protected fun skysoftRestoreItemAtlasForScale(cir: CallbackInfoReturnable<Int>) {
        if (skysoftIsChangingCachedItemAtlasScale && itemAtlas == null) {
            itemAtlas = skysoftItemAtlasesByScale.remove(cir.returnValue)
        }
        skysoftIsChangingCachedItemAtlasScale = false
    }

    @Inject(method = ["endFrame"], at = [At("TAIL")])
    protected fun skysoftEndInactiveItemAtlasFrames(ci: CallbackInfo) {
        if (skysoftItemAtlasesByScale.isEmpty()) return
        val minecraft = Minecraft.getInstance()
        if (
            !GuiScaleController.hasDistinctInventoryScale(
                MinecraftClient.screen(minecraft),
                minecraft.window,
            )
        ) {
            skysoftCloseInactiveItemAtlases()
            return
        }
        skysoftItemAtlasesByScale.values.forEach(GuiItemAtlas::endFrame)
    }

    @Inject(method = ["close"], at = [At("HEAD")])
    protected fun skysoftCloseInactiveItemAtlases(ci: CallbackInfo) {
        skysoftCloseInactiveItemAtlases()
    }

    @Unique
    private fun skysoftCloseInactiveItemAtlases() {
        skysoftItemAtlasesByScale.values.forEach(GuiItemAtlas::close)
        skysoftItemAtlasesByScale.clear()
    }

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
