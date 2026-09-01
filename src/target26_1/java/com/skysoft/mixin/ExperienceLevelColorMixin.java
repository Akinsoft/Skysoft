package com.skysoft.mixin;

import com.skysoft.integration.MixinFeatureAdapters;
import net.minecraft.client.gui.contextualbar.ContextualBarRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(ContextualBarRenderer.class)
public interface ExperienceLevelColorMixin {
    @ModifyArg(
        method = "extractExperienceLevel",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;text(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IIIZ)V",
            ordinal = 4
        ),
        index = 4
    )
    private static int skysoftColorSkyBlockExperienceLevel(int original) {
        return MixinFeatureAdapters.skyBlockExperienceLevelColor(original);
    }
}
