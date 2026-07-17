package com.skysoft.mixin

import com.llamalad7.mixinextras.injector.wrapoperation.Operation
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation
import com.mojang.blaze3d.buffers.GpuBufferSlice
import com.mojang.blaze3d.platform.Window
import com.skysoft.features.misc.blockoverlay.BlockOverlay
import com.skysoft.gui.GuiOverlayLayer
import com.skysoft.gui.GuiOverlayRegistry
import com.skysoft.gui.scale.GuiScaleController
import com.skysoft.utils.MinecraftClient
import com.skysoft.utils.SkysoftErrorBoundary
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.render.GuiRenderer
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.renderer.GameRenderer
import net.minecraft.client.renderer.fog.FogRenderer
import net.minecraft.client.renderer.state.GameRenderState
import net.minecraft.client.renderer.state.gui.GuiRenderState
import org.spongepowered.asm.mixin.Final
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.Shadow
import org.spongepowered.asm.mixin.Unique
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable

@Mixin(GameRenderer::class)
abstract class GameRendererMixin {
    @Shadow
    @Final
    private lateinit var minecraft: Minecraft

    @Shadow
    @Final
    private lateinit var guiRenderer: GuiRenderer

    @Shadow
    @Final
    private lateinit var fogRenderer: FogRenderer

    @Shadow
    @Final
    private lateinit var gameRenderState: GameRenderState

    @Inject(method = ["shouldRenderBlockOutline"], at = [At("RETURN")], cancellable = true)
    protected fun skysoftReplaceBlockOutline(callback: CallbackInfoReturnable<Boolean>) {
        callback.returnValue = SkysoftErrorBoundary.value("Block Overlay outline selection", callback.returnValue) {
            BlockOverlay.selectBlockOutline(callback.returnValue).rendersVanilla
        }
    }

    @WrapOperation(
        method = ["extractGui"],
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

    @Inject(
        method = ["render"],
        at = [
            At(
                value = "INVOKE",
                target = "Lnet/minecraft/client/gui/render/GuiRenderer;render(Lcom/mojang/blaze3d/buffers/GpuBufferSlice;)V",
                shift = At.Shift.AFTER,
            ),
        ],
    )
    protected fun skysoftRenderInventoryAtSeparateScale(
        deltaTracker: DeltaTracker,
        renderLevel: Boolean,
        callbackInfo: CallbackInfo,
    ) {
        SkysoftErrorBoundary.run("Inventory GUI scale rendering") {
            val defaultRenderState = skysoftGetDefaultRenderState()
            val window = minecraft.window
            val renderStates = GuiScaleController.takeRenderBatch()
            if (renderStates != null) {
                val screen = MinecraftClient.screen(minecraft)
                try {
                    GuiScaleController.useInventoryScale(screen, window).use {
                        skysoftSyncWindowScale(window)
                        (guiRenderer as GuiRendererAccessor).skysoftSetRenderState(renderStates.inventory())
                        val fogBuffer = fogRenderer.getBuffer(FogRenderer.FogMode.NONE)
                        guiRenderer.render(fogBuffer)
                    }
                } finally {
                    (guiRenderer as GuiRendererAccessor).skysoftSetRenderState(defaultRenderState)
                    skysoftSyncWindowScale(window)
                }
            }

            val aboveScreenRenderState = renderStates?.overlays() ?: GuiRenderState()
            skysoftRenderAboveScreenState(defaultRenderState, aboveScreenRenderState, window, renderStates != null)
        }
    }

    @Unique
    private fun skysoftRenderAboveScreenState(
        defaultRenderState: GuiRenderState,
        aboveScreenRenderState: GuiRenderState,
        window: Window,
        hasSeparatedInventory: Boolean,
    ) {
        val hasSkysoftOverlays = GuiOverlayRegistry.shouldRenderLayer(GuiOverlayLayer.ABOVE_SCREEN)
        if (!hasSeparatedInventory && !hasSkysoftOverlays) return

        if (hasSkysoftOverlays) {
            val mouseX = minecraft.mouseHandler.getScaledXPos(window).toInt()
            val mouseY = minecraft.mouseHandler.getScaledYPos(window).toInt()
            val overlayGraphics = GuiGraphicsExtractor(
                minecraft,
                aboveScreenRenderState,
                mouseX,
                mouseY,
            )
            GuiOverlayRegistry.renderLayer(GuiOverlayLayer.ABOVE_SCREEN, overlayGraphics)
        }

        try {
            (guiRenderer as GuiRendererAccessor).skysoftSetRenderState(aboveScreenRenderState)
            val fogBuffer: GpuBufferSlice = fogRenderer.getBuffer(FogRenderer.FogMode.NONE)
            guiRenderer.render(fogBuffer)
        } finally {
            (guiRenderer as GuiRendererAccessor).skysoftSetRenderState(defaultRenderState)
        }
    }

    @Unique
    private fun skysoftGetDefaultRenderState(): GuiRenderState = gameRenderState.guiRenderState

    @Unique
    private fun skysoftSyncWindowScale(window: Window) {
        gameRenderState.windowRenderState.guiScale = window.guiScale
    }
}
