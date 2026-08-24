package com.skysoft.mixin;

import com.skysoft.features.event.diana.DianaHubTerrainCache;
import com.skysoft.utils.mixin.MixinErrorBoundary;
import net.minecraft.client.multiplayer.ClientChunkCache;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(ClientChunkCache.class)
public abstract class DianaHubTerrainClientChunkCacheMixin {
    @ModifyVariable(method = "updateViewRadius", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private int skysoftExpandDianaHubCache(int viewDistance) {
        return skysoftDianaHubCacheDistance(viewDistance);
    }

    private int skysoftDianaHubCacheDistance(int viewDistance) {
        return MixinErrorBoundary.value(
            "Diana Hub terrain cache distance",
            viewDistance,
            () -> DianaHubTerrainCache.storageViewDistance(viewDistance)
        );
    }
}
