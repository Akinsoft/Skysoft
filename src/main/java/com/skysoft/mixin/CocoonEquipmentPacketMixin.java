package com.skysoft.mixin;

import com.skysoft.utils.mixin.MixinFeatureAdapters;
import com.skysoft.utils.mixin.MixinErrorBoundary;
import java.util.ArrayList;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class CocoonEquipmentPacketMixin {
    @Inject(method = "handleSetEquipment", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/network/PacketProcessor;)V", shift = At.Shift.AFTER))
    protected void skysoftDetectCocoonEquipment(ClientboundSetEquipmentPacket packet, CallbackInfo ci) {
        MixinErrorBoundary.run("Cocoon equipment packet", () -> {
            var stacks = new ArrayList<ItemStack>();
            packet.getSlots().forEach(pair -> stacks.add(pair.getSecond()));
            MixinFeatureAdapters.handleCocoonEquipment(packet.getEntity(), stacks);
        });
    }
}
