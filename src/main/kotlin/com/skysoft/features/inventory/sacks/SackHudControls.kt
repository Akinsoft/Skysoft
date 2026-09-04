package com.skysoft.features.inventory.sacks

import com.skysoft.config.SkysoftConfigGui
import com.skysoft.data.skyblock.SkyBlockItemId.skyBlockId
import com.skysoft.features.inventory.InventoryOverlayInput
import com.skysoft.mixin.AbstractContainerScreenAccessor
import com.skysoft.utils.SoundUtilities
import com.skysoft.utils.input.InputHandlingResult
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.input.MouseButtonEvent
import org.lwjgl.glfw.GLFW

internal fun registerSackHudInput() {
    val isActive = { sackHudConfig.enabled }
    InventoryOverlayInput.registerClickHandler("Sacks Tracker mouse click", isActive) { screen, click ->
        if (shouldAllowSackHudClick(screen, click)) InputHandlingResult.IGNORED else InputHandlingResult.CONSUMED
    }
    InventoryOverlayInput.registerScrollHandler("Sacks Tracker mouse scroll", isActive) {
            screen, mouseX, mouseY, verticalAmount ->
        val allowed = InventoryOverlayInput.isPointCovered(screen, mouseX, mouseY) ||
            !wasSackHudScrollHandled(verticalAmount)
        if (allowed) InputHandlingResult.IGNORED else InputHandlingResult.CONSUMED
    }
}

private fun shouldAllowSackHudClick(
    screen: AbstractContainerScreen<*>,
    click: MouseButtonEvent,
): Boolean {
    if (!isSackHudVisible()) return true
    if (InventoryOverlayInput.isPointCovered(screen, click.x(), click.y())) {
        sackHudItemPanel.close()
        return true
    }
    val control = sackHudHoveredControl?.action
    val panelHovered = sackHudItemPanel.isHovered
    if (control is SackHudControl.Item && sackHudItemPanel.isRemovingItems()) {
        if (click.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            removeSackHudTrackedItem(control.itemId)
            SoundUtilities.playClickSound()
        }
        return false
    }
    val handled = when (control) {
        SackHudControl.More -> wasLeftClickHandled(click.button(), sackHudItemPanel::toggleOverview)
        SackHudControl.AddItems -> wasLeftClickHandled(click.button(), sackHudItemPanel::beginAddingItems)
        SackHudControl.RemoveItems -> wasLeftClickHandled(click.button(), sackHudItemPanel::beginRemovingItems)
        is SackHudControl.ItemSelection -> sackHudItemPanel.wasSelectionClickHandled(control.action, click.button())
        is SackHudControl.Item -> wasSackItemClickHandled(
            screen,
            control.itemId,
            trackedSackHudItem(control.itemId).name,
            click.button(),
        )
        null -> wasInventoryItemSelected(screen, click.button())
    }
    val keepsPanelOpen = panelHovered || control == SackHudControl.More || control == null && handled
    if (!keepsPanelOpen) sackHudItemPanel.close()
    if (handled) SoundUtilities.playClickSound()
    return !handled && !panelHovered
}

private fun wasInventoryItemSelected(screen: AbstractContainerScreen<*>, button: Int): Boolean {
    if (!sackHudItemPanel.isSelectingFromInventory() || button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return false
    val player = Minecraft.getInstance().player ?: return false
    val slot = (screen as AbstractContainerScreenAccessor).skysoftGetHoveredSlot()
    val itemId = slot?.takeIf { it.container === player.inventory }?.item?.skyBlockId() ?: return false
    return sackHudItemPanel.wasInventoryItemSelected(itemId)
}

private fun wasSackHudScrollHandled(verticalAmount: Double): Boolean {
    if (!isSackHudVisible() || verticalAmount == 0.0) return false
    if (sackHudItemPanel.wasSearchScrollHandled(verticalAmount)) return true
    if (!sackHudHovered) return false
    val maximumOffset = sackHudMaximumScrollOffset(sackHudConfig.trackedItems.size)
    if (maximumOffset == 0) return false
    sackHudScrollOffset = (sackHudScrollOffset + if (verticalAmount < 0.0) 1 else -1)
        .coerceIn(0, maximumOffset)
    return true
}

private inline fun wasLeftClickHandled(button: Int, action: () -> Unit): Boolean {
    if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return false
    action()
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
