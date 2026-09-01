package com.skysoft.features.inventory.sacks

import com.skysoft.config.SkysoftConfigGui
import com.skysoft.data.skyblock.SkyBlockDataRepository
import com.skysoft.features.inventory.itemlist.ItemListViewerScreen
import com.skysoft.utils.MinecraftClient
import com.skysoft.utils.TextUtilities.cleanSkyBlockText
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import org.lwjgl.glfw.GLFW

internal fun wasSackItemClickHandled(
    screen: AbstractContainerScreen<*>,
    itemId: String,
    fallbackName: String,
    button: Int,
): Boolean = when (button) {
    GLFW.GLFW_MOUSE_BUTTON_LEFT -> {
        val connection = Minecraft.getInstance().connection ?: return false
        val key = SkyBlockDataRepository.itemKey(itemId)
        val itemName = SkyBlockDataRepository.entry(key)?.displayName ?: fallbackName.cleanSkyBlockText()
        connection.sendCommand("bz $itemName")
        MinecraftClient.setScreen(null)
        true
    }
    GLFW.GLFW_MOUSE_BUTTON_RIGHT -> if (SkysoftConfigGui.config().inventory.itemList.enabled) {
        MinecraftClient.setScreen(ItemListViewerScreen(screen, SkyBlockDataRepository.itemKey(itemId)))
        true
    } else {
        false
    }
    else -> false
}

internal fun sackItemActionLines(): List<String> = buildList {
    add("§eLeft-click §7to open Bazaar")
    if (SkysoftConfigGui.config().inventory.itemList.enabled) {
        add("§eRight-click §7to open Item List Info")
    }
}
