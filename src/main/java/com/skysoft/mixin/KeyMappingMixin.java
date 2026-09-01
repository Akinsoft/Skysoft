package com.skysoft.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.skysoft.utils.mixin.MixinErrorBoundary;
import com.skysoft.features.misc.autosprint.AutoSprint;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(KeyMapping.class)
public class KeyMappingMixin {
    @ModifyReturnValue(method = "isDown", at = @At("RETURN"))
    protected boolean skysoftAutoSprint(boolean original) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.options == null || (Object) this != minecraft.options.keySprint) return original;
        LocalPlayer player = minecraft.player;
        if (player == null) return original;
        boolean active = MixinErrorBoundary.value("Auto Sprint key state", false, () -> AutoSprint.INSTANCE.isActive(player));
        return original || !player.isSprinting() && active;
    }
}
