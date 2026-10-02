package com.skysoft.mixin;

import com.skysoft.utils.mixin.MixinErrorBoundary;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.skysoft.features.inventory.SlotLockManager;
import com.skysoft.utils.input.InputHandlingResult;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Minecraft.class)
public class MinecraftSlotLockMixin {
    @WrapOperation(
        method = "handleKeybinds",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/multiplayer/ClientPacketListener;send(Lnet/minecraft/network/protocol/Packet;)V"
        )
    )
    private void skysoftProtectLockedSlotsFromOffhandSwap(
        ClientPacketListener connection,
        Packet<?> packet,
        Operation<Void> original
    ) {
        Minecraft minecraft = (Minecraft) (Object) this;
        boolean blocked = MixinErrorBoundary.value("Offhand swap protection", false, () ->
            packet instanceof ServerboundPlayerActionPacket action
                && action.getAction() == ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND
                && SlotLockManager.handleOffhandSwap(minecraft.player) == InputHandlingResult.CONSUMED
        );
        if (blocked) return;
        original.call(connection, packet);
    }
}
