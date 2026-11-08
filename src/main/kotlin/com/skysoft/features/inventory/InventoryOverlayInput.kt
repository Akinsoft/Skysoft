package com.skysoft.features.inventory

import com.mojang.blaze3d.platform.InputConstants
import com.skysoft.data.skyblock.SkyBlockItemId.skyBlockId
import com.skysoft.gui.OverlayControlMouse
import com.skysoft.mixin.AbstractContainerScreenAccessor
import com.skysoft.utils.ActiveListenerRegistry
import com.skysoft.utils.SkysoftScreenEvents
import com.skysoft.utils.gui.Point
import com.skysoft.utils.input.InputHandlingResult
import com.skysoft.utils.input.InputUtilities
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents
import net.minecraft.client.Minecraft
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

    internal fun registerTrackerInput(
        boundary: String,
        isActive: () -> Boolean,
        shouldAllowClick: (AbstractContainerScreen<*>, MouseButtonEvent) -> Boolean,
        wasScrollHandled: (Double) -> Boolean,
    ) {
        registerClickHandler("$boundary mouse click", isActive) { screen, click ->
            if (shouldAllowClick(screen, click)) InputHandlingResult.IGNORED else InputHandlingResult.CONSUMED
        }
        registerScrollHandler("$boundary mouse scroll", isActive) { screen, mouseX, mouseY, verticalAmount ->
            val allowed = isPointCovered(screen, mouseX, mouseY) || !wasScrollHandled(verticalAmount)
            if (allowed) InputHandlingResult.IGNORED else InputHandlingResult.CONSUMED
        }
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

    internal fun pointer(screen: AbstractContainerScreen<*>?): InventoryOverlayPointer {
        val mouse = InputUtilities.scaledMousePosition(Minecraft.getInstance())
        val (normalX, normalY) = OverlayControlMouse.normalPoint(mouse.x, mouse.y)
        val (screenX, screenY) = OverlayControlMouse.screenPoint(mouse.x, mouse.y)
        return InventoryOverlayPointer(
            mouse = mouse,
            normal = Point(normalX, normalY),
            screen = Point(screenX, screenY),
            isInteractive = screen != null && !isPointCovered(screen, screenX.toDouble(), screenY.toDouble()),
        )
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

internal inline fun wasLeftClickHandled(button: Int, action: () -> Unit): Boolean {
    if (button != InputConstants.MOUSE_BUTTON_LEFT) return false
    action()
    return true
}

internal fun hoveredPlayerInventoryItemId(screen: AbstractContainerScreen<*>): String? {
    val player = Minecraft.getInstance().player ?: return null
    val slot = (screen as AbstractContainerScreenAccessor).skysoftGetHoveredSlot()
    return slot?.takeIf { it.container === player.inventory }?.item?.skyBlockId()
}

internal data class InventoryOverlayPointer(
    val mouse: Point,
    val normal: Point,
    val screen: Point,
    val isInteractive: Boolean,
)
