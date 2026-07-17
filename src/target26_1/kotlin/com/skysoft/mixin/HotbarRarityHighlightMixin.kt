package com.skysoft.mixin

import com.llamalad7.mixinextras.injector.wrapoperation.Operation
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation
import com.skysoft.features.inventory.RarityHighlightRenderer
import net.minecraft.client.DeltaTracker
import net.minecraft.client.gui.Gui
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

@Mixin(Gui::class)
abstract class HotbarRarityHighlightMixin {
    @Inject(method = ["extractItemHotbar"], at = [At("HEAD")])
    protected fun skysoftBeginHotbarRarityHighlightFrame(
        context: GuiGraphicsExtractor,
        deltaTracker: DeltaTracker,
        ci: CallbackInfo,
    ) {
        RarityHighlightRenderer.beginFrame()
    }

    @Inject(method = ["extractSlot"], at = [At("HEAD")])
    protected fun skysoftRenderHotbarRarityBackground(
        context: GuiGraphicsExtractor,
        x: Int,
        y: Int,
        deltaTracker: DeltaTracker,
        player: Player,
        stack: ItemStack,
        seed: Int,
        ci: CallbackInfo,
    ) {
        RarityHighlightRenderer.renderBackground(context, stack, x, y)
    }

    @WrapOperation(
        method = ["extractSlot"],
        at = [
            At(
                value = "INVOKE",
                target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;" +
                    "item(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;III)V",
            ),
        ],
    )
    protected fun skysoftRenderHotbarRarityContour(
        context: GuiGraphicsExtractor,
        owner: LivingEntity,
        stack: ItemStack,
        x: Int,
        y: Int,
        seed: Int,
        original: Operation<Void>,
    ) {
        RarityHighlightRenderer.renderItem(stack) {
            original.call(context, owner, stack, x, y, seed)
        }
    }
}
