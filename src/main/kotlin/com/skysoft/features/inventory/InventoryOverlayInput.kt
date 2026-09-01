package com.skysoft.features.inventory

import com.skysoft.utils.ActiveListenerRegistry
import com.skysoft.utils.SkysoftScreenEvents
import com.skysoft.utils.input.InputHandlingResult
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.input.MouseButtonEvent

object InventoryOverlayInput {
    private val clickHandlers =
        ActiveListenerRegistry<(AbstractContainerScreen<*>, MouseButtonEvent) -> InputHandlingResult>()
    private val scrollHandlers =
        ActiveListenerRegistry<(AbstractContainerScreen<*>, Double, Double, Double) -> InputHandlingResult>()
    private val coverageProviders =
        ActiveListenerRegistry<(AbstractContainerScreen<*>, Double, Double) -> Boolean>()
    private var registered = false

    internal fun registerClickObserver(
        boundary: String,
        isActive: () -> Boolean,
        observer: (AbstractContainerScreen<*>, MouseButtonEvent) -> Unit,
    ) {
        register()
        clickHandlers.register(boundary, isActive) { screen, click ->
            observer(screen, click)
            InputHandlingResult.IGNORED
        }
    }

    internal fun registerClickHandler(
        boundary: String,
        isActive: () -> Boolean,
        handler: (AbstractContainerScreen<*>, MouseButtonEvent) -> InputHandlingResult,
    ) {
        register()
        clickHandlers.register(boundary, isActive, handler)
    }

    internal fun registerScrollHandler(
        boundary: String,
        isActive: () -> Boolean,
        handler: (AbstractContainerScreen<*>, Double, Double, Double) -> InputHandlingResult,
    ) {
        register()
        scrollHandlers.register(boundary, isActive, handler)
    }

    internal fun registerCoverageProvider(
        boundary: String,
        isActive: () -> Boolean,
        isCovered: (AbstractContainerScreen<*>, Double, Double) -> Boolean,
    ) {
        coverageProviders.register(boundary, isActive, isCovered)
    }

    @JvmStatic
    fun isPointCovered(
        screen: AbstractContainerScreen<*>,
        mouseX: Double,
        mouseY: Double,
    ): Boolean = coverageProviders.anyActive { isCovered ->
        isCovered(screen, mouseX, mouseY)
    }

    private fun register() {
        if (registered) return
        registered = true
        SkysoftScreenEvents.onBeforeInit("Inventory overlay input setup") { _, screen ->
            if (screen !is AbstractContainerScreen<*>) return@onBeforeInit
            ScreenMouseEvents.allowMouseClick(screen).register { _, click ->
                !clickHandlers.anyActive { handler ->
                    handler(screen, click) == InputHandlingResult.CONSUMED
                }
            }
            ScreenMouseEvents.allowMouseScroll(screen).register { _, mouseX, mouseY, _, verticalAmount ->
                !scrollHandlers.anyActive { handler ->
                    handler(screen, mouseX, mouseY, verticalAmount) == InputHandlingResult.CONSUMED
                }
            }
        }
    }
}
