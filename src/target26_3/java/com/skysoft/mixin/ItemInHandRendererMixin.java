package com.skysoft.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.skysoft.features.helditem.HeldItemSwingVisuals;
import com.skysoft.features.helditem.HeldItemTransforms;
import com.skysoft.features.helditem.SwingReplacementResult;
import com.skysoft.features.misc.Zoom;
import com.skysoft.utils.mixin.MixinErrorBoundary;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwingAnimationType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FirstPersonHandsAndItemsRenderer.class)
public class ItemInHandRendererMixin {
    @Inject(method = "submitHandsWithItems", at = @At("HEAD"), cancellable = true)
    private void skysoftHideHandsWhileZooming(CallbackInfo ci) {
        if (Zoom.shouldHideHand()) ci.cancel();
    }

    @Inject(
        method = "submitArmWithItem",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/item/ItemStackRenderState;submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;III)V"
        )
    )
    private void skysoftTransformHeldItem(
        PlayerRenderState playerState,
        FirstPersonHandsAndItemsRenderState handsState,
        float frameInterp,
        float xRot,
        InteractionHand hand,
        float attack,
        ItemStack itemStack,
        float inverseArmHeight,
        PoseStack poseStack,
        SubmitNodeCollector submitNodeCollector,
        int light,
        CallbackInfo ci
    ) {
        MixinErrorBoundary.run("Held Item transforms", () -> HeldItemTransforms.apply(itemStack, poseStack));
        MixinErrorBoundary.run("Held Item swing visuals", () -> HeldItemSwingVisuals.apply(itemStack, poseStack));
    }

    @WrapMethod(method = "submitArmWithItem")
    private void skysoftRenderWithHeldItemSwing(
        PlayerRenderState playerState,
        FirstPersonHandsAndItemsRenderState handsState,
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
        AvatarRenderState avatar = playerState.avatarRenderState;
        HumanoidArm arm = hand == InteractionHand.MAIN_HAND ? avatar.mainArm : avatar.mainArm.getOpposite();
        LivingEntity.SwingDescription swing = avatar.currentSwing;
        boolean isWhackSwing = swing != null && swing.hand() == hand && swing.animation().type() == SwingAnimationType.WHACK;
        HeldItemSwingVisuals.renderWithSwing(itemStack, attack, arm, isWhackSwing,
            () -> original.call(playerState, handsState, frameInterp, xRot, hand, attack, itemStack, inverseArmHeight, poseStack, submitNodeCollector, light));
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
