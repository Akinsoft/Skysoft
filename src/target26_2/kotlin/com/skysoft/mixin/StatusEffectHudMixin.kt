package com.skysoft.mixin

import com.skysoft.config.SkysoftConfigGui
import net.minecraft.client.gui.Hud
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

@Mixin(Hud::class)
abstract class StatusEffectHudMixin {
    @Inject(method = ["extractEffects"], at = [At("HEAD")], cancellable = true)
    protected fun skysoftHideVanillaStatusEffects(ci: CallbackInfo) {
        if (SkysoftConfigGui.config().gui.areVanillaStatusEffectsHidden) ci.cancel()
    }
}
