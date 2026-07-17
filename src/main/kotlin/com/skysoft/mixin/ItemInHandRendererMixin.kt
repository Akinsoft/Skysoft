package com.skysoft.mixin

import com.mojang.blaze3d.vertex.PoseStack
import com.skysoft.features.helditem.HeldItemSwingVisuals
import com.skysoft.features.helditem.HeldItemTransforms
import com.skysoft.features.helditem.HeldItemUpdateFix
import com.skysoft.features.helditem.SwingReplacementResult
import net.minecraft.client.player.AbstractClientPlayer
import net.minecraft.client.renderer.ItemInHandRenderer
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.HumanoidArm
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.item.ItemDisplayContext
import net.minecraft.world.item.ItemStack
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable

@Mixin(ItemInHandRenderer::class)
open class ItemInHandRendererMixin {
    @Inject(method = ["shouldInstantlyReplaceVisibleItem"], at = [At("RETURN")], cancellable = true)
    protected fun skysoftKeepSameUpdatedItemVisible(
        currentlyVisibleItem: ItemStack,
        expectedItem: ItemStack,
        cir: CallbackInfoReturnable<Boolean>,
    ) {
        if (!cir.returnValue && HeldItemUpdateFix.shouldPreserveUpdate(currentlyVisibleItem, expectedItem)) {
            cir.returnValue = true
        }
    }

    @Inject(method = ["renderItem"], at = [At("HEAD")])
    protected fun skysoftTransformHeldItem(
        entity: LivingEntity,
        itemStack: ItemStack,
        displayContext: ItemDisplayContext,
        poseStack: PoseStack,
        submitNodeCollector: SubmitNodeCollector,
        light: Int,
        ci: CallbackInfo,
    ) {
        if (
            displayContext == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND ||
            displayContext == ItemDisplayContext.FIRST_PERSON_LEFT_HAND
        ) {
            HeldItemTransforms.apply(itemStack, poseStack)
            HeldItemSwingVisuals.apply(itemStack, poseStack)
        }
    }

    @Inject(method = [ITEM_IN_HAND_ARM_METHOD], at = [At("HEAD")])
    protected fun skysoftBeginHeldItemSwing(
        player: AbstractClientPlayer,
        frameInterp: Float,
        xRot: Float,
        hand: InteractionHand,
        attack: Float,
        itemStack: ItemStack,
        inverseArmHeight: Float,
        poseStack: PoseStack,
        submitNodeCollector: SubmitNodeCollector,
        light: Int,
        ci: CallbackInfo,
    ) {
        val arm = if (hand == InteractionHand.MAIN_HAND) player.mainArm else player.mainArm.opposite
        HeldItemSwingVisuals.begin(itemStack, attack, arm)
    }

    @Inject(method = [ITEM_IN_HAND_ARM_METHOD], at = [At("TAIL")])
    protected fun skysoftEndHeldItemSwing(
        player: AbstractClientPlayer,
        frameInterp: Float,
        xRot: Float,
        hand: InteractionHand,
        attack: Float,
        itemStack: ItemStack,
        inverseArmHeight: Float,
        poseStack: PoseStack,
        submitNodeCollector: SubmitNodeCollector,
        light: Int,
        ci: CallbackInfo,
    ) {
        HeldItemSwingVisuals.end()
    }

    @Inject(method = ["swingArm"], at = [At("HEAD")], cancellable = true)
    protected fun skysoftReplaceHeldItemSwing(
        attack: Float,
        poseStack: PoseStack,
        invert: Int,
        arm: HumanoidArm,
        ci: CallbackInfo,
    ) {
        if (HeldItemSwingVisuals.replaceVanillaSwing() == SwingReplacementResult.REPLACED) {
            ci.cancel()
        }
    }
}
