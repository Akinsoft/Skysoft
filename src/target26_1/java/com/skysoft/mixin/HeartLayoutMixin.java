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
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(Gui.class)
public abstract class HeartLayoutMixin {
    @Shadow @Final private Minecraft minecraft;
    @ModifyExpressionValue(method = "extractPlayerHealth", at = @At(value = "INVOKE", target = "Ljava/lang/Math;max(FF)F"))
    protected float skysoftMergeAbsorptionHeartRows(float vanillaValue) { return AbsorptionHeartLayout.INSTANCE.resolveMaximumHealth(vanillaValue, minecraft.getCameraEntity() instanceof Player player ? player : null); }
    @ModifyArg(method = "extractPlayerHealth", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Gui;extractHearts(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/world/entity/player/Player;IIIIFIIIZ)V"), index = 5)
    protected int skysoftStopRegenerationHeartBobbing(int vanillaValue) { return HeartBobbing.INSTANCE.resolveRegenerationOffset(vanillaValue); }
    @ModifyArg(method = "extractPlayerHealth", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Gui;extractHearts(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/world/entity/player/Player;IIIIFIIIZ)V"), index = 7)
    protected int skysoftMergeAbsorptionWithCurrentHealth(int vanillaValue) { return AbsorptionHeartLayout.INSTANCE.resolveVisibleHealth(vanillaValue, minecraft.getCameraEntity() instanceof Player player ? player : null); }
    @ModifyArg(method = "extractPlayerHealth", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Gui;extractHearts(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/world/entity/player/Player;IIIIFIIIZ)V"), index = 8)
    protected int skysoftMergeAbsorptionWithDisplayedHealth(int vanillaValue) { return AbsorptionHeartLayout.INSTANCE.resolveVisibleHealth(vanillaValue, minecraft.getCameraEntity() instanceof Player player ? player : null); }
    @ModifyExpressionValue(method = "extractHearts", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/RandomSource;nextInt(I)I"))
    protected int skysoftStopLowHealthHeartBobbing(int vanillaValue) { return HeartBobbing.INSTANCE.resolveLowHealthOffset(vanillaValue); }
}
