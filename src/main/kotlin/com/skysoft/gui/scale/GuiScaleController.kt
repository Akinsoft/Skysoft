package com.skysoft.gui.scale

import com.mojang.blaze3d.platform.Window
import com.skysoft.config.InventoryScreenConfig
import com.skysoft.config.SkysoftConfigGui
import com.skysoft.features.inventory.StorageOverlayController
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.renderer.state.gui.GuiRenderState

class GuiScaleController private constructor() {
    data class ResolvedScales(
        private val normalValue: Int,
        private val inventoryValue: Int,
        private val tooltipValue: Int,
    ) {
        fun normal(): Int = normalValue

        fun inventory(): Int = inventoryValue

        fun tooltip(): Int = tooltipValue
    }

    data class RenderBatch(
        private val inventoryValue: GuiRenderState,
        private val overlaysValue: GuiRenderState,
    ) {
        fun inventory(): GuiRenderState = inventoryValue

        fun overlays(): GuiRenderState = overlaysValue
    }

    class WindowScaleOverride private constructor(
        private val window: Window,
        requestedScale: Int,
    ) : AutoCloseable {
        private val previousScale = window.guiScale
        private var isClosed = false

        init {
            window.guiScale = requestedScale
        }

        override fun close() {
            check(!isClosed) { "GUI scale override closed twice" }
            isClosed = true
            window.guiScale = previousScale
        }

        internal companion object {
            fun create(window: Window, requestedScale: Int) = WindowScaleOverride(window, requestedScale)
        }
    }

    companion object {
        private val minecraft = Minecraft.getInstance()
        private var pendingRenderBatch: RenderBatch? = null
        private var overlaysUseNormalCoordinates = false

        @JvmStatic
        fun usesSeparateInventoryScale(screen: Screen?): Boolean =
            supportsInventoryScale(screen) &&
                (config().separateInventoryGuiScale || shouldCapStorageOverlayScale(screen))

        @JvmStatic
        fun usesSeparateTooltipScale(screen: Screen?): Boolean =
            config().separateTooltipGuiScale && supportsInventoryScale(screen)

        @JvmStatic
        fun hasDistinctInventoryScale(screen: Screen?, window: Window): Boolean {
            if (!usesSeparateInventoryScale(screen)) return false
            val scales = resolve(screen, window)
            return scales.normal() != scales.inventory()
        }

        @JvmStatic
        fun resolve(screen: Screen?, window: Window): ResolvedScales {
            val config = config()
            val normal = resolve(window, minecraft.options.guiScale().get())
            val tooltip = resolve(window, config.settings.tooltipGuiScale)
            val configuredInventory = if (config.separateInventoryGuiScale) {
                resolve(window, config.settings.inventoryGuiScale)
            } else {
                normal
            }
            val inventory = capStorageOverlayScale(
                minOf(configuredInventory, inventoryScaleLimit(screen)),
                StorageOverlayController.isActive(screen as? AbstractContainerScreen<*>),
            )
            return ResolvedScales(normal, inventory.coerceAtLeast(1), tooltip)
        }

        @JvmStatic
        fun useInventoryScale(screen: Screen?, window: Window): WindowScaleOverride =
            WindowScaleOverride.create(window, resolve(screen, window).inventory())

        @JvmStatic
        fun useTooltipScale(screen: Screen?, window: Window): WindowScaleOverride =
            WindowScaleOverride.create(window, resolve(screen, window).tooltip())

        @JvmStatic
        fun convertCoordinate(coordinate: Int, sourceScale: Int, targetScale: Int): Int =
            Math.round(coordinate * sourceScale.coerceAtLeast(1) / targetScale.coerceAtLeast(1).toFloat())

        @JvmStatic
        fun updateScreenDimensions(screen: Screen, window: Window) {
            val state = screen as ScaledScreenState
            val width = window.guiScaledWidth
            val height = window.guiScaledHeight
            if (!state.skysoftMatchesScaleDimensions(width, height)) {
                screen.resize(width, height)
                state.skysoftRememberScaleDimensions(width, height)
            }
        }

        @JvmStatic
        fun restoreScreenDimensions(screen: Screen, window: Window) {
            val state = screen as ScaledScreenState
            if (!state.skysoftHasScaleDimensions()) return
            val width = window.guiScaledWidth
            val height = window.guiScaledHeight
            if (!state.skysoftMatchesScaleDimensions(width, height)) screen.resize(width, height)
            state.skysoftForgetScaleDimensions()
        }

        @JvmStatic
        fun submitRenderBatch(inventory: GuiRenderState, overlays: GuiRenderState) {
            check(pendingRenderBatch == null) { "Inventory GUI render batch was not consumed" }
            pendingRenderBatch = RenderBatch(inventory, overlays)
        }

        @JvmStatic
        fun takeRenderBatch(): RenderBatch? = pendingRenderBatch.also { pendingRenderBatch = null }

        @JvmStatic
        fun areOverlaysUsingNormalCoordinates(): Boolean = overlaysUseNormalCoordinates

        @JvmStatic
        fun setOverlaysUseNormalCoordinates(value: Boolean) {
            overlaysUseNormalCoordinates = value
        }

        private fun resolve(window: Window, configuredScale: Int): Int =
            window.calculateScale(configuredScale.coerceAtLeast(0), minecraft.isEnforceUnicode).coerceAtLeast(1)

        private fun supportsInventoryScale(screen: Screen?): Boolean =
            screen is AbstractContainerScreen<*> || screen is InventoryScaledScreen && screen.usesInventoryScale()

        private fun inventoryScaleLimit(screen: Screen?): Int =
            if (screen is InventoryScaledScreen && screen.usesInventoryScale()) {
                screen.inventoryScaleLimit().coerceAtLeast(1)
            } else {
                Int.MAX_VALUE
            }

        private fun shouldCapStorageOverlayScale(screen: Screen?): Boolean {
            val normalScale = resolve(minecraft.window, minecraft.options.guiScale().get())
            return capStorageOverlayScale(
                normalScale,
                StorageOverlayController.isActive(screen as? AbstractContainerScreen<*>),
            ) != normalScale
        }

        private fun config(): InventoryScreenConfig = SkysoftConfigGui.config().gui.inventoryScreen
    }
}

internal fun capStorageOverlayScale(scale: Int, isStorageOverlayActive: Boolean): Int =
    if (isStorageOverlayActive) minOf(scale, STORAGE_OVERLAY_MAX_GUI_SCALE) else scale

private const val STORAGE_OVERLAY_MAX_GUI_SCALE = 4
