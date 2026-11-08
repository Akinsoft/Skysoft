package com.skysoft.mixin;

import com.skysoft.integration.MixinFeatureAdapters;
import com.skysoft.utils.mixin.MixinErrorBoundary;
import java.util.Collections;
import java.util.EnumSet;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundTabListPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class TabListPacketMixin {
    @Unique
    private static final EnumSet<ClientboundPlayerInfoUpdatePacket.Action> SNAPSHOT_ACTIONS = EnumSet.of(
        ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER,
        ClientboundPlayerInfoUpdatePacket.Action.UPDATE_GAME_MODE,
        ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LISTED,
        ClientboundPlayerInfoUpdatePacket.Action.UPDATE_DISPLAY_NAME,
        ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LIST_ORDER
    );

    @Inject(method = "handlePlayerInfoUpdate", at = @At("RETURN"))
    private void skysoftMarkDirtyAfterPlayerInfoUpdate(ClientboundPlayerInfoUpdatePacket packet, CallbackInfo ci) {
        if (!Collections.disjoint(packet.actions(), SNAPSHOT_ACTIONS)) markDirty();
    }

    @Inject(method = "handlePlayerInfoRemove", at = @At("RETURN"))
    private void skysoftMarkDirtyAfterPlayerInfoRemove(ClientboundPlayerInfoRemovePacket packet, CallbackInfo ci) {
        markDirty();
    }

    @Inject(method = "handleTabListCustomisation", at = @At("RETURN"))
    private void skysoftMarkDirtyAfterTabListCustomisation(ClientboundTabListPacket packet, CallbackInfo ci) {
        markDirty();
    }

    @Unique
    private static void markDirty() {
        MixinErrorBoundary.run("Tab list packet", MixinFeatureAdapters::markTabListDirty);
    }
}
