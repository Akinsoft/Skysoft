package com.skysoft.mixin

import com.llamalad7.mixinextras.injector.wrapoperation.Operation
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation
import com.skysoft.data.skyblock.AttributeShardTransfers
import com.skysoft.data.skyblock.SkyBlockDroppedItems
import com.skysoft.data.skyblock.SkyBlockSackTransfers
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
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

@Mixin(MultiPlayerGameMode::class)
open class MultiPlayerGameModeMixin {
    @Inject(method = ["handleContainerInput"], at = [At("HEAD")])
    protected fun skysoftTrackContainerItemDrop(
        containerId: Int,
        slotNum: Int,
        buttonNum: Int,
        input: ContainerInput,
        player: Player,
        callbackInfo: CallbackInfo,
    ) {
        if (callbackInfo.isCancelled || player.containerMenu.containerId != containerId) return
        val screen = MinecraftClient.screen() as? AbstractContainerScreen<*>
        if (screen?.menu === player.containerMenu && screen.title.string == HUNTING_BOX_TITLE && buttonNum == 1 &&
            slotNum in player.containerMenu.slots.indices
        ) {
            AttributeShardTransfers.recordRemoval(player.containerMenu.getSlot(slotNum).item)
        }
        if (screen?.menu === player.containerMenu && screen.title.string == SACK_OF_SACKS_TITLE &&
            slotNum in player.containerMenu.slots.indices &&
            player.containerMenu.getSlot(slotNum).item.hoverName.string == INSERT_INVENTORY_NAME
        ) {
            SkyBlockSackTransfers.recordInsertInventory()
        }
        val stack = when {
            input == ContainerInput.THROW && slotNum in player.containerMenu.slots.indices ->
                player.containerMenu.getSlot(slotNum).item

            input == ContainerInput.PICKUP && slotNum == OUTSIDE_SLOT -> player.containerMenu.carried
            else -> return
        }
        val amount = if (buttonNum == 0) {
            if (input == ContainerInput.THROW) 1 else stack.count
        } else {
            if (input == ContainerInput.THROW) stack.count else 1
        }
        SkyBlockDroppedItems.recordIntent(stack, amount)
    }

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

private const val OUTSIDE_SLOT = -999
private const val HUNTING_BOX_TITLE = "Hunting Box"
private const val SACK_OF_SACKS_TITLE = "Sack of Sacks"
private const val INSERT_INVENTORY_NAME = "Insert inventory"
