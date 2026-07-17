package com.skysoft.mixin

import com.llamalad7.mixinextras.injector.wrapoperation.Operation
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation
import com.skysoft.features.helditem.HeldItemUpdateFix
import com.skysoft.features.inventory.SmoothSwapping
import com.skysoft.utils.MinecraftClient
import com.skysoft.utils.SkysoftErrorBoundary
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.multiplayer.MultiPlayerGameMode
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.ContainerInput
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
    ): Boolean {
        if (original.call(current, previous)) return true
        return SkysoftErrorBoundary.value("Held Item destroy target update", false) {
            HeldItemUpdateFix.shouldPreserveUpdate(previous, current)
        }
    }

    @WrapOperation(
        method = ["handleContainerInput"],
        at = [
            At(
                value = "INVOKE",
                target = "Lnet/minecraft/world/inventory/AbstractContainerMenu;" +
                    "clicked(IILnet/minecraft/world/inventory/ContainerInput;" +
                    "Lnet/minecraft/world/entity/player/Player;)V",
            ),
        ],
    )
    protected fun skysoftAnimateLocalContainerMutation(
        menu: AbstractContainerMenu,
        slotNum: Int,
        buttonNum: Int,
        input: ContainerInput,
        player: Player,
        original: Operation<Void>,
    ) {
        val screen = MinecraftClient.screen() as? AbstractContainerScreen<*>
        if (screen == null || screen.menu !== menu) {
            original.call(menu, slotNum, buttonNum, input, player)
            return
        }
        SkysoftErrorBoundary.aroundUnit(
            "Smooth Swapping local mutation",
            { original.call(menu, slotNum, buttonNum, input, player) },
        ) { mutate ->
            SmoothSwapping.animateLocalContainerMutation(screen, mutate)
        }
    }
}
