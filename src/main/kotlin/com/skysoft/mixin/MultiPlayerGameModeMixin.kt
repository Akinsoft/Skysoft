package com.skysoft.mixin

import com.llamalad7.mixinextras.injector.wrapoperation.Operation
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation
import com.skysoft.features.helditem.HeldItemUpdateFix
import net.minecraft.client.multiplayer.MultiPlayerGameMode
import net.minecraft.world.item.ItemStack
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At

@Mixin(MultiPlayerGameMode::class)
open class MultiPlayerGameModeMixin {
    @WrapOperation(
        method = ["sameDestroyTarget"],
        at = [
            At(
                value = "INVOKE",
                target = "Lnet/minecraft/world/item/ItemStack;" +
                    "isSameItemSameComponents(Lnet/minecraft/world/item/ItemStack;" +
                    "Lnet/minecraft/world/item/ItemStack;)Z",
            ),
        ],
    )
    protected fun isSkysoftSameDestroyTargetAfterItemUpdate(
        current: ItemStack,
        previous: ItemStack,
        original: Operation<Boolean>,
    ): Boolean = original.call(current, previous) || HeldItemUpdateFix.shouldPreserveUpdate(previous, current)
}
