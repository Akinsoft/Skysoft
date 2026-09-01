package com.skysoft.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.skysoft.integration.MixinFeatureAdapters;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.contextualbar.ExperienceBarRenderer;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(ExperienceBarRenderer.class)
public class ExperienceBarMixin {
    @WrapMethod(method = "extractBackground")
    private void skysoftPositionVanillaExperienceBar(
        GuiGraphicsExtractor graphics,
        DeltaTracker deltaTracker,
        Operation<Void> original
    ) {
        MixinFeatureAdapters.renderVanillaExperienceBar(
            graphics,
            () -> original.call(graphics, deltaTracker)
        );
    }
}
