package com.skysoft.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.skysoft.features.misc.AbsorptionHeartLayout;
import com.skysoft.features.misc.HeartBobbing;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(Gui.class)
public abstract class HeartLayoutMixin {
    @Unique
    private static final String EXTRACT_HEARTS =
        "Lnet/minecraft/client/gui/Gui;extractHearts(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/world/entity/player/Player;IIIIFIIIZ)V";

    @Shadow @Final private Minecraft minecraft;

    @ModifyExpressionValue(method = "extractPlayerHealth", at = @At(value = "INVOKE", target = "Ljava/lang/Math;max(FF)F"))
    private float skysoftMergeAbsorptionHeartRows(float vanillaValue) {
        return AbsorptionHeartLayout.INSTANCE.resolveMaximumHealth(vanillaValue, skysoftCameraPlayer());
    }

    @ModifyArg(method = "extractPlayerHealth", at = @At(value = "INVOKE", target = EXTRACT_HEARTS), index = 5)
    private int skysoftStopRegenerationHeartBobbing(int vanillaValue) {
        return HeartBobbing.INSTANCE.resolveRegenerationOffset(vanillaValue);
    }

    @ModifyArg(method = "extractPlayerHealth", at = @At(value = "INVOKE", target = EXTRACT_HEARTS), index = 7)
    private int skysoftMergeAbsorptionWithCurrentHealth(int vanillaValue) {
        return AbsorptionHeartLayout.INSTANCE.resolveVisibleHealth(vanillaValue, skysoftCameraPlayer());
    }

    @ModifyArg(method = "extractPlayerHealth", at = @At(value = "INVOKE", target = EXTRACT_HEARTS), index = 8)
    private int skysoftMergeAbsorptionWithDisplayedHealth(int vanillaValue) {
        return AbsorptionHeartLayout.INSTANCE.resolveVisibleHealth(vanillaValue, skysoftCameraPlayer());
    }

    @ModifyExpressionValue(method = "extractHearts", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/RandomSource;nextInt(I)I"))
    private int skysoftStopLowHealthHeartBobbing(int vanillaValue) {
        return HeartBobbing.INSTANCE.resolveLowHealthOffset(vanillaValue);
    }

    @Unique
    private Player skysoftCameraPlayer() {
        return minecraft.getCameraEntity() instanceof Player player ? player : null;
    }
}
