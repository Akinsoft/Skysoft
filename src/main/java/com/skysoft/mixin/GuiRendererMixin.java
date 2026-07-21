package com.skysoft.mixin;

import com.skysoft.utils.mixin.MixinErrorBoundary;
import com.skysoft.features.inventory.RarityHighlightRenderer;
import com.skysoft.gui.scale.GuiScaleController;
import com.skysoft.utils.MinecraftClient;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.render.GuiItemAtlas;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.renderer.state.gui.GuiItemRenderState;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GuiRenderer.class)
public abstract class GuiRendererMixin {
    @Shadow @Final private GuiRenderState renderState;
    @Shadow private GuiItemAtlas itemAtlas;
    @Shadow private int cachedGuiScale;
    @Unique private final Map<Integer, GuiItemAtlas> skysoftItemAtlasesByScale = new HashMap<>();
    @Unique private boolean skysoftIsChangingCachedItemAtlasScale;

    @Inject(method = "getGuiScaleInvalidatingItemAtlasIfChanged", at = @At("HEAD"))
    protected void skysoftBeginItemAtlasScaleResolution(CallbackInfoReturnable<Integer> cir) {
        Minecraft minecraft = Minecraft.getInstance();
        skysoftIsChangingCachedItemAtlasScale = GuiScaleController.hasDistinctInventoryScale(MinecraftClient.INSTANCE.screen(minecraft), minecraft.getWindow());
        if (!skysoftIsChangingCachedItemAtlasScale) skysoftCloseInactiveItemAtlases();
    }

    @Inject(method = "invalidateItemAtlas", at = @At("HEAD"), cancellable = true)
    protected void skysoftPreserveItemAtlasForScale(CallbackInfo ci) {
        if (!skysoftIsChangingCachedItemAtlasScale || itemAtlas == null) return;
        if (skysoftItemAtlasesByScale.containsKey(cachedGuiScale)) throw new IllegalStateException("An item atlas is already cached for GUI scale " + cachedGuiScale);
        skysoftItemAtlasesByScale.put(cachedGuiScale, itemAtlas);
        itemAtlas = null;
        ci.cancel();
    }

    @Inject(method = "getGuiScaleInvalidatingItemAtlasIfChanged", at = @At("RETURN"))
    protected void skysoftRestoreItemAtlasForScale(CallbackInfoReturnable<Integer> cir) {
        if (skysoftIsChangingCachedItemAtlasScale && itemAtlas == null) itemAtlas = skysoftItemAtlasesByScale.remove(cir.getReturnValue());
        skysoftIsChangingCachedItemAtlasScale = false;
    }

    @Inject(method = "endFrame", at = @At("TAIL"))
    protected void skysoftEndInactiveItemAtlasFrames(CallbackInfo ci) {
        if (skysoftItemAtlasesByScale.isEmpty()) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (!GuiScaleController.hasDistinctInventoryScale(MinecraftClient.INSTANCE.screen(minecraft), minecraft.getWindow())) {
            skysoftCloseInactiveItemAtlases();
            return;
        }
        skysoftItemAtlasesByScale.values().forEach(GuiItemAtlas::endFrame);
    }

    @Inject(method = "close", at = @At("HEAD"))
    protected void skysoftCloseInactiveItemAtlases(CallbackInfo ci) { skysoftCloseInactiveItemAtlases(); }

    @Unique
    private void skysoftCloseInactiveItemAtlases() {
        skysoftItemAtlasesByScale.values().forEach(GuiItemAtlas::close);
        skysoftItemAtlasesByScale.clear();
    }

    @Inject(method = "submitBlitFromItemAtlas", at = @At("TAIL"))
    protected void skysoftSubmitRarityContour(GuiItemRenderState itemState, GuiItemAtlas.SlotView slotView, CallbackInfo ci) {
        MixinErrorBoundary.run("Rarity Highlight contour rendering", () -> RarityHighlightRenderer.renderContour(renderState, itemState, slotView));
    }
}
