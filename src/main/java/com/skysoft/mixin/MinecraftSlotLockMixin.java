package com.skysoft.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.skysoft.data.skyblock.SkyBlockDroppedItems;
import com.skysoft.features.inventory.slots.SlotLockManager;
import com.skysoft.utils.input.InputHandlingResult;
import com.skysoft.utils.mixin.MixinErrorBoundary;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Minecraft.class)
public abstract class MinecraftSlotLockMixin {
    @WrapOperation(
        method = "handleKeybinds",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/multiplayer/ClientPacketListener;send(Lnet/minecraft/network/protocol/Packet;)V"
        )
    )
    private void skysoftTrackDropsAndProtectOffhandSwap(
        ClientPacketListener connection,
        Packet<?> packet,
        Operation<Void> original
    ) {
        Minecraft minecraft = (Minecraft) (Object) this;
        MixinErrorBoundary.run("Dropped item tracking", () -> {
            if (minecraft.player == null || !(packet instanceof ServerboundPlayerActionPacket action)) return;
            ItemStack held = minecraft.player.getMainHandItem();
            if (action.getAction() == ServerboundPlayerActionPacket.Action.DROP_ITEM) {
                SkyBlockDroppedItems.INSTANCE.recordIntent(held, 1);
            } else if (action.getAction() == ServerboundPlayerActionPacket.Action.DROP_ALL_ITEMS) {
                SkyBlockDroppedItems.INSTANCE.recordIntent(held, held.getCount());
            }
        });
        boolean blocked = MixinErrorBoundary.value("Offhand swap protection", false, () ->
            packet instanceof ServerboundPlayerActionPacket action
                && action.getAction() == ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND
                && SlotLockManager.handleOffhandSwap(minecraft.player) == InputHandlingResult.CONSUMED
        );
        if (blocked) return;
        original.call(connection, packet);
    }
}
