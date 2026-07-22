package com.skysoft.gui

import com.skysoft.gui.scale.GuiScaleController
import com.skysoft.utils.MinecraftClient
import com.skysoft.utils.gui.Rect
import net.minecraft.client.Minecraft

data class OverlayControlArea<T>(
    val action: T,
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    val tooltipLines: List<String> = emptyList(),
) {
    fun contains(mouseX: Int, mouseY: Int): Boolean = Rect(x, y, width, height).contains(mouseX, mouseY)
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

object OverlayControlMouse {
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
