package com.skysoft.features.waypoints

import com.skysoft.config.SkysoftConfigGui
import com.skysoft.gui.GuiOverlay
import com.skysoft.gui.GuiOverlayContextType
import com.skysoft.gui.GuiOverlayLayer
import com.skysoft.gui.GuiOverlayRegistry
import com.skysoft.gui.OverlayControlMouse
import com.skysoft.gui.tooltip.SkysoftNativeTooltip
import com.skysoft.utils.MinecraftClient
import com.skysoft.utils.SkysoftChat
import com.skysoft.utils.SoundUtilities
import com.skysoft.utils.animation.PanelFadeTransition
import com.skysoft.utils.gui.Rect
import com.skysoft.utils.input.InputUtilities
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.ChatScreen
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen

internal object WaypointPanel {
    private val transition = PanelFadeTransition()
    val opacity: Double get() = transition.opacity()
    val isInteractive: Boolean get() = transition.isInteractive
    var isOpen = false
        private set
    var pendingDelete: String? = null
    var scrollOffset = 0
    private var reportedSaveError: String? = null
    var layout: WaypointPanelLayout? = null
        private set
    var controls: List<WaypointPanelControl> = emptyList()
        private set
    private var previousSelection = WaypointSelection()
    var isBrowsingPresets = true
        private set
    val itemCount: Int get() = if (isBrowsingPresets) WaypointPresetList.groups.size else Waypoints.group?.points?.size ?: 0
    val isEditing: Boolean get() = isOpen && !isBrowsingPresets && Waypoints.config.enabled && Waypoints.island != null
    val canSelectWorld: Boolean get() = isEditing && isVisible()

    fun register() {
        GuiOverlayRegistry.register(
            GuiOverlay(
                id = "waypoint_editor",
                layer = GuiOverlayLayer.ABOVE_SCREEN,
                contexts = GuiOverlayContextType.entries.toSet(),
                visible = { isVisible(it.screen) },
                coversScreenPoint = WaypointPanelInput::isPointerTarget,
                render = { context, _ -> render(context) },
            ),
        )
        WaypointPanelInput.register()
    }

    fun open() {
        isOpen = true
        transition.show()
        showPage(presets = true)
        WaypointPlacement.captureAim()
        if (Waypoints.browseIsland == null) Waypoints.browseIsland = Waypoints.island
        if (Waypoints.group == null) {
            WaypointLibrary.groups.firstOrNull { Waypoints.isInContext(it) }?.let { Waypoints.select(it) }
        }
        if (MinecraftClient.screen() !is WaypointPanelScreen) MinecraftClient.setScreen(WaypointPanelScreen())
    }

    fun showPage(presets: Boolean) {
        WaypointSettingsPanel.clear()
        WaypointPanelInput.cancelDrag()
        WaypointPresetList.blur()
        isBrowsingPresets = presets
        scrollOffset = 0
        controls = emptyList()
        layout = null
        pendingDelete = null
    }

    fun dismiss() {
        transition.hide()
        WaypointSettingsPanel.close()
    }

    fun close() {
        WaypointSettingsPanel.clear()
        pendingDelete = null
        isOpen = false
        transition.reset()
        controls = emptyList()
        layout = null
        WaypointPanelInput.cancelDrag()
        WaypointLibrary.save()
        WaypointPresetList.reset()
        if (MinecraftClient.screen() is WaypointPanelScreen || MinecraftClient.screen() is WaypointDialog) MinecraftClient.setScreen(null)
    }

    fun isVisible(screen: Screen? = MinecraftClient.screen()): Boolean = isOpen && transition.isVisible &&
        !MinecraftClient.isGuiHidden(Minecraft.getInstance()) &&
        (
            screen !is WaypointDialog ||
                WaypointPanelLayout.canFitBeside(Minecraft.getInstance().window.guiScaledWidth, screen.preferredWidth)
            ) &&
        (
            screen == null || screen is WaypointPanelScreen || screen is WaypointDialog ||
                screen is ChatScreen || screen is AbstractContainerScreen<*>
            )

    private fun interactionScreen(): Screen? {
        var screen = MinecraftClient.screen()
        while (screen is WaypointDialog) screen = screen.parentScreen
        return screen
    }

    val showFooter: Boolean get() = interactionScreen().let {
        it is ChatScreen || it is AbstractContainerScreen<*> || it is WaypointPanelScreen
    }

