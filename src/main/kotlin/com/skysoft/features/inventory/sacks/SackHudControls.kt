package com.skysoft.features.inventory.sacks

import com.skysoft.config.SkysoftConfigGui
import com.skysoft.data.skyblock.SkyBlockDataRepository
import com.skysoft.data.skyblock.SkyBlockItemId.skyBlockId
import com.skysoft.features.inventory.InventoryOverlayInput
import com.skysoft.features.inventory.itemlist.ItemListViewerScreen
import com.skysoft.mixin.AbstractContainerScreenAccessor
import com.skysoft.utils.MinecraftClient
import com.skysoft.utils.SkysoftErrorBoundary
import com.skysoft.utils.SoundUtilities
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.input.MouseButtonEvent
import org.lwjgl.glfw.GLFW

internal fun registerSackHudInput() {
    ScreenEvents.BEFORE_INIT.register { _, screen, _, _ ->
        if (screen !is AbstractContainerScreen<*>) return@register
        ScreenMouseEvents.allowMouseClick(screen).register { _, click ->
            SkysoftErrorBoundary.value("Sack HUD mouse click", true) {
                shouldAllowSackHudClick(screen, click)
            }
        }
        ScreenMouseEvents.allowMouseScroll(screen).register { _, mouseX, mouseY, _, verticalAmount ->
            SkysoftErrorBoundary.value("Sack HUD mouse scroll", true) {
                InventoryOverlayInput.isPointCovered(screen, mouseX, mouseY) ||
                    !wasSackHudScrollHandled(verticalAmount)
            }
        }
    }
}

private fun shouldAllowSackHudClick(
    screen: AbstractContainerScreen<*>,
    click: MouseButtonEvent,
): Boolean {
    if (!isSackHudVisible()) return true
    if (InventoryOverlayInput.isPointCovered(screen, click.x(), click.y())) return true
    val control = sackHudHoveredControl?.action
    val handled = when (control) {
        SackHudControl.AddItem -> if (click.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            sackHudAddingItem = !sackHudAddingItem
            true
        } else {
            false
        }
        is SackHudControl.Item -> wasSackHudItemClickHandled(screen, control.itemId, click.button())
        null -> wasInventoryItemAdded(screen, click.button())
    }
    if (handled) SoundUtilities.playClickSound()
    return !handled
}

private fun wasInventoryItemAdded(screen: AbstractContainerScreen<*>, button: Int): Boolean {
    if (!sackHudAddingItem || button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return false
    val player = Minecraft.getInstance().player ?: return false
    val slot = (screen as AbstractContainerScreenAccessor).skysoftGetHoveredSlot()
    val itemId = slot?.takeIf { it.container === player.inventory }?.item?.skyBlockId() ?: return false
    addSackHudTrackedItem(itemId)
    sackHudAddingItem = false
    return true
}

private fun wasSackHudItemClickHandled(
    screen: AbstractContainerScreen<*>,
    itemId: String,
    button: Int,
): Boolean = when (button) {
    GLFW.GLFW_MOUSE_BUTTON_LEFT -> {
        val connection = Minecraft.getInstance().connection ?: return false
        val key = SkyBlockDataRepository.itemKey(itemId)
        val itemName = SkyBlockDataRepository.entry(key)?.displayName
            ?: com.skysoft.data.ProfileStorageApi.storage.sackContents[itemId]?.displayName
            ?: return false
        connection.sendCommand("bz $itemName")
        MinecraftClient.setScreen(null)
        true
    }
    GLFW.GLFW_MOUSE_BUTTON_RIGHT -> {
        removeSackHudTrackedItem(itemId)
        true
    }
    GLFW.GLFW_MOUSE_BUTTON_MIDDLE -> if (SkysoftConfigGui.config().inventory.itemList.enabled) {
        MinecraftClient.setScreen(ItemListViewerScreen(screen, SkyBlockDataRepository.itemKey(itemId)))
        true
    } else {
        false
    }
    else -> false
}

private fun wasSackHudScrollHandled(verticalAmount: Double): Boolean {
    if (!isSackHudVisible() || !sackHudHovered || verticalAmount == 0.0) return false
    val maximumOffset = sackHudMaximumScrollOffset(sackHudConfig.trackedItems.size)
    if (maximumOffset == 0) return false
    sackHudScrollOffset = (sackHudScrollOffset + if (verticalAmount < 0.0) 1 else -1)
        .coerceIn(0, maximumOffset)
    return true
}

internal fun addSackHudTrackedItem(itemId: String) {
    if (itemId in sackHudConfig.trackedItems) return
    sackHudConfig.trackedItems += itemId
    SkysoftConfigGui.config().saveNow()
}

internal fun removeSackHudTrackedItem(itemId: String) {
    if (!sackHudConfig.trackedItems.remove(itemId)) return
    sackHudChangeHighlights.remove(itemId)
    sackHudScrollOffset = sackHudScrollOffset.coerceIn(
        0,
        sackHudMaximumScrollOffset(sackHudConfig.trackedItems.size),
    )
    SkysoftConfigGui.config().saveNow()
}
