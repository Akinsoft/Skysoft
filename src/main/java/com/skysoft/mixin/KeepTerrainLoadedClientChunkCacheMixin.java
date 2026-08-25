package com.skysoft.mixin;

import com.skysoft.features.misc.KeepTerrainLoaded;
import com.skysoft.utils.mixin.MixinErrorBoundary;
import net.minecraft.client.multiplayer.ClientChunkCache;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(ClientChunkCache.class)
public abstract class KeepTerrainLoadedClientChunkCacheMixin {
    @ModifyVariable(method = "updateViewRadius", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private int skysoftExpandTerrainCache(int viewDistance) {
        return MixinErrorBoundary.value(
            "Keep Terrain Loaded cache distance",
            viewDistance,
            () -> KeepTerrainLoaded.storageViewDistance(viewDistance)
        );
    }
}
