package com.skysoft.mixin;

import com.skysoft.utils.mixin.MixinErrorBoundary;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.skysoft.config.SkysoftConfigGui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.TooltipRenderUtil;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(TooltipRenderUtil.class)
public class TooltipRenderUtilMixin {
    @WrapOperation(
        method = "extractTooltipBackground",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blitSprite(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIII)V",
            ordinal = 0
        )
    )
    private static void skysoftRenderSolidTooltipBackground(
        GuiGraphicsExtractor graphics,
        RenderPipeline pipeline,
        Identifier sprite,
        int x,
        int y,
        int width,
        int height,
        Operation<Void> original
    ) {
        RenderPipeline backgroundPipeline = MixinErrorBoundary.value("Solid tooltip background", pipeline,
            () -> SkysoftConfigGui.INSTANCE.config().inventory.isTooltipBackgroundSolid
                ? RenderPipelines.GUI_OPAQUE_TEXTURED_BACKGROUND
                : pipeline);
        original.call(graphics, backgroundPipeline, sprite, x, y, width, height);
    }
}
