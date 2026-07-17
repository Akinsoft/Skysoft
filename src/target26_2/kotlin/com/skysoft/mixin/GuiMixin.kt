package com.skysoft.mixin

import com.llamalad7.mixinextras.injector.wrapoperation.Operation
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation
import com.mojang.blaze3d.platform.Window
import com.skysoft.gui.scale.GuiScaleController
import com.skysoft.utils.SkysoftErrorBoundary
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Gui
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.renderer.state.gui.GuiRenderState
import org.spongepowered.asm.mixin.Final
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.Shadow
import org.spongepowered.asm.mixin.Unique
import org.spongepowered.asm.mixin.injection.At

@Mixin(Gui::class)
abstract class GuiMixin {
    @Shadow
    @Final
    private lateinit var minecraft: Minecraft

    @WrapOperation(
        method = ["extractRenderState"],
        at = [
            At(
                value = "INVOKE",
                target = "Lnet/minecraft/client/gui/screens/Screen;" +
                    "extractRenderStateWithTooltipAndSubtitles(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V",
            ),
        ],
    )
    protected fun skysoftExtractInventoryAtSeparateScale(
        screen: Screen,
        graphics: GuiGraphicsExtractor,
        mouseX: Int,
        mouseY: Int,
        delta: Float,
        original: Operation<Void>,
    ) {
        var operationCalled = false
        var operationFailure: Throwable? = null
        SkysoftErrorBoundary.run("Inventory GUI scale extraction") {
            extractInventoryAtSeparateScale(screen, graphics, mouseX, mouseY) { renderGraphics, x, y ->
                operationCalled = true
                try {
                    original.call(screen, renderGraphics, x, y, delta)
                } catch (failure: Throwable) {
                    operationFailure = failure
                }
            }
        }
        if (!operationCalled) original.call(screen, graphics, mouseX, mouseY, delta)
        operationFailure?.let { throw it }
    }

    @Unique
    private fun extractInventoryAtSeparateScale(
        screen: Screen,
        graphics: GuiGraphicsExtractor,
        mouseX: Int,
        mouseY: Int,
        render: (GuiGraphicsExtractor, Int, Int) -> Unit,
    ) {
        val window = minecraft.window
        if (!GuiScaleController.usesSeparateInventoryScale(screen)) {
            GuiScaleController.restoreScreenDimensions(screen, window)
            render(graphics, mouseX, mouseY)
            return
        }

        val screenRenderState = GuiRenderState()
        val aboveScreenRenderState = GuiRenderState()
        try {
            GuiScaleController.useInventoryScale(screen, window).use {
                skysoftSyncWindowScale(window)
                GuiScaleController.updateScreenDimensions(screen, window)
                val scaledMouseX = minecraft.mouseHandler.getScaledXPos(window).toInt()
                val scaledMouseY = minecraft.mouseHandler.getScaledYPos(window).toInt()
                val scaledGraphics = GuiGraphicsExtractor(
                    minecraft,
                    screenRenderState,
                    scaledMouseX,
                    scaledMouseY,
                )
                render(scaledGraphics, scaledMouseX, scaledMouseY)
                (graphics as GuiGraphicsExtractorAccessor).skysoftSetGuiRenderState(aboveScreenRenderState)
                GuiScaleController.submitRenderBatch(screenRenderState, aboveScreenRenderState)
            }
        } finally {
            skysoftSyncWindowScale(window)
        }
    }

    @Unique
    private fun skysoftSyncWindowScale(window: Window) {
        minecraft.gameRenderer.gameRenderState().windowRenderState.guiScale = window.guiScale
    }
}
