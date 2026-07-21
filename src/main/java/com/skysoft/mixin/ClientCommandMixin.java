package com.skysoft.mixin;

import com.skysoft.utils.mixin.MixinErrorBoundary;
import com.skysoft.utils.mixin.MixinFeatureAdapters;
import net.minecraft.client.multiplayer.ClientPacketListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(ClientPacketListener.class)
public abstract class ClientCommandMixin {
    @ModifyVariable(method = "sendCommand", at = @At("HEAD"), argsOnly = true)
    private String skysoftRewriteOutgoingCommand(String command) {
        return MixinErrorBoundary.value(
            "Short Warp Commands rewrite",
            command,
            () -> MixinFeatureAdapters.rewriteOutgoingCommand(command)
        );
    }
}
