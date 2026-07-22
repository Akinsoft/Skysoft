package com.skysoft.mixin;

import com.skysoft.utils.mixin.MixinErrorBoundary;
import com.skysoft.config.SkysoftConfigGui;
import net.minecraft.client.gui.screens.inventory.EffectsInInventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EffectsInInventory.class)
public abstract class EffectsInInventoryMixin {
    @Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
    protected void skysoftHideVanillaStatusEffects(CallbackInfo ci) {
        boolean hide = MixinErrorBoundary.value("Inventory status effect visibility", false,
            () -> SkysoftConfigGui.INSTANCE.config().gui.vanillaUi.areVanillaStatusEffectsHidden);
        if (hide) ci.cancel();
    }
}
