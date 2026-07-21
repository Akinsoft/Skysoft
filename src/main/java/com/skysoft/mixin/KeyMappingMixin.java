package com.skysoft.mixin;

import com.skysoft.utils.mixin.MixinErrorBoundary;
import com.skysoft.features.misc.autosprint.AutoSprint;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(KeyMapping.class)
public class KeyMappingMixin {
    @Inject(method = "isDown", at = @At("HEAD"), cancellable = true)
    protected void skysoftAutoSprint(CallbackInfoReturnable<Boolean> cir) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.options == null || (Object) this != minecraft.options.keySprint) return;
        LocalPlayer player = minecraft.player;
        if (player == null) return;
        boolean active = MixinErrorBoundary.value("Auto Sprint key state", false, () -> AutoSprint.INSTANCE.isActive(player));
        if (!player.isSprinting() && active) cir.setReturnValue(true);
    }
}
