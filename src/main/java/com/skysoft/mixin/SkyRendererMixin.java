package com.skysoft.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.skysoft.config.SkyColorConfig;
import com.skysoft.config.SkysoftConfigGui;
import com.skysoft.utils.ColorUtilities;
import net.minecraft.client.renderer.SkyRenderer;
import net.minecraft.util.ARGB;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(SkyRenderer.class)
public class SkyRendererMixin {
    @ModifyVariable(method = "renderSkyDisc", at = @At("HEAD"), argsOnly = true)
    private int skysoftUseCustomSkyColor(int original) {
        SkyColorConfig config = SkysoftConfigGui.INSTANCE.config().misc.skyColor;
        return config.enabled
            ? config.details.color.get().getEffectiveColourRGB() & ColorUtilities.RGB_MASK
            : original;
    }

    @ModifyExpressionValue(
        method = "renderDarkDisc",
        at = @At(value = "NEW", target = "(FFFF)Lorg/joml/Vector4f;")
    )
    private Vector4f skysoftUseCustomVoidColor(Vector4f original) {
        SkyColorConfig config = SkysoftConfigGui.INSTANCE.config().misc.skyColor;
        if (!config.enabled) {
            return original;
        }

        int rgb = config.details.voidColor.get().getEffectiveColourRGB();
        return ARGB.vector4fFromARGB32(rgb | 0xFF000000);
    }
}
