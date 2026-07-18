package com.skysoft.mixin

import com.llamalad7.mixinextras.injector.wrapoperation.Operation
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation
import com.mojang.blaze3d.pipeline.RenderPipeline
import com.skysoft.features.misc.CenteredCrosshairFix
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.Hud
import net.minecraft.resources.Identifier
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At

@Mixin(Hud::class)
abstract class CenteredCrosshairMixin {
    @WrapOperation(
        method = ["extractCrosshair"],
        at = [
            At(
                value = "INVOKE",
                target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;" +
                    "blitSprite(Lcom/mojang/blaze3d/pipeline/RenderPipeline;" +
                    "Lnet/minecraft/resources/Identifier;IIII)V",
                ordinal = 0,
            ),
        ],
    )
    protected fun skysoftCenterCrosshair(
        graphics: GuiGraphicsExtractor,
        pipeline: RenderPipeline,
        sprite: Identifier,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        original: Operation<Void>,
    ) {
        if (!CenteredCrosshairFix.isEnabled()) {
            original.call(graphics, pipeline, sprite, x, y, width, height)
            return
        }
        CenteredCrosshairFix.renderCentered(graphics, x, y, width, height) {
            original.call(graphics, pipeline, sprite, x, y, width, height)
        }
    }
}
