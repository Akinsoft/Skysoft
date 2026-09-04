package com.skysoft.gui

import com.skysoft.gui.scale.GuiScaleController
import com.skysoft.utils.MinecraftClient
import com.skysoft.utils.gui.Rect
import kotlin.math.floor
import net.minecraft.client.Minecraft
import org.lwjgl.glfw.GLFW

data class OverlayControlArea<T>(
    val action: T,
    val bounds: Rect,
    val tooltipLines: List<String> = emptyList(),
) {
    fun contains(mouseX: Int, mouseY: Int): Boolean = bounds.contains(mouseX, mouseY)
}

object OverlayControlTooltips {
    fun cycle(settingName: String, options: List<String>, selectedIndex: Int): List<String> {
        require(options.isNotEmpty()) { "Cycle tooltip options cannot be empty" }
        require(selectedIndex in options.indices) { "Selected tooltip option index is out of bounds" }

        return buildList {
            add("§e$settingName")
            add("")
            options.forEachIndexed { index, option ->
                add(if (index == selectedIndex) "§a▶ §f$option" else "§7$option")
            }
            add("")
            add("§eClick §7to switch §e$settingName")
            add("§eRight-click §7to go backwards")
        }
    }
}

object OverlayControlCycle {
    fun wasClickHandled(button: Int, action: (backwards: Boolean) -> Unit): Boolean {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT && button != GLFW.GLFW_MOUSE_BUTTON_RIGHT) return false
        action(button == GLFW.GLFW_MOUSE_BUTTON_RIGHT)
        return true
    }

    fun <T> next(values: List<T>, current: T, backwards: Boolean): T {
        require(values.isNotEmpty()) { "Cycle values cannot be empty" }
        val currentIndex = values.indexOf(current)
        require(currentIndex >= 0) { "Current cycle value is missing" }
        return values[Math.floorMod(currentIndex + if (backwards) -1 else 1, values.size)]
    }
}

object OverlayControlMouse {
    fun localCoordinate(normalCoordinate: Int, origin: Int, scale: Float): Int =
        floor((normalCoordinate - origin) / scale).toInt()

    fun normalPoint(mouseX: Int, mouseY: Int): Pair<Int, Int> {
        val minecraft = Minecraft.getInstance()
        val window = minecraft.window
        val screen = MinecraftClient.screen(minecraft)
        val scales = GuiScaleController.resolve(screen, window)
        if (GuiScaleController.usesSeparateInventoryScale(screen)) {
            if (window.guiScale == scales.normal()) return mouseX to mouseY
            return GuiScaleController.convertCoordinate(mouseX, scales.inventory(), scales.normal()) to
                GuiScaleController.convertCoordinate(mouseY, scales.inventory(), scales.normal())
        }
        return GuiScaleController.convertCoordinate(mouseX, window.guiScale, scales.normal()) to
            GuiScaleController.convertCoordinate(mouseY, window.guiScale, scales.normal())
    }

    fun screenPoint(mouseX: Int, mouseY: Int): Pair<Int, Int> {
        val minecraft = Minecraft.getInstance()
        val window = minecraft.window
        val screen = MinecraftClient.screen(minecraft)
        val (normalMouseX, normalMouseY) = normalPoint(mouseX, mouseY)
        if (!GuiScaleController.usesSeparateInventoryScale(screen)) return normalMouseX to normalMouseY
        val scales = GuiScaleController.resolve(screen, window)
        return GuiScaleController.convertCoordinate(normalMouseX, scales.normal(), scales.inventory()) to
            GuiScaleController.convertCoordinate(normalMouseY, scales.normal(), scales.inventory())
    }
}
