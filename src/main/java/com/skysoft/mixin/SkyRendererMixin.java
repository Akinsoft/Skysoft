package com.skysoft.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.skysoft.features.misc.SkyColor;
import net.minecraft.client.renderer.SkyRenderer;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(SkyRenderer.class)
public class SkyRendererMixin {
    @ModifyVariable(method = "renderSkyDisc", at = @At("HEAD"), argsOnly = true)
    private int skysoftUseCustomSkyColor(int original) {
        return SkyColor.skyColor(original);
    }

    @ModifyExpressionValue(
        method = "renderDarkDisc",
        at = @At(value = "NEW", target = "(FFFF)Lorg/joml/Vector4f;")
    )
    private Vector4f skysoftUseCustomVoidColor(Vector4f original) {
        return SkyColor.voidColor(original);
    }
}
