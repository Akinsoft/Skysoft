package com.skysoft.mixin;

import com.skysoft.utils.mixin.MixinErrorBoundary;
import com.skysoft.config.SkysoftConfigGui;
import net.minecraft.client.gui.Hud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Hud.class)
public abstract class StatusEffectHudMixin {
    @Inject(method = "extractEffects", at = @At("HEAD"), cancellable = true)
    protected void skysoftHideVanillaStatusEffects(CallbackInfo ci) {
        boolean hide = MixinErrorBoundary.value("Vanilla status effect HUD visibility", false, () -> SkysoftConfigGui.INSTANCE.config().gui.vanillaUi.areVanillaStatusEffectsHidden);
        if (hide) ci.cancel();
    }
}
