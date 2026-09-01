package com.skysoft.mixin;

import com.skysoft.utils.mixin.MixinErrorBoundary;
import com.skysoft.integration.MixinFeatureAdapters;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundResetScorePacket;
import net.minecraft.network.protocol.game.ClientboundSetDisplayObjectivePacket;
import net.minecraft.network.protocol.game.ClientboundSetObjectivePacket;
import net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket;
import net.minecraft.network.protocol.game.ClientboundSetScorePacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public class ScoreboardPacketMixin {
    @Inject(method = "handleAddObjective", at = @At("RETURN"))
    private void skysoftMarkDirtyAfterObjective(ClientboundSetObjectivePacket packet, CallbackInfo ci) {
        markDirty();
    }

    @Inject(method = "handleSetScore", at = @At("RETURN"))
    private void skysoftMarkDirtyAfterScore(ClientboundSetScorePacket packet, CallbackInfo ci) {
        markDirty();
    }

    @Inject(method = "handleResetScore", at = @At("RETURN"))
    private void skysoftMarkDirtyAfterScoreReset(ClientboundResetScorePacket packet, CallbackInfo ci) {
        markDirty();
    }

    @Inject(method = "handleSetDisplayObjective", at = @At("RETURN"))
    private void skysoftMarkDirtyAfterDisplayObjective(ClientboundSetDisplayObjectivePacket packet, CallbackInfo ci) {
        markDirty();
    }

    @Inject(method = "handleSetPlayerTeamPacket", at = @At("RETURN"))
    private void skysoftMarkDirtyAfterPlayerTeam(ClientboundSetPlayerTeamPacket packet, CallbackInfo ci) {
        markDirty();
        MixinErrorBoundary.run("Tab list player team packet", MixinFeatureAdapters::markTabListDirty);
    }

    private static void markDirty() {
        MixinErrorBoundary.run("Sidebar scoreboard packet", MixinFeatureAdapters::markSidebarScoreboardDirty);
    }
}
