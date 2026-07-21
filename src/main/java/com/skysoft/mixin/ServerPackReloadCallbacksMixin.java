package com.skysoft.mixin;

import com.skysoft.features.misc.SkyBlockResourcePackManagerBridge;
import java.util.List;
import net.minecraft.client.resources.server.ServerPackManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.client.resources.server.ServerPackManager$1")
public class ServerPackReloadCallbacksMixin {
    @Shadow
    @Final
    private List<?> val$packsToLoad;

    @Shadow
    @Final
    private ServerPackManager this$0;

    @Inject(method = "onSuccess", at = @At("TAIL"))
    private void skysoftMarkResourcePacksApplied(CallbackInfo ci) {
        ((SkyBlockResourcePackManagerBridge) this.this$0).skysoftMarkResourcePacksApplied(this.val$packsToLoad);
    }
}
