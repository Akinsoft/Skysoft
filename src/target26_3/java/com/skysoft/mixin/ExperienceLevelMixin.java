package com.skysoft.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.skysoft.integration.MixinFeatureAdapters;
import net.minecraft.client.gui.Hud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Hud.class)
public class ExperienceLevelMixin {
    @ModifyExpressionValue(
        method = "extractHotbarAndDecorations",
        at = @At(
            value = "FIELD",
            target = "Lnet/minecraft/client/player/LocalPlayer;experienceLevel:I",
            ordinal = 0
        )
    )
    private int skysoftShowSkyBlockExperienceLevel(int original) {
        return MixinFeatureAdapters.skyBlockExperienceLevelVisibility(original);
    }

    @ModifyExpressionValue(
        method = "extractHotbarAndDecorations",
        at = @At(
            value = "FIELD",
            target = "Lnet/minecraft/client/player/LocalPlayer;experienceLevel:I",
            ordinal = 1
        )
    )
    private int skysoftUseSkyBlockExperienceLevel(int original) {
        return MixinFeatureAdapters.skyBlockExperienceLevel(original);
    }
}
