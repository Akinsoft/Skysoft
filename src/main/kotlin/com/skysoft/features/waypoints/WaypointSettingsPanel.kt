package com.skysoft.features.waypoints

import com.skysoft.config.SkysoftConfigGui
import com.skysoft.utils.MinecraftClient
import com.skysoft.utils.animation.PanelFadeTransition
import com.skysoft.utils.gui.OverlayListScroll
import com.skysoft.utils.gui.PixelSliderRenderer
import com.skysoft.utils.input.InputUtilities
import kotlin.math.roundToInt
import net.minecraft.client.gui.screens.Screen
import org.lwjgl.glfw.GLFW

internal object WaypointSettingsPanel {
    private val transition = PanelFadeTransition()
    val isOpen: Boolean get() = transition.isVisible
    val opacity: Double get() = transition.opacity()
    val isInteractive: Boolean get() = transition.isInteractive
    var showDetails = false
        private set
    var awaitingBinding: WaypointBinding? = null
        private set
    var layout: WaypointSettingsLayout? = null
        private set
    private var owner: Screen? = null
    private var capturedBinding: Int? = null
    private var draggedSlider: WaypointSlider? = null
    private var optionOffset = 0
    private val isActive: Boolean get() =
        isInteractive && owner === MinecraftClient.screen() && WaypointPanel.isVisible()

    fun shouldCaptureTyping(): Boolean = isActive && (awaitingBinding != null || capturedBinding != null)

    fun toggle() {
        if (isOpen && !transition.isClosing) {
            close()
        } else {
            owner = MinecraftClient.screen()
            transition.show()
        }
    }

    fun close() {
        didFinishDrag()
        transition.hide()
        awaitingBinding = null
        capturedBinding = null
    }

    fun clear() {
        close()
        transition.reset()
        layout = null
        owner = null
    }

    fun tick() {
        transition.opacity()
        if (!isOpen || !WaypointPanel.isVisible() || owner !== MinecraftClient.screen()) clear()
        if (capturedBinding?.let(InputUtilities::isBindingDown) == false) capturedBinding = null
        if (draggedSlider != null && !InputUtilities.isBindingDown(GLFW.GLFW_MOUSE_BUTTON_LEFT)) didFinishDrag()
    }

    fun updateLayout(panel: WaypointPanelLayout, width: Int, height: Int): WaypointSettingsLayout? {
        layout = if (isOpen && owner === MinecraftClient.screen()) {
            WaypointSettingsLayout.create(panel, width, height, optionOffset)
        } else null
        layout?.let { optionOffset = it.optionOffset }
        return layout
    }

    fun selectTab(details: Boolean) {
        didFinishDrag()
        awaitingBinding = null
        showDetails = details
        optionOffset = 0
    }

    fun bind(binding: WaypointBinding) {
        awaitingBinding = binding
    }

    fun didPressKey(key: Int): Boolean {
        if (!isActive) return false
        if (capturedBinding == key) return true
        val binding = awaitingBinding
        if (binding != null) {
            when (key) {
                GLFW.GLFW_KEY_ESCAPE -> awaitingBinding = null
                GLFW.GLFW_KEY_DELETE, GLFW.GLFW_KEY_BACKSPACE -> assign(binding, GLFW.GLFW_KEY_UNKNOWN)
                in GLFW.GLFW_KEY_SPACE..GLFW.GLFW_KEY_LAST -> assign(binding, key)
            }
            capturedBinding = key
            return true
        }
        if (key != GLFW.GLFW_KEY_ESCAPE) return false
        close()
        return true
    }

    fun didCaptureMouse(button: Int): Boolean {
        if (!isActive) return false
        val binding = awaitingBinding ?: return false
        assign(binding, button)
        capturedBinding = button
        return true
    }

    fun didReleaseBinding(key: Int): Boolean {
        if (capturedBinding != key) return false
        capturedBinding = null
        return true
    }

    private fun assign(binding: WaypointBinding, key: Int) {
        binding.set(key)
        awaitingBinding = null
        save()
    }

    fun startDrag(setting: WaypointSlider, x: Int) {
        draggedSlider = setting
        didDrag(x)
    }

    fun didDrag(x: Int): Boolean {
        if (!isActive) return false
        val setting = draggedSlider ?: return false
        val track = layout?.track(setting) ?: return false
        setting.set(PixelSliderRenderer.valueAt(x, track, setting.range, setting.step))
        return true
    }

    fun didFinishDrag(): Boolean {
        if (draggedSlider == null) return false
        draggedSlider = null
        save()
        return true
    }

    fun didScroll(x: Int, y: Int, amount: Double): Boolean {
        if (!isActive) return false
        val current = layout ?: return false
        if (!current.bounds.contains(x, y)) return false
        if (showDetails) {
            WaypointSlider.entries.firstOrNull {
                current.isOptionVisible(it.ordinal) && current.optionRow(it.ordinal).contains(x, y)
            }?.let {
                it.set(it.value() + amount.roundToInt() * it.step)
                save()
                return true
            }
        }
        didFinishDrag()
        optionOffset = OverlayListScroll.nextOffset(optionOffset, amount, current.maximumOffset)
        return true
    }

    fun save() {
        SkysoftConfigGui.config().saveNow()
    }
}
