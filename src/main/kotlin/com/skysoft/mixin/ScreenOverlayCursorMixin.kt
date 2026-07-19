package com.skysoft.mixin

import com.llamalad7.mixinextras.injector.wrapoperation.Operation
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation
import com.skysoft.features.inventory.StorageOverlayController
import com.skysoft.gui.GuiOverlayLayer
import com.skysoft.gui.GuiOverlayRegistry
import com.skysoft.gui.scale.CursorController
import com.skysoft.gui.scale.InventoryCursorMemory
import com.skysoft.gui.scale.ScaledScreenState
import com.skysoft.gui.tooltip.TooltipViewport
import com.skysoft.utils.SkysoftErrorBoundary
import net.minecraft.client.KeyboardHandler
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.gui.screens.inventory.ContainerScreen
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.Unique
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

@Mixin(Screen::class)
open class ScreenOverlayCursorMixin : ScaledScreenState {
    private companion object {
        @JvmStatic
        @WrapOperation(
            method = ["defaultHandleClickEvent"],
            at = [
                At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/KeyboardHandler;setClipboard(Ljava/lang/String;)V",
                ),
            ],
        )
        private fun skysoftAcknowledgeErrorReportCopy(
            keyboardHandler: KeyboardHandler,
            value: String,
            original: Operation<Void>,
        ) {
            original.call(keyboardHandler, value)
            SkysoftErrorBoundary.acknowledgeClipboardCopy(value)
        }
    }

    @field:Unique
    private var skysoftInventoryGuiWidth = -1

    @field:Unique
    private var skysoftInventoryGuiHeight = -1

    override fun skysoftHasScaleDimensions(): Boolean =
        skysoftInventoryGuiWidth >= 0 && skysoftInventoryGuiHeight >= 0

    override fun skysoftMatchesScaleDimensions(width: Int, height: Int): Boolean =
        skysoftInventoryGuiWidth == width && skysoftInventoryGuiHeight == height

    override fun skysoftRememberScaleDimensions(width: Int, height: Int) {
        skysoftInventoryGuiWidth = width
        skysoftInventoryGuiHeight = height
    }

    override fun skysoftForgetScaleDimensions() {
        skysoftInventoryGuiWidth = -1
        skysoftInventoryGuiHeight = -1
    }

    @Inject(method = ["extractRenderState"], at = [At("HEAD")], cancellable = true)
    protected fun skysoftSuppressStorageOverlayWidgets(
        context: GuiGraphicsExtractor,
        mouseX: Int,
        mouseY: Int,
        delta: Float,
        ci: CallbackInfo,
    ) {
        val isActive = SkysoftErrorBoundary.value("Storage Overlay widget suppression", false) {
            isSkysoftStorageOverlayActive()
        }
        if (isActive) ci.cancel()
    }

    @Inject(method = ["removed"], at = [At("HEAD")])
    protected fun skysoftSaveCursorBeforeRemove(ci: CallbackInfo) {
        SkysoftErrorBoundary.run("Inventory cursor screen removal") {
            val minecraft = Minecraft.getInstance()
            TooltipViewport.clear()
            InventoryCursorMemory.rememberScreenCursor(
                this as Screen,
                minecraft.mouseHandler.xpos(),
                minecraft.mouseHandler.ypos(),
            )
        }
    }

    @Inject(
        method = ["extractRenderStateWithTooltipAndSubtitles"],
        at = [
            At(
                value = "INVOKE",
                target = "Lnet/minecraft/client/gui/screens/Screen;extractBackground(" +
                    "Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V",
                shift = At.Shift.AFTER,
            ),
        ],
    )
    protected fun skysoftRenderBelowScreenOverlays(
        context: GuiGraphicsExtractor,
        mouseX: Int,
        mouseY: Int,
        delta: Float,
        ci: CallbackInfo,
    ) {
        SkysoftErrorBoundary.run("Below-screen overlay rendering") {
            GuiOverlayRegistry.renderLayer(GuiOverlayLayer.BELOW_SCREEN, context)
        }
    }

    @Inject(
        method = ["extractRenderStateWithTooltipAndSubtitles"],
        at = [
            At(
                value = "INVOKE",
                target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;extractDeferredElements(IIF)V",
            ),
        ],
    )
    protected fun skysoftRenderStorageOverlayAboveScreenContents(
        context: GuiGraphicsExtractor,
        mouseX: Int,
        mouseY: Int,
        delta: Float,
        ci: CallbackInfo,
    ) {
        SkysoftErrorBoundary.run("Storage Overlay background rendering") {
            skysoftRenderStorageOverlay(context, mouseX, mouseY)
        }
    }

    @Inject(method = ["init(II)V"], at = [At("HEAD")])
    protected fun skysoftClearInventoryGuiSizeBeforeInit(width: Int, height: Int, ci: CallbackInfo) {
        skysoftForgetScaleDimensions()
    }

    @Inject(method = ["init(II)V"], at = [At("TAIL")])
    protected fun skysoftRestoreCursorAfterInit(width: Int, height: Int, ci: CallbackInfo) {
        SkysoftErrorBoundary.run("Inventory cursor screen initialization") {
            val minecraft = Minecraft.getInstance()
            InventoryCursorMemory.restoreWhenScreenInitializes(
                this as Screen,
                minecraft.window,
                minecraft.mouseHandler as CursorController,
            )
        }
    }

    @Inject(method = ["tick"], at = [At("HEAD")])
    protected fun skysoftRestoreCursorAfterDelayedRecenter(ci: CallbackInfo) {
        SkysoftErrorBoundary.run("Inventory cursor delayed restore") {
            val minecraft = Minecraft.getInstance()
            InventoryCursorMemory.continueRestore(
                this as Screen,
                minecraft.window,
                minecraft.mouseHandler as CursorController,
            )
        }
    }

    @Inject(method = ["resize"], at = [At("HEAD")])
    protected fun skysoftClearInventoryGuiSizeBeforeResize(ci: CallbackInfo) {
        skysoftForgetScaleDimensions()
    }

    @Unique
    private fun isSkysoftStorageOverlayActive(): Boolean {
        val screen = this as? AbstractContainerScreen<*> ?: return false
        return StorageOverlayController.isActive(screen)
    }

    @Unique
    private fun skysoftRenderStorageOverlay(context: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {
        val screen = this as? ContainerScreen ?: return
        if (StorageOverlayController.isActive(screen)) {
            context.nextStratum()
            StorageOverlayController.renderBackground(screen, context, mouseX, mouseY)
        }
    }
}
