package com.skysoft.integration

import com.mojang.blaze3d.platform.Window
import com.skysoft.features.bazaar.BazaarTracker
import com.skysoft.features.inventory.StorageOverlayController
import com.skysoft.features.misc.MouseLock
import com.skysoft.features.misc.Zoom
import com.skysoft.features.screenshot.ScreenshotCapturePreview
import com.skysoft.gui.scale.GuiScaleController
import com.skysoft.gui.scale.InventoryCursorMemory
import com.skysoft.gui.tooltip.TooltipScrollPriorityScreen
import com.skysoft.gui.tooltip.TooltipViewport
import com.skysoft.utils.MinecraftClient
import com.skysoft.utils.SkysoftErrorBoundary
import com.skysoft.utils.input.InputHandlingResult
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import org.lwjgl.glfw.GLFW

object MouseInputHooks {
    @JvmStatic
    fun beginMouseGrab(window: Window) {
        SkysoftErrorBoundary.run("Inventory cursor mouse grab") {
            InventoryCursorMemory.beginMouseGrab(window)
        }
    }

    @JvmStatic
    fun cursorAfterInput(): InventoryCursorMemory.CursorPoint? =
        SkysoftErrorBoundary.value("Inventory cursor restoration", null) {
            InventoryCursorMemory.cursorAfterInput(MinecraftClient.screen())
        }

    @JvmStatic
    fun applyMovement(amount: Double): Double {
        val locked = SkysoftErrorBoundary.value("Mouse Lock movement", amount) { MouseLock.apply(amount) }
        return SkysoftErrorBoundary.value("Zoom mouse movement", locked) { Zoom.applyMouseMovement(locked) }
    }

    @JvmStatic
    fun shouldConsumeScroll(verticalAmount: Double): Boolean =
        SkysoftErrorBoundary.value("Zoom mouse scrolling", false) {
            Zoom.didHandleScroll(verticalAmount)
        }

    @JvmStatic
    fun shouldConsumeButton(button: Int, action: Int): Boolean {
        if (action != GLFW.GLFW_PRESS) return false
        return didConsumeButton("Screenshot Capture Preview mouse control") {
            ScreenshotCapturePreview.processMouseButtonPress(button)
        } || didConsumeButton("Bazaar Tracker mouse control") {
            BazaarTracker.handleMouseButtonPress(button)
        }
    }

    @JvmStatic
    fun didHandleTooltipScroll(
        screen: Screen,
        mouseX: Double,
        mouseY: Double,
        horizontalAmount: Double,
        verticalAmount: Double,
    ): Boolean = SkysoftErrorBoundary.value("Tooltip mouse scrolling", false) {
        val overStorage = screen is AbstractContainerScreen<*> &&
            StorageOverlayController.shouldPreferMouseScroll(screen, mouseX, mouseY, verticalAmount)
        val screenPrioritizesScroll = verticalAmount != 0.0 &&
            screen is TooltipScrollPriorityScreen &&
            screen.mouseScrollPriorityAreas.any { area -> area.contains(mouseX.toInt(), mouseY.toInt()) }
        if (!overStorage && !screenPrioritizesScroll) {
            TooltipViewport.didHandleMouseScroll(horizontalAmount, verticalAmount)
        } else {
            TooltipViewport.isCompetingScrollKeyDown() &&
                TooltipViewport.didHandleCompetingMouseScroll(horizontalAmount, verticalAmount)
        }
    }

    @JvmStatic
    fun inventoryScaledX(window: Window, xPosition: Double): Double? =
        inventoryScaledCoordinate(window, xPosition, isXAxis = true)

    @JvmStatic
    fun inventoryScaledY(window: Window, yPosition: Double): Double? =
        inventoryScaledCoordinate(window, yPosition, isXAxis = false)

    private fun didConsumeButton(boundary: String, action: () -> InputHandlingResult): Boolean =
        SkysoftErrorBoundary.value(boundary, false) { action() == InputHandlingResult.CONSUMED }

    private fun inventoryScaledCoordinate(window: Window, position: Double, isXAxis: Boolean): Double? =
        SkysoftErrorBoundary.value(
            if (isXAxis) "Inventory GUI scaled mouse X" else "Inventory GUI scaled mouse Y",
            null,
        ) {
            val screen = MinecraftClient.screen()
            if (
                !GuiScaleController.usesSeparateInventoryScale(screen) ||
                GuiScaleController.areOverlaysUsingNormalCoordinates()
            ) {
                return@value null
            }
            GuiScaleController.useInventoryScale(screen, window).use {
                if (isXAxis) {
                    position * window.guiScaledWidth / window.screenWidth.toDouble()
                } else {
                    position * window.guiScaledHeight / window.screenHeight.toDouble()
                }
            }
        }
}
