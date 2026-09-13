package com.skysoft.features.waypoints

import com.skysoft.config.SkysoftConfigGui
import com.skysoft.features.inventory.InventoryOverlayInput
import com.skysoft.gui.OverlayControlMouse.normalPointFromScreen
import com.skysoft.mixin.AbstractContainerScreenAccessor
import com.skysoft.utils.MinecraftClient
import com.skysoft.utils.SkysoftScreenEvents
import com.skysoft.utils.gui.OverlayListScroll
import com.skysoft.utils.gui.Rect
import com.skysoft.utils.input.InputHandlingResult
import com.skysoft.utils.input.InputUtilities
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.screens.ChatScreen
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.MouseButtonEvent
import org.lwjgl.glfw.GLFW

internal object WaypointPanelInput {
    private var dragOffset: Pair<Int, Int>? = null
    private var dragScreen: Screen? = null

    fun register() {
        InventoryOverlayInput.registerCoverageProvider("Waypoint panel coverage", WaypointPanel::isVisible) { _, x, y ->
            isScreenPointCovered(x.toInt(), y.toInt())
        }
        InventoryOverlayInput.registerClickHandler("Waypoint panel click", WaypointPanel::isVisible) { _, click ->
            if (didClick(click)) InputHandlingResult.CONSUMED else InputHandlingResult.IGNORED
        }
        InventoryOverlayInput.registerScrollHandler("Waypoint panel scroll", WaypointPanel::isVisible) { _, x, y, amount ->
            if (didScroll(x, y, amount)) InputHandlingResult.CONSUMED else InputHandlingResult.IGNORED
        }
        SkysoftScreenEvents.onBeforeInit("Waypoint panel input") { _, screen ->
            if (screen is ChatScreen) {
                ScreenMouseEvents.allowMouseClick(screen).register { _, click ->
                    !didClick(click)
                }
                ScreenMouseEvents.allowMouseScroll(screen).register { _, x, y, _, amount -> !didScroll(x, y, amount) }
            }
            if (screen is ChatScreen || screen is AbstractContainerScreen<*>) {
                ScreenMouseEvents.allowMouseDrag(screen).register { _, click, _, _ -> !didDrag(click) }
                ScreenMouseEvents.allowMouseRelease(screen).register { _, click -> !didRelease(click) }
            }
            if (screen is ChatScreen || screen is AbstractContainerScreen<*> || screen is WaypointPanelScreen || screen is WaypointDialog) {
                ScreenKeyboardEvents.allowKeyPress(screen).register { _, event ->
                    !WaypointSettingsPanel.didPressKey(event.key()) && !WaypointPresetList.didPressKey(event)
                }
                ScreenKeyboardEvents.allowKeyRelease(screen).register { _, event -> !WaypointSettingsPanel.didReleaseBinding(event.key()) }
            }
        }
    }

    @JvmStatic
    fun didType(event: CharacterEvent): Boolean =
        WaypointSettingsPanel.shouldCaptureTyping() || WaypointPresetList.didType(event)

    fun isPointerTarget(x: Int, y: Int): Boolean {
        if (!WaypointPanel.isVisible()) return false
        val (normalX, normalY) = normalPointFromScreen(x, y)
        return WaypointPanel.containsPoint(normalX, normalY) ||
            (!isWorldPointCovered(x, y) && WaypointWorldSelection.containsPoint(normalX, normalY))
    }

    private fun isScreenPointCovered(x: Int, y: Int): Boolean {
        if (!WaypointPanel.isVisible()) return false
        val (normalX, normalY) = normalPointFromScreen(x, y)
        return WaypointPanel.containsPoint(normalX, normalY)
    }

    fun isWorldPointCovered(x: Int, y: Int): Boolean {
        val screen = MinecraftClient.screen() ?: return false
        if (screen.children().filterIsInstance<AbstractWidget>().any { widget ->
                widget.visible && widget.isMouseOver(x.toDouble(), y.toDouble())
            }
        ) return true
        if (screen !is AbstractContainerScreen<*>) return false
        val access = screen as AbstractContainerScreenAccessor
        val inventory = Rect(
            access.skysoftGetLeftPos(), access.skysoftGetTopPos(), access.skysoftGetImageWidth(), access.skysoftGetImageHeight()
        )
        return inventory.contains(x, y) || InventoryOverlayInput.isPointCovered(screen, x.toDouble(), y.toDouble())
    }

    fun didClick(click: MouseButtonEvent): Boolean {
        val (x, y) = normalPointFromScreen(click.x().toInt(), click.y().toInt())
        return didClick(click, x, y)
    }

