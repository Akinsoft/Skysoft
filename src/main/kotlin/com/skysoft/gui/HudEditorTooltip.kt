package com.skysoft.gui

import com.skysoft.features.inventory.InventoryButtonManager
import com.skysoft.utils.input.InputUtilities
import java.util.Locale
import org.lwjgl.glfw.GLFW

internal fun hudEditorTooltipLines(
    active: HudEditorElement?,
    activeButton: InventoryButtonManager.ButtonPlacement?,
    gridEnabled: Boolean,
): List<String> = buildList {
    when {
        activeButton != null -> {
            val button = activeButton.button
            add("§cSkysoft Position Editor")
            add("§bInventory Button")
            add("§7Command: §e${button.command.takeIf { it.isNotBlank() } ?: "empty"}")
            add("§7Scale: §e${"%.2f".format(Locale.US, button.scale)}")
            add(inventoryButtonHoldKeyLine(button.requiredKey))
            add("§eLeft-click drag §7to move")
            add("§eDouble-click §7to select")
            add("§eArrow Keys §7to move one pixel")
            add("§eHold Shift §7to snap to other buttons")
            add("§eScroll-Wheel §7to resize")
            add(if (button.isUserCreated == true) "§eR §7to remove" else "§eR §7to reset")
        }

        active == null -> {
            add("§cSkysoft Position Editor")
            add("§7Hover a HUD element or inventory button to move it.")
            add("§eDouble-click §7to select")
            add("§eLeft-click drag §7to move")
            add("§eScroll §7to resize")
        }

        else -> {
            add("§cSkysoft Position Editor")
            add("§b${active.label}")
            val details = active.editorDetailsLines()
            if (details != null) {
                addAll(details)
            } else {
                add(
                    if (active.canScale) {
                        "§7x: §e${active.position.x}§7, y: §e${active.position.y}§7, scale: §e${
                            "%.2f".format(Locale.US, active.position.scale)
                        }"
                    } else {
                        "§7x: §e${active.position.x}§7, y: §e${active.position.y}"
                    },
                )
            }
            addAll(active.editorActionLines() ?: defaultHudEditorActionLines(active))
        }
    }
    addAll(editorGlobalTooltipLines(gridEnabled))
}

private fun defaultHudEditorActionLines(element: HudEditorElement): List<String> = buildList {
    if (element.canMove) {
        add("§eLeft-click drag §7to move")
        add("§eDouble-click §7to select")
        add("§eArrow Keys §7to move one pixel")
    }
    if (element.canResizeWidth || element.canResizeHeight) add("§eDrag outside corner handles §7to resize")
    if (element.canMove || element.canResizeWidth || element.canResizeHeight) add("§eHold Shift §7to snap")
    add("§eRight-click §7to open settings")
    if (element.canScale) add("§eScroll-Wheel §7to resize")
    add("§eR §7to reset")
}

private fun editorGlobalTooltipLines(gridEnabled: Boolean): List<String> = listOf(
    if (gridEnabled) "§eG §7to hide the snapping grid" else "§eG §7to show the snapping grid",
    "§eCtrl+Z / Ctrl+Y §7to undo or redo",
)

private fun inventoryButtonHoldKeyLine(requiredKey: Int?): String =
    if (requiredKey != null && requiredKey != GLFW.GLFW_KEY_UNKNOWN) {
        "§7Hold Key: §e${InputUtilities.bindingName(requiredKey)}"
    } else {
        "§7Hold Key: §eNone"
    }
