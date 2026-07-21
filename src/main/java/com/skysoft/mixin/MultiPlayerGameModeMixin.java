package com.skysoft.mixin;

import com.skysoft.utils.mixin.MixinFeatureAdapters;
import com.skysoft.utils.mixin.MixinErrorBoundary;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.skysoft.data.skyblock.AttributeShardTransfers;
import com.skysoft.data.skyblock.SkyBlockDroppedItems;
import com.skysoft.data.skyblock.SkyBlockSackTransfers;
import com.skysoft.features.helditem.HeldItemUpdateFix;
import com.skysoft.utils.MinecraftClient;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MultiPlayerGameMode.class)
public class MultiPlayerGameModeMixin {
    private static final int OUTSIDE_SLOT = -999;
    private static final String HUNTING_BOX_TITLE = "Hunting Box";
    private static final String SACK_OF_SACKS_TITLE = "Sack of Sacks";
    private static final String INSERT_INVENTORY_NAME = "Insert inventory";

    @Inject(method = "handleContainerInput", at = @At("HEAD"))
    protected void skysoftTrackContainerItemDrop(int containerId, int slotNum, int buttonNum, ContainerInput input, Player player, CallbackInfo ci) {
        if (ci.isCancelled() || player.containerMenu.containerId != containerId) return;
        Screen current = MinecraftClient.INSTANCE.screen();
        AbstractContainerScreen<?> screen = current instanceof AbstractContainerScreen<?> container ? container : null;
        boolean validSlot = slotNum >= 0 && slotNum < player.containerMenu.slots.size();
        if (screen != null && screen.getMenu() == player.containerMenu && screen.getTitle().getString().equals(HUNTING_BOX_TITLE) && buttonNum == 1 && validSlot) {
            AttributeShardTransfers.INSTANCE.recordRemoval(player.containerMenu.getSlot(slotNum).getItem());
        }
        if (screen != null && screen.getMenu() == player.containerMenu && screen.getTitle().getString().equals(SACK_OF_SACKS_TITLE) && validSlot && player.containerMenu.getSlot(slotNum).getItem().getHoverName().getString().equals(INSERT_INVENTORY_NAME)) {
            SkyBlockSackTransfers.INSTANCE.recordInsertInventory();
        }
        ItemStack stack;
        if (input == ContainerInput.THROW && validSlot) stack = player.containerMenu.getSlot(slotNum).getItem();
        else if (input == ContainerInput.PICKUP && slotNum == OUTSIDE_SLOT) stack = player.containerMenu.getCarried();
        else return;
        int amount = buttonNum == 0 ? input == ContainerInput.THROW ? 1 : stack.getCount() : input == ContainerInput.THROW ? stack.getCount() : 1;
        SkyBlockDroppedItems.INSTANCE.recordIntent(stack, amount);
    }

    @WrapOperation(method = "sameDestroyTarget", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;isSameItemSameComponents(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;)Z"))
    protected boolean isSkysoftSameDestroyTargetAfterItemUpdate(ItemStack current, ItemStack previous, Operation<Boolean> original) {
        if (original.call(current, previous)) return true;
        return MixinErrorBoundary.value("Held Item destroy target update", false, () -> HeldItemUpdateFix.INSTANCE.shouldPreserveUpdate(previous, current));
    }

    @WrapOperation(method = "handleContainerInput", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/inventory/AbstractContainerMenu;clicked(IILnet/minecraft/world/inventory/ContainerInput;Lnet/minecraft/world/entity/player/Player;)V"))
    protected void skysoftAnimateLocalContainerMutation(AbstractContainerMenu menu, int slotNum, int buttonNum, ContainerInput input, Player player, Operation<Void> original) {
        Screen current = MinecraftClient.INSTANCE.screen();
        if (!(current instanceof AbstractContainerScreen<?> screen) || screen.getMenu() != menu) {
            original.call(menu, slotNum, buttonNum, input, player);
            return;
        }
        MixinErrorBoundary.aroundUnit("Smooth Swapping local mutation",
            () -> original.call(menu, slotNum, buttonNum, input, player),
            mutate -> MixinFeatureAdapters.animateLocalContainerMutation(screen, mutate));
    }
}
