package com.skysoft.mixin;

import com.skysoft.utils.mixin.MixinErrorBoundary;
import com.mojang.blaze3d.vertex.PoseStack;
import com.skysoft.features.helditem.HeldItemSwingVisuals;
import com.skysoft.features.helditem.HeldItemTransforms;
import com.skysoft.features.helditem.HeldItemUpdateFix;
import com.skysoft.features.helditem.SwingReplacementResult;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemInHandRenderer.class)
public class ItemInHandRendererMixin {
    @Inject(method = "shouldInstantlyReplaceVisibleItem", at = @At("RETURN"), cancellable = true)
    protected void skysoftKeepSameUpdatedItemVisible(ItemStack currentlyVisibleItem, ItemStack expectedItem, CallbackInfoReturnable<Boolean> cir) {
        boolean preserve = MixinErrorBoundary.value("Held Item visible item update", false, () -> HeldItemUpdateFix.INSTANCE.shouldPreserveUpdate(currentlyVisibleItem, expectedItem));
        if (!cir.getReturnValue() && preserve) cir.setReturnValue(true);
    }

    @Inject(method = "renderItem", at = @At("HEAD"))
    private void skysoftTransformHeldItem(
        LivingEntity entity,
        ItemStack itemStack,
        ItemDisplayContext displayContext,
        PoseStack poseStack,
        SubmitNodeCollector submitNodeCollector,
        int light,
        CallbackInfo ci
    ) {
        if (displayContext == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND
            || displayContext == ItemDisplayContext.FIRST_PERSON_LEFT_HAND) {
            MixinErrorBoundary.run("Held Item transforms", () -> HeldItemTransforms.apply(itemStack, poseStack));
            MixinErrorBoundary.run("Held Item swing visuals", () -> HeldItemSwingVisuals.apply(itemStack, poseStack));
        }
    }

    @Inject(method = ItemInHandRendererMethodsKt.ITEM_IN_HAND_ARM_METHOD, at = @At("HEAD"))
    private void skysoftBeginHeldItemSwing(
        AbstractClientPlayer player,
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
        HumanoidArm arm = hand == InteractionHand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite();
        MixinErrorBoundary.run("Held Item swing state", () -> HeldItemSwingVisuals.begin(itemStack, attack, arm));
    }

    @Inject(method = ItemInHandRendererMethodsKt.ITEM_IN_HAND_ARM_METHOD, at = @At("TAIL"))
    private void skysoftEndHeldItemSwing(
        AbstractClientPlayer player,
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
        MixinErrorBoundary.run("Held Item swing state", HeldItemSwingVisuals::end);
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
