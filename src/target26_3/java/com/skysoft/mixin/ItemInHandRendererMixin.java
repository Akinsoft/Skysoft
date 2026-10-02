package com.skysoft.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.skysoft.utils.mixin.MixinErrorBoundary;
import com.mojang.blaze3d.vertex.PoseStack;
import com.skysoft.features.helditem.HeldItemSwingVisuals;
import com.skysoft.features.helditem.HeldItemTransforms;
import com.skysoft.features.helditem.SwingReplacementResult;
import com.skysoft.features.misc.Zoom;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FirstPersonHandsAndItemsRenderer.class)
public class ItemInHandRendererMixin {
    @Inject(method = ItemInHandRendererMethodsKt.ITEM_IN_HAND_HANDS_METHOD, at = @At("HEAD"), cancellable = true)
    private void skysoftHideHandsWhileZooming(CallbackInfo ci) {
        if (Zoom.shouldHideHand()) ci.cancel();
    }

    @WrapOperation(method = ItemInHandRendererMethodsKt.ITEM_IN_HAND_ARM_METHOD,
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/item/ItemStackRenderState;submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;III)V"))
    private void skysoftTransformHeldItem(
        ItemStackRenderState renderState, PoseStack poseStack, SubmitNodeCollector collector,
        int light, int overlay, int outline, Operation<Void> original,
        @Local(argsOnly = true) ItemStack itemStack
    ) {
        MixinErrorBoundary.run("Held Item transforms", () -> HeldItemTransforms.apply(itemStack, poseStack));
        MixinErrorBoundary.run("Held Item swing visuals", () -> HeldItemSwingVisuals.apply(itemStack, poseStack));
        original.call(renderState, poseStack, collector, light, overlay, outline);
    }

    @WrapMethod(method = ItemInHandRendererMethodsKt.ITEM_IN_HAND_ARM_METHOD)
    private void skysoftRenderWithHeldItemSwing(
        PlayerRenderState player,
        FirstPersonHandsAndItemsRenderState handState,
        float frameInterp,
        float xRot,
        InteractionHand hand,
        float attack,
        ItemStack itemStack,
        float inverseArmHeight,
        PoseStack poseStack,
        SubmitNodeCollector submitNodeCollector,
        int light,
        Operation<Void> original
    ) {
        if (player.avatarRenderState == null) {
            original.call(player, handState, frameInterp, xRot, hand, attack, itemStack, inverseArmHeight, poseStack, submitNodeCollector, light);
            return;
        }
        HumanoidArm arm = hand == InteractionHand.MAIN_HAND ? player.avatarRenderState.mainArm : player.avatarRenderState.mainArm.getOpposite();
        HeldItemSwingVisuals.renderWithSwing(itemStack, attack, arm,
            () -> original.call(player, handState, frameInterp, xRot, hand, attack, itemStack, inverseArmHeight, poseStack, submitNodeCollector, light));
    }

    @Inject(method = "swingArm", at = @At("HEAD"), cancellable = true)
    private void skysoftReplaceHeldItemSwing(
        float attack,
        PoseStack poseStack,
        int invert,
        HumanoidArm arm,
        CallbackInfo ci
    ) {
        boolean replaced = MixinErrorBoundary.value("Held Item swing replacement", false, () -> HeldItemSwingVisuals.replaceVanillaSwing() == SwingReplacementResult.REPLACED);
        if (replaced) ci.cancel();
    }
}
