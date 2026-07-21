package com.skysoft.mixin;

import com.skysoft.utils.mixin.MixinErrorBoundary;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.skysoft.features.inventory.RarityHighlightRenderer;
import com.skysoft.features.inventory.SlotBindingManager;
import com.skysoft.gui.tooltip.AdjacentTooltipRenderer;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import net.minecraft.client.renderer.state.gui.GuiItemRenderState;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiGraphicsExtractor.class)
public abstract class GuiGraphicsExtractorMixin {
    @WrapOperation(method = "item(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/ItemStack;III)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/state/gui/GuiRenderState;addItem(Lnet/minecraft/client/renderer/state/gui/GuiItemRenderState;)V"))
    protected void skysoftAttachRarityContour(GuiRenderState renderState, GuiItemRenderState itemState, Operation<Void> original) {
        MixinErrorBoundary.run("Rarity Highlight contour attachment", () -> RarityHighlightRenderer.attachContour(itemState));
        original.call(renderState, itemState);
    }

    @Inject(method = "extractDeferredElements", at = @At("HEAD"))
    protected void skysoftQueueSlotBindingTooltipBeforeDeferredTooltips(int mouseX, int mouseY, float delta, CallbackInfo ci) {
        MixinErrorBoundary.run("Slot Binding tooltip rendering", () -> SlotBindingManager.renderTopLayer((GuiGraphicsExtractor) (Object) this));
    }

    @Inject(method = "tooltip(Lnet/minecraft/client/gui/Font;Ljava/util/List;IILnet/minecraft/client/gui/screens/inventory/tooltip/ClientTooltipPositioner;Lnet/minecraft/resources/Identifier;)V", at = @At("TAIL"))
    protected void skysoftRenderAdjacentTooltip(Font font, List<ClientTooltipComponent> lines, int x, int y, ClientTooltipPositioner positioner, Identifier style, CallbackInfo ci) {
        MixinErrorBoundary.run("Adjacent tooltip rendering", () -> AdjacentTooltipRenderer.INSTANCE.renderPending((GuiGraphicsExtractor) (Object) this, font));
    }
}
