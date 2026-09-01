package com.skysoft.mixin;

import com.skysoft.utils.mixin.MixinErrorBoundary;
import com.skysoft.integration.MixinFeatureAdapters;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundTabListPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public class TabListPacketMixin {
    @Inject(method = "handlePlayerInfoUpdate", at = @At("RETURN"))
    private void skysoftMarkDirtyAfterPlayerInfoUpdate(ClientboundPlayerInfoUpdatePacket packet, CallbackInfo ci) {
        if (affectsSnapshot(packet)) markDirty();
    }

    @Inject(method = "handlePlayerInfoRemove", at = @At("RETURN"))
    private void skysoftMarkDirtyAfterPlayerInfoRemove(ClientboundPlayerInfoRemovePacket packet, CallbackInfo ci) {
        markDirty();
    }

    @Inject(method = "handleTabListCustomisation", at = @At("RETURN"))
    private void skysoftMarkDirtyAfterTabListCustomisation(ClientboundTabListPacket packet, CallbackInfo ci) {
        markDirty();
    }

    private static boolean affectsSnapshot(ClientboundPlayerInfoUpdatePacket packet) {
        return packet.actions().contains(ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER)
            || packet.actions().contains(ClientboundPlayerInfoUpdatePacket.Action.UPDATE_GAME_MODE)
            || packet.actions().contains(ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LISTED)
            || packet.actions().contains(ClientboundPlayerInfoUpdatePacket.Action.UPDATE_DISPLAY_NAME)
            || packet.actions().contains(ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LIST_ORDER);
    }

    private static void markDirty() {
        MixinErrorBoundary.run("Tab list packet", MixinFeatureAdapters::markTabListDirty);
    }
}