    fun menuParent(): Screen? {
        WaypointSettingsPanel.close()
        return interactionScreen()
    }

    fun tick() {
        transition.opacity()
        if (isOpen && !transition.isVisible) {
            close()
            return
        }
        val saveError = WaypointLibrary.saveError
        if (saveError != reportedSaveError) {
            saveError?.let(::notify)
            reportedSaveError = saveError
        }
        WaypointSettingsPanel.tick()
        WaypointPresetList.tick()
        if (!isBrowsingPresets && previousSelection != Waypoints.selection) {
            val group = Waypoints.group
            val selected = group?.points?.indexOfFirst { it.id == Waypoints.selection.pointId } ?: -1
            if (previousSelection.groupId != Waypoints.selection.groupId) scrollOffset = 0
            val rows = layout?.visibleRows ?: 1
            if (selected >= 0 && selected !in scrollOffset until scrollOffset + rows) scrollOffset = selected
        }
        previousSelection = Waypoints.selection
        WaypointPanelInput.releaseIfNeeded()
    }

    fun perform(action: () -> Unit) {
        try {
            action()
        } catch (failure: IllegalArgumentException) {
            notify(failure.message ?: "The waypoint data is invalid.")
        } catch (failure: IllegalStateException) {
            notify(failure.message ?: "This waypoint action is unavailable.")
        }
    }

    fun notify(text: String) {
        SkysoftChat.chat("Waypoints: $text")
    }

    fun enable() {
        Waypoints.config.enabled = true
        SkysoftConfigGui.config().saveNow()
        WaypointPlacement.captureAim()
    }

    private fun render(context: GuiGraphicsExtractor) {
        val minecraft = Minecraft.getInstance()
        val window = minecraft.window
        val current = WaypointPanelLayout.create(window.guiScaledWidth, window.guiScaledHeight)
        val settings = WaypointSettingsPanel.updateLayout(current, window.guiScaledWidth, window.guiScaledHeight)
        layout = current.takeIf { settings == null || current.bounds.intersection(settings.bounds) == null }
        scrollOffset = scrollOffset.coerceIn(0, (itemCount - current.visibleRows).coerceAtLeast(0))
        val mouse = InputUtilities.scaledMousePosition(minecraft)
        val (x, y) = OverlayControlMouse.normalPoint(mouse.x, mouse.y)
        val interactive = MinecraftClient.screen() != null
        val mouseX = if (interactive) x else -1
        val mouseY = if (interactive) y else -1
        controls = if (layout != null) WaypointPanelRenderer.render(context, current, mouseX, mouseY) else emptyList()
        if (settings != null) {
            context.nextStratum()
            controls += WaypointSettingsRenderer.render(context, settings, mouseX, mouseY)
        }
        if (interactive && (MinecraftClient.screen() !is WaypointDialog || WaypointSettingsPanel.isOpen)) {
            val control = controls.lastOrNull { it.bounds.contains(x, y) }
            if (control != null && control.tooltip.isNotEmpty()) {
                SkysoftNativeTooltip.setForNextFrame(context, control.tooltip, x, y, scrollable = false)
            }
        }
    }

    fun containsPoint(x: Int, y: Int): Boolean =
        layout?.bounds?.contains(x, y) == true || WaypointSettingsPanel.layout?.bounds?.contains(x, y) == true

    internal fun activate(control: WaypointPanelControl, rightClick: Boolean = false) {
        if (!control.enabled) return
        val action = if (rightClick) control.rightClick ?: return else control.action
        SoundUtilities.playClickSound()
        perform(action)
    }

    fun deletePoint(group: WaypointGroup, point: WaypointPoint) {
        Waypoints.select(group, point)
        if (pendingDelete == point.id || Minecraft.getInstance().hasShiftDown()) {
            WaypointEditing.removePoint()
            pendingDelete = null
        } else {
            pendingDelete = point.id
        }
    }
}

internal data class WaypointPanelControl(
    val bounds: Rect,
    val enabled: Boolean = true,
    val tooltip: List<String> = emptyList(),
    val pointId: String? = null,
    val rightClick: (() -> Unit)? = null,
    val action: () -> Unit,
)

internal fun waypointTooltip(description: String): List<String> =
    description.split('\n').filter(String::isNotEmpty).map { "§7$it" }
