package com.skysoft.mixin

import com.llamalad7.mixinextras.injector.wrapoperation.Operation
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation
import com.skysoft.data.skyblock.SkyBlockDroppedItems
import com.skysoft.features.inventory.SlotLockManager
import com.skysoft.utils.SkysoftErrorBoundary
import com.skysoft.utils.input.InputHandlingResult
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientPacketListener
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At

@Mixin(Minecraft::class)
open class MinecraftSlotLockMixin {
    @WrapOperation(
        method = ["handleKeybinds"],
        at = [
            At(
                value = "INVOKE",
                target = "Lnet/minecraft/client/multiplayer/ClientPacketListener;send(Lnet/minecraft/network/protocol/Packet;)V",
            ),
        ],
    )
    protected fun skysoftProtectLockedSlotsFromOffhandSwap(
        connection: ClientPacketListener,
        packet: Packet<*>,
        original: Operation<Void>,
    ) {
        val minecraft = this as Minecraft
        SkysoftErrorBoundary.run("Dropped item tracking") {
            val stack = minecraft.player?.mainHandItem ?: return@run
            if (packet is ServerboundPlayerActionPacket) {
                when (packet.action) {
                    ServerboundPlayerActionPacket.Action.DROP_ITEM -> SkyBlockDroppedItems.recordIntent(stack, 1)
                    ServerboundPlayerActionPacket.Action.DROP_ALL_ITEMS ->
                        SkyBlockDroppedItems.recordIntent(stack, stack.count)
                    else -> Unit
                }
            }
        }
        val isBlocked = SkysoftErrorBoundary.value("Offhand swap protection", false) {
            packet is ServerboundPlayerActionPacket &&
                packet.action == ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND &&
                SlotLockManager.handleOffhandSwap(minecraft.player) == InputHandlingResult.CONSUMED
        }
        if (isBlocked) return
        original.call(connection, packet)
    }
}
