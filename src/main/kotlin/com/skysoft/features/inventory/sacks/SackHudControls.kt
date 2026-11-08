package com.skysoft.features.inventory.sacks

import com.mojang.blaze3d.platform.InputConstants
import com.skysoft.config.SkysoftConfigGui
import com.skysoft.features.inventory.InventoryOverlayInput
import com.skysoft.features.inventory.hoveredPlayerInventoryItemId
import com.skysoft.features.inventory.wasLeftClickHandled
import com.skysoft.utils.SoundUtilities
import com.skysoft.utils.gui.OverlayListScroll
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.input.MouseButtonEvent

internal fun registerSackHudInput() {
    InventoryOverlayInput.registerTrackerInput(
        "Sacks Tracker",
        isActive = { sackHudConfig.enabled },
        shouldAllowClick = ::shouldAllowSackHudClick,
        wasScrollHandled = ::wasSackHudScrollHandled,
    )
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
        if (click.button() == InputConstants.MOUSE_BUTTON_LEFT) {
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
    if (!sackHudItemPanel.isSelectingFromInventory() || button != InputConstants.MOUSE_BUTTON_LEFT) return false
    val itemId = hoveredPlayerInventoryItemId(screen) ?: return false
    return sackHudItemPanel.wasInventoryItemSelected(itemId)
}

private fun wasSackHudScrollHandled(verticalAmount: Double): Boolean {
    if (!isSackHudVisible() || verticalAmount == 0.0) return false
    if (sackHudItemPanel.wasSearchScrollHandled(verticalAmount)) return true
    if (!sackHudHovered) return false
    val maximumOffset = sackHudMaximumScrollOffset(sackHudConfig.trackedItems.size)
    if (maximumOffset == 0) return false
    sackHudScrollOffset = OverlayListScroll.nextOffset(sackHudScrollOffset, verticalAmount, maximumOffset)
    return true
}

internal fun addSackHudTrackedItem(itemId: String) {
    if (itemId in sackHudConfig.trackedItems) return
    sackHudConfig.trackedItems += itemId
    SkysoftConfigGui.config().saveNow()
}

private fun removeSackHudTrackedItem(itemId: String) {
    if (!sackHudConfig.trackedItems.remove(itemId)) return
    sackHudChangeHighlights.remove(itemId)
    sackHudScrollOffset = sackHudScrollOffset.coerceIn(
        0,
        sackHudMaximumScrollOffset(sackHudConfig.trackedItems.size),
    )
    SkysoftConfigGui.config().saveNow()
}
