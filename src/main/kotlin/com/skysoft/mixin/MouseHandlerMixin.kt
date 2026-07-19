package com.skysoft.mixin

import com.llamalad7.mixinextras.injector.wrapoperation.Operation
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation
import com.mojang.blaze3d.platform.Window
import com.skysoft.features.bazaar.BazaarTracker
import com.skysoft.features.inventory.StorageOverlayController
import com.skysoft.features.screenshot.ScreenshotCapturePreview
import com.skysoft.gui.scale.CursorController
import com.skysoft.gui.scale.GuiScaleController
import com.skysoft.gui.scale.InventoryCursorMemory
import com.skysoft.gui.tooltip.TooltipViewport
import com.skysoft.utils.MinecraftClient
import com.skysoft.utils.SkysoftErrorBoundary
import com.skysoft.utils.input.InputHandlingResult
import net.minecraft.client.MouseHandler
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.input.MouseButtonInfo
import org.objectweb.asm.Opcodes
import org.lwjgl.glfw.GLFW
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.Shadow
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable

@Mixin(MouseHandler::class)
open class MouseHandlerMixin : CursorController {
    @field:Shadow
    private var xpos = 0.0

    @field:Shadow
    private var ypos = 0.0

    override fun skysoftMoveCursor(cursorX: Double, cursorY: Double) {
        xpos = cursorX
        ypos = cursorY
    }

    @Inject(
        method = ["grabMouse"],
        at = [
            At(
                value = "FIELD",
                target = "Lnet/minecraft/client/MouseHandler;mouseGrabbed:Z",
                opcode = Opcodes.PUTFIELD,
            ),
        ],
    )
    protected fun skysoftSaveCursorBeforeGrab(ci: CallbackInfo) {
        SkysoftErrorBoundary.run("Inventory cursor mouse grab") { InventoryCursorMemory.beginMouseGrab(xpos, ypos) }
    }

    @Inject(
        method = ["grabMouse"],
        at = [
            At(
                value = "INVOKE",
                target = "Lcom/mojang/blaze3d/platform/InputConstants;grabOrReleaseMouse(" +
                    "Lcom/mojang/blaze3d/platform/Window;IDD)V",
            ),
        ],
    )
    protected fun skysoftSaveCursorAfterGrab(ci: CallbackInfo) {
        SkysoftErrorBoundary.run("Inventory cursor mouse grab") { InventoryCursorMemory.finishMouseGrab(xpos, ypos) }
    }

    @Inject(method = ["onButton"], at = [At("HEAD")], cancellable = true)
    protected fun skysoftProcessOverlayMouseControl(
        window: Long,
        buttonInfo: MouseButtonInfo,
        action: Int,
        ci: CallbackInfo,
    ) {
        val isConsumed = SkysoftErrorBoundary.value("Overlay mouse control", false) {
            action == GLFW.GLFW_PRESS && (
                ScreenshotCapturePreview.processMouseButtonPress(buttonInfo.button()) == InputHandlingResult.CONSUMED ||
                    BazaarTracker.handleMouseButtonPress(buttonInfo.button()) == InputHandlingResult.CONSUMED
                )
        }
        if (isConsumed) ci.cancel()
    }

    @WrapOperation(
        method = ["onScroll"],
        at = [
            At(
                value = "INVOKE",
                target = "Lnet/minecraft/client/gui/screens/Screen;mouseScrolled(DDDD)Z",
            ),
        ],
    )
    protected fun doesSkysoftHandleTooltipScroll(
        screen: Screen,
        mouseX: Double,
        mouseY: Double,
        horizontalAmount: Double,
        verticalAmount: Double,
        original: Operation<Boolean>,
    ): Boolean {
        val isHandled = SkysoftErrorBoundary.value("Tooltip mouse scrolling", false) {
            val container = screen as? AbstractContainerScreen<*>
            val overStorageScrollPanel = container != null &&
                StorageOverlayController.shouldPreferMouseScroll(container, mouseX, mouseY, verticalAmount)
            if (overStorageScrollPanel) {
                TooltipViewport.isStorageOverlayScrollKeyDown() &&
                    TooltipViewport.didHandleStorageMouseScroll(horizontalAmount, verticalAmount)
            } else {
                TooltipViewport.didHandleMouseScroll(horizontalAmount, verticalAmount)
            }
        }
        if (isHandled) return true
        return original.call(screen, mouseX, mouseY, horizontalAmount, verticalAmount)
    }

    @WrapOperation(
        method = ["releaseMouse"],
        at = [
            At(
                value = "INVOKE",
                target = "Lcom/mojang/blaze3d/platform/InputConstants;grabOrReleaseMouse(" +
                    "Lcom/mojang/blaze3d/platform/Window;IDD)V",
            ),
        ],
    )
    protected fun skysoftRestoreCursorOnRelease(
        window: Window,
        cursorMode: Int,
        initialCursorX: Double,
        initialCursorY: Double,
        original: Operation<Void>,
    ) {
        val restored = SkysoftErrorBoundary.value("Inventory cursor release", null) {
            InventoryCursorMemory.cursorForRelease(initialCursorX, initialCursorY)
        }
        val cursorX = restored?.x ?: initialCursorX
        val cursorY = restored?.y ?: initialCursorY
        if (restored != null) {
            xpos = cursorX
            ypos = cursorY
        }
        original.call(window, cursorMode, cursorX, cursorY)
    }

    private companion object {
        @JvmStatic
        @Inject(
            method = ["getScaledXPos(Lcom/mojang/blaze3d/platform/Window;D)D"],
            at = [At("HEAD")],
            cancellable = true,
        )
        private fun skysoftGetInventoryScaledX(
            window: Window,
            xPosition: Double,
            cir: CallbackInfoReturnable<Double>,
        ) {
            val scaledX = SkysoftErrorBoundary.value<Double?>("Inventory GUI scaled mouse X", null) {
                val screen = MinecraftClient.screen()
                if (
                    !GuiScaleController.usesSeparateInventoryScale(screen) ||
                    GuiScaleController.areOverlaysUsingNormalCoordinates()
                ) {
                    return@value null
                }
                GuiScaleController.useInventoryScale(screen, window).use {
                    xPosition * window.guiScaledWidth / window.screenWidth.toDouble()
                }
            }
            if (scaledX != null) cir.setReturnValue(scaledX)
        }

        @JvmStatic
        @Inject(
            method = ["getScaledYPos(Lcom/mojang/blaze3d/platform/Window;D)D"],
            at = [At("HEAD")],
            cancellable = true,
        )
        private fun skysoftGetInventoryScaledY(
            window: Window,
            yPosition: Double,
            cir: CallbackInfoReturnable<Double>,
        ) {
            val scaledY = SkysoftErrorBoundary.value<Double?>("Inventory GUI scaled mouse Y", null) {
                val screen = MinecraftClient.screen()
                if (
                    !GuiScaleController.usesSeparateInventoryScale(screen) ||
                    GuiScaleController.areOverlaysUsingNormalCoordinates()
                ) {
                    return@value null
                }
                GuiScaleController.useInventoryScale(screen, window).use {
                    yPosition * window.guiScaledHeight / window.screenHeight.toDouble()
                }
            }
            if (scaledY != null) cir.setReturnValue(scaledY)
        }
    }
}
