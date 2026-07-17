package com.skysoft.mixin

import com.skysoft.config.SkysoftConfigGui
import com.skysoft.utils.SkysoftErrorBoundary
import net.minecraft.client.gui.Hud
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

@Mixin(Hud::class)
abstract class StatusEffectHudMixin {
    @Inject(method = ["extractEffects"], at = [At("HEAD")], cancellable = true)
    protected fun skysoftHideVanillaStatusEffects(ci: CallbackInfo) {
        val shouldHide = SkysoftErrorBoundary.value("Vanilla status effect HUD visibility", false) {
            SkysoftConfigGui.config().gui.areVanillaStatusEffectsHidden
        }
        if (shouldHide) ci.cancel()
    }
}
