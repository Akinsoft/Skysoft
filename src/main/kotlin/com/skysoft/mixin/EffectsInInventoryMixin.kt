package com.skysoft.mixin

import com.skysoft.config.SkysoftConfigGui
import com.skysoft.utils.SkysoftErrorBoundary
import net.minecraft.client.gui.screens.inventory.EffectsInInventory
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

@Mixin(EffectsInInventory::class)
abstract class EffectsInInventoryMixin {
    @Inject(method = ["extractRenderState"], at = [At("HEAD")], cancellable = true)
    protected fun skysoftHideVanillaStatusEffects(ci: CallbackInfo) {
        val shouldHide = SkysoftErrorBoundary.value("Inventory status effect visibility", false) {
            SkysoftConfigGui.config().gui.areVanillaStatusEffectsHidden
        }
        if (shouldHide) ci.cancel()
    }
}
