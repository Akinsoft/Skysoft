package com.skysoft.mixin;

import com.skysoft.utils.mixin.MixinFeatureAdapters;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.contextualbar.ExperienceBar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ExperienceBar.class)
public class ExperienceBarMixin {
    @Inject(method = "extractBackground", at = @At("HEAD"), cancellable = true)
    private void skysoftHideVanillaExperienceBar(
        GuiGraphicsExtractor graphics,
        DeltaTracker deltaTracker,
        CallbackInfo ci
    ) {
        if (MixinFeatureAdapters.shouldHideVanillaExperienceBar()) {
            ci.cancel();
        }
    }
}
