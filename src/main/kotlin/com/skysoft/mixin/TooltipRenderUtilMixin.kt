package com.skysoft.mixin

import com.llamalad7.mixinextras.injector.wrapoperation.Operation
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation
import com.mojang.blaze3d.pipeline.RenderPipeline
import com.skysoft.config.SkysoftConfigGui
import com.skysoft.utils.SkysoftErrorBoundary
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.inventory.tooltip.TooltipRenderUtil
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.resources.Identifier
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At

@Mixin(TooltipRenderUtil::class)
abstract class TooltipRenderUtilMixin private constructor() {
    private companion object {
        @JvmStatic
        @WrapOperation(
            method = ["extractTooltipBackground"],
            at = [
                At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blitSprite(" +
                        "Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIII)V",
                    ordinal = 0,
                ),
            ],
        )
        private fun skysoftRenderSolidTooltipBackground(
            graphics: GuiGraphicsExtractor,
            pipeline: RenderPipeline,
            sprite: Identifier,
            x: Int,
            y: Int,
            width: Int,
            height: Int,
            original: Operation<Void>,
        ) {
            val backgroundPipeline = SkysoftErrorBoundary.value("Solid tooltip background", pipeline) {
                if (SkysoftConfigGui.config().inventory.isTooltipBackgroundSolid) {
                    RenderPipelines.GUI_OPAQUE_TEXTURED_BACKGROUND
                } else {
                    pipeline
                }
            }
            original.call(graphics, backgroundPipeline, sprite, x, y, width, height)
        }
    }
}