    fun didClick(click: MouseButtonEvent, x: Int, y: Int): Boolean {
        if (!WaypointPanel.isVisible()) return false
        if (!WaypointPanel.isInteractive) return WaypointPanel.containsPoint(x, y)
        if (WaypointSettingsPanel.didCaptureMouse(click.button())) return true
        if (WaypointPresetList.didClick(click, x, y)) return true
        val layout = WaypointPanel.layout
        val control = WaypointPanel.controls.lastOrNull { it.bounds.contains(x, y) }
        if (click.button() !in EDITOR_BUTTONS) return WaypointPanel.containsPoint(x, y)
        val pendingDelete = WaypointPanel.pendingDelete
        val settings = WaypointSettingsPanel.layout
        when {
            control != null -> {
                WaypointPanel.activate(control, rightClick = click.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT)
                if (control.enabled && control.pointId != null && click.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                    WaypointPointDrag.begin(control.pointId, y)
                    dragScreen = MinecraftClient.screen()
                }
            }
            layout != null && layout.header.contains(x, y) && click.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT -> {
                WaypointSettingsPanel.close()
                dragOffset = (x - layout.bounds.x) to (y - layout.bounds.y)
                dragScreen = MinecraftClient.screen()
            }
            !WaypointPanel.containsPoint(x, y) -> {
                WaypointSettingsPanel.close()
                WaypointPanel.pendingDelete = null
                if (isWorldPointCovered(click.x().toInt(), click.y().toInt())) return false
                val selected = WaypointWorldSelection.didSelectAt(x, y)
                if (selected && click.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT) WaypointPointScreen.open()
                return selected
            }
        }
        if (settings != null && !settings.bounds.contains(x, y) && WaypointSettingsPanel.layout === settings) {
            WaypointSettingsPanel.close()
        }
        if (WaypointPanel.pendingDelete == pendingDelete) WaypointPanel.pendingDelete = null
        return true
    }

    fun didDrag(click: MouseButtonEvent): Boolean {
        if (click.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) return false
        val (x, y) = normalPointFromScreen(click.x().toInt(), click.y().toInt())
        if (WaypointSettingsPanel.didDrag(x)) return true
        if (dragScreen !== MinecraftClient.screen()) return false
        if (WaypointPointDrag.didDrag(y)) return true
        val offset = dragOffset ?: return false
        Waypoints.config.editorX = (x - offset.first).coerceAtLeast(0)
        Waypoints.config.editorY = (y - offset.second).coerceAtLeast(0)
        return true
    }

    fun didRelease(click: MouseButtonEvent): Boolean {
        if (WaypointSettingsPanel.didReleaseBinding(click.button())) return true
        if (click.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) return false
        if (WaypointSettingsPanel.didFinishDrag()) return true
        if (WaypointPointDrag.didFinish()) return true
        if (dragOffset == null) return false
        cancelDrag()
        return true
    }

    fun cancelDrag() {
        val wasMovingPanel = dragOffset != null
        dragOffset = null
        dragScreen = null
        WaypointPointDrag.cancel()
        if (wasMovingPanel) SkysoftConfigGui.config().saveNow()
    }

    fun releaseIfNeeded() {
        if (dragScreen !== MinecraftClient.screen() || !WaypointPanel.isVisible()) {
            cancelDrag()
        } else if (!InputUtilities.isBindingDown(GLFW.GLFW_MOUSE_BUTTON_LEFT)) {
            WaypointPointDrag.didFinish()
            cancelDrag()
        } else {
            WaypointPointDrag.tick()
        }
    }

    fun didScroll(x: Double, y: Double, amount: Double): Boolean {
        if (!WaypointPanel.isVisible() || amount == 0.0) return false
        val (normalX, normalY) = normalPointFromScreen(x.toInt(), y.toInt())
        if (WaypointSettingsPanel.didScroll(normalX, normalY, amount)) return true
        val list = WaypointPanel.layout?.list ?: return false
        if (list.contains(normalX, normalY)) {
            val maximum = (WaypointPanel.itemCount - (WaypointPanel.layout?.visibleRows ?: 0)).coerceAtLeast(0)
            WaypointPanel.scrollOffset = OverlayListScroll.nextOffset(WaypointPanel.scrollOffset, amount, maximum)
            WaypointPointDrag.tick()
            return true
        }
        return WaypointPanel.containsPoint(normalX, normalY)
    }

    private val EDITOR_BUTTONS = setOf(GLFW.GLFW_MOUSE_BUTTON_LEFT, GLFW.GLFW_MOUSE_BUTTON_RIGHT)
}
