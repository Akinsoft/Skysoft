package com.skysoft.mixin;

import com.skysoft.utils.mixin.MixinErrorBoundary;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.skysoft.features.inventory.HeadDisplaySize;
import com.skysoft.features.inventory.RarityHighlightRenderer;
import com.skysoft.features.inventory.SlotBindingManager;
import com.skysoft.features.misc.ShortbowCooldownHider;
import com.skysoft.gui.tooltip.AdjacentTooltipRenderer;
import com.skysoft.gui.tooltip.TooltipViewport;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import net.minecraft.client.renderer.state.gui.GuiItemRenderState;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.joml.Matrix3x2f;
import org.joml.Vector2ic;

@Mixin(GuiGraphicsExtractor.class)
public abstract class GuiGraphicsExtractorMixin {
    @ModifyArg(method = "item(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/ItemStack;III)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/state/gui/GuiItemRenderState;<init>(Lorg/joml/Matrix3x2f;Lnet/minecraft/client/renderer/item/TrackingItemStackRenderState;IILnet/minecraft/client/gui/navigation/ScreenRectangle;)V"), index = 0)
    private Matrix3x2f skysoftScaleHeadItem(Matrix3x2f pose, @Local(argsOnly = true) ItemStack stack, @Local(argsOnly = true, ordinal = 0) int x, @Local(argsOnly = true, ordinal = 1) int y) {
        return HeadDisplaySize.scalePose(pose, stack, x, y);
    }

    @WrapOperation(method = "item(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/ItemStack;III)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/state/gui/GuiRenderState;addItem(Lnet/minecraft/client/renderer/state/gui/GuiItemRenderState;)V"))
    protected void skysoftAttachRarityContour(GuiRenderState renderState, GuiItemRenderState itemState, Operation<Void> original) {
        MixinErrorBoundary.run("Rarity Highlight contour attachment", () -> RarityHighlightRenderer.attachContour(itemState));
        original.call(renderState, itemState);
    }

    @Inject(method = "extractDeferredElements", at = @At("HEAD"))
    protected void skysoftQueueSlotBindingTooltipBeforeDeferredTooltips(int mouseX, int mouseY, float delta, CallbackInfo ci) {
        MixinErrorBoundary.run("Slot Binding tooltip rendering", () -> SlotBindingManager.renderTopLayer((GuiGraphicsExtractor) (Object) this));
    }

    @Inject(method = "itemCooldown", at = @At("HEAD"), cancellable = true)
    protected void skysoftHideShortbowCooldown(ItemStack stack, int x, int y, CallbackInfo ci) {
        boolean hide = MixinErrorBoundary.value("Shortbow cooldown overlay visibility", false, () -> ShortbowCooldownHider.shouldHide(stack));
        if (hide) ci.cancel();
    }

    @Inject(method = "tooltip", at = @At("TAIL"))
    protected void skysoftRenderAdjacentTooltip(CallbackInfo ci, @Local(argsOnly = true) Font font) {
        MixinErrorBoundary.run("Adjacent tooltip rendering", () -> AdjacentTooltipRenderer.INSTANCE.renderPending((GuiGraphicsExtractor) (Object) this, font));
    }

    @WrapOperation(method = "tooltip", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/inventory/tooltip/ClientTooltipPositioner;positionTooltip(IIIIII)Lorg/joml/Vector2ic;"))
    protected Vector2ic skysoftPositionScrollableTooltip(ClientTooltipPositioner positioner, int screenWidth, int screenHeight, int x, int y, int tooltipWidth, int tooltipHeight, Operation<Vector2ic> original, @Local(argsOnly = true) List<ClientTooltipComponent> tooltip) {
        ClientTooltipPositioner scrolling = MixinErrorBoundary.value("Scrollable tooltip positioning", positioner, () -> TooltipViewport.decorate(tooltip, x, y, positioner));
        Vector2ic result = original.call(scrolling, screenWidth, screenHeight, x, y, tooltipWidth, tooltipHeight);
        AdjacentTooltipRenderer.INSTANCE.captureMainFrame((GuiGraphicsExtractor) (Object) this, result, tooltipWidth);
        return result;
    }
}
