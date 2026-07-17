package com.skysoft.gui

import com.skysoft.SkysoftMod
import com.skysoft.gui.scale.GuiScaleController
import com.skysoft.features.inventory.StorageOverlayController
import com.skysoft.utils.MinecraftClient
import com.skysoft.utils.SkysoftErrorBoundary
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.ChatScreen
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen

object GuiOverlayRegistry {
    private val overlays = mutableListOf<GuiOverlay>()

    fun register() {
        HudElementRegistry.attachElementAfter(
            VanillaHudElements.HOTBAR,
            SkysoftMod.id("gui_overlays_below_screen"),
            { context, _ ->
                SkysoftErrorBoundary.run("GUI overlay world layer") {
                    renderWorldLayer(GuiOverlayLayer.BELOW_SCREEN, context)
                }
            },
        )
    }

    fun register(overlay: GuiOverlay) {
        require(overlays.none { it.id == overlay.id }) { "Duplicate GUI overlay id: ${overlay.id}" }
        overlays += overlay
    }

    @JvmStatic
    fun shouldRenderLayer(layer: GuiOverlayLayer): Boolean {
        val overlayContext = context()
        return overlays.any { overlay ->
            overlay.layer == layer && SkysoftErrorBoundary.value(overlay.errorBoundary, false) {
                overlay.isVisible(overlayContext)
            }
        }
    }

    @JvmStatic
    fun renderLayer(layer: GuiOverlayLayer, context: GuiGraphicsExtractor) {
        val overlayContext = context()
        if (layer == GuiOverlayLayer.BELOW_SCREEN) {
            renderBelowScreenLayer(context, overlayContext)
        } else {
            renderLayer(layer, context, overlayContext)
        }
    }

    private fun renderWorldLayer(layer: GuiOverlayLayer, context: GuiGraphicsExtractor) {
        val overlayContext = context()
        if (overlayContext.type == GuiOverlayContextType.WORLD) renderLayer(layer, context, overlayContext)
    }

    private fun renderBelowScreenLayer(context: GuiGraphicsExtractor, overlayContext: GuiOverlayContext) {
        val screen = overlayContext.screen
        if (screen == null || !GuiScaleController.usesSeparateInventoryScale(screen)) {
            renderLayer(GuiOverlayLayer.BELOW_SCREEN, context, overlayContext)
            return
        }

        val window = Minecraft.getInstance().window
        val scales = GuiScaleController.resolve(screen, window)
        val inventoryScale = scales.inventory()
        val normalScale = scales.normal()
        val previousScale = window.guiScale
        context.pose().pushMatrix()
        window.guiScale = normalScale
        GuiScaleController.setOverlaysUseNormalCoordinates(true)
        context.pose().scale(normalScale / inventoryScale.toFloat(), normalScale / inventoryScale.toFloat())
        try {
            renderLayer(GuiOverlayLayer.BELOW_SCREEN, context, overlayContext)
        } finally {
            GuiScaleController.setOverlaysUseNormalCoordinates(false)
            window.guiScale = previousScale
            context.pose().popMatrix()
        }
    }

    private fun renderLayer(layer: GuiOverlayLayer, context: GuiGraphicsExtractor, overlayContext: GuiOverlayContext) {
        for (overlay in overlays) {
            if (overlay.layer != layer || !SkysoftErrorBoundary.value(overlay.errorBoundary, false) {
                    overlay.isVisible(overlayContext)
                }
            ) {
                continue
            }
            context.nextStratum()
            SkysoftErrorBoundary.run(overlay.errorBoundary) { overlay.render(context, overlayContext) }
        }
    }

    private fun context(): GuiOverlayContext {
        val minecraft = Minecraft.getInstance()
        val screen = MinecraftClient.screen(minecraft)
        return GuiOverlayContext(
            screen = screen,
            type = when {
                screen == null -> GuiOverlayContextType.WORLD
                screen is AbstractContainerScreen<*> && StorageOverlayController.isActive(screen) -> GuiOverlayContextType.STORAGE
                screen is AbstractContainerScreen<*> -> GuiOverlayContextType.INVENTORY
                screen is AbstractSignEditScreen -> GuiOverlayContextType.SIGN_INPUT
                screen is ChatScreen -> GuiOverlayContextType.CHAT
                else -> GuiOverlayContextType.SCREEN
            },
        )
    }
}

data class GuiOverlay(
    val id: String,
    val layer: GuiOverlayLayer,
    val contexts: Set<GuiOverlayContextType>,
    val visible: (GuiOverlayContext) -> Boolean = { true },
    val render: (GuiGraphicsExtractor, GuiOverlayContext) -> Unit,
) {
    internal val errorBoundary = "GUI overlay $id"

    fun isVisible(context: GuiOverlayContext): Boolean = context.type in contexts && visible(context)
}

data class GuiOverlayContext(
    val screen: Screen?,
    val type: GuiOverlayContextType,
)

enum class GuiOverlayLayer {
    BELOW_SCREEN,
    ABOVE_SCREEN,
}

enum class GuiOverlayContextType {
    WORLD,
    INVENTORY,
    STORAGE,
    SIGN_INPUT,
    CHAT,
    SCREEN,
}
