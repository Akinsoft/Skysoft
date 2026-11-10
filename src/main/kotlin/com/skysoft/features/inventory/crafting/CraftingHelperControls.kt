package com.skysoft.features.inventory.crafting

import com.mojang.blaze3d.platform.InputConstants
import com.skysoft.config.SkysoftConfigGui
import com.skysoft.gui.InventoryOverlayInput
import com.skysoft.gui.tracker.TrackedItemQuantityAction
import com.skysoft.gui.tracker.TrackedItemSelectionAction
import com.skysoft.gui.hoveredPlayerInventoryItemId
import com.skysoft.features.inventory.itemlist.ItemListViewerScreen
import com.skysoft.features.inventory.itemlist.itemListQuickCraftCommand
import com.skysoft.gui.wasLeftClickHandled
import com.skysoft.utils.MinecraftClient
import com.skysoft.utils.SoundUtilities
import com.skysoft.utils.TextUtilities.cleanSkyBlockText
import com.skysoft.utils.gui.OverlayListScroll
import com.skysoft.utils.input.InputHandlingResult
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent

internal sealed interface CraftingHelperControl {
    data object More : CraftingHelperControl
    data object AddItems : CraftingHelperControl
    data object DecreaseItems : CraftingHelperControl
    data class ItemSelection(val action: TrackedItemSelectionAction) : CraftingHelperControl
    data class Quantity(val action: TrackedItemQuantityAction) : CraftingHelperControl
    data class Line(val line: CraftingHelperLine) : CraftingHelperControl
}

internal object CraftingHelperInput {
    fun handleKeyPress(event: KeyEvent): InputHandlingResult =
        InputHandlingResult.from(craftingHelperConfig.enabled && wasCraftingHelperKeyPressHandled(event))

    fun handleCharTyped(event: CharacterEvent): InputHandlingResult =
        InputHandlingResult.from(craftingHelperConfig.enabled && craftingHelperItemPanel.wasCharTypedHandled(event))
}

private fun wasCraftingHelperKeyPressHandled(event: KeyEvent): Boolean =
    craftingHelperItemPanel.wasQuantityKeyPressHandled(event, ::modifyCraftingHelperTarget) ||
        craftingHelperItemPanel.wasKeyPressHandled(event)

internal fun registerCraftingHelperInput() {
    InventoryOverlayInput.registerTrackerInput(
        "Crafting Helper",
        isActive = { craftingHelperConfig.enabled },
        shouldAllowClick = ::shouldAllowCraftingHelperClick,
        wasScrollHandled = ::wasCraftingHelperScrollHandled,
    )
}

private fun shouldAllowCraftingHelperClick(
    screen: AbstractContainerScreen<*>,
    click: MouseButtonEvent,
): Boolean {
    if (!isCraftingHelperVisible()) return true
    if (InventoryOverlayInput.isPointCovered(screen, click.x(), click.y())) {
        craftingHelperItemPanel.close()
        return true
    }
    val control = craftingHelperHoveredControl?.action
    val panelHovered = craftingHelperItemPanel.isHovered
    val handled = when (control) {
        CraftingHelperControl.More ->
            wasLeftClickHandled(click.button(), craftingHelperItemPanel::toggleAddingItems)
        CraftingHelperControl.AddItems ->
            wasLeftClickHandled(click.button(), craftingHelperItemPanel::beginAddingItems)
        CraftingHelperControl.DecreaseItems ->
            wasLeftClickHandled(click.button(), craftingHelperItemPanel::beginRemovingItems)
        is CraftingHelperControl.ItemSelection ->
            craftingHelperItemPanel.wasSelectionClickHandled(control.action, click.button())
        is CraftingHelperControl.Quantity ->
            craftingHelperItemPanel.wasQuantityClickHandled(control.action, click.button(), ::modifyCraftingHelperTarget)
        is CraftingHelperControl.Line -> wasCraftingHelperLineClickHandled(screen, control.line, click.button())
        null -> wasInventoryTargetSelected(screen, click.button())
    }
    val keepsPanelOpen = panelHovered || control == CraftingHelperControl.More ||
        control is CraftingHelperControl.Line && control.line.isTarget &&
        click.button() == InputConstants.MOUSE_BUTTON_LEFT || control == null && handled
    if (!keepsPanelOpen) craftingHelperItemPanel.close()
    if (handled) SoundUtilities.playClickSound()
    return !handled && !panelHovered
}

private fun wasInventoryTargetSelected(screen: AbstractContainerScreen<*>, button: Int): Boolean {
    if (!craftingHelperItemPanel.isSelectingFromInventory() || button != InputConstants.MOUSE_BUTTON_LEFT) return false
    val itemId = hoveredPlayerInventoryItemId(screen) ?: return false
    return craftingHelperItemPanel.wasInventoryItemSelected(itemId)
}

private fun wasCraftingHelperLineClickHandled(
    screen: AbstractContainerScreen<*>,
    line: CraftingHelperLine,
    button: Int,
): Boolean = when (button) {
    InputConstants.MOUSE_BUTTON_LEFT -> if (line.isTarget) {
        craftingHelperItemPanel.toggleItem(line.itemId)
        true
    } else {
        wasAcquisitionClickHandled(line)
    }
    InputConstants.MOUSE_BUTTON_RIGHT -> {
        val key = line.key ?: return false
        if (!SkysoftConfigGui.config().items.itemList.enabled) return false
        MinecraftClient.setScreen(ItemListViewerScreen(screen, key))
        true
    }
    else -> false
}

private fun wasAcquisitionClickHandled(line: CraftingHelperLine): Boolean {
    val command = if (line.supercraft != null) {
        itemListQuickCraftCommand(line.supercraft.itemId)
    } else {
        when (line.acquisition) {
            CraftingAcquisition.BAZAAR -> "bz ${line.plainName.cleanSkyBlockText()}"
            CraftingAcquisition.AUCTION_HOUSE -> "ahs ${line.plainName.cleanSkyBlockText()}"
            CraftingAcquisition.FORGE -> "warp forge"
            null -> null
        }
    }
    val connection = command?.let { Minecraft.getInstance().connection ?: return false }
    val copyAmount = line.supercraft?.crafts ?: line.missing
    if (craftingHelperConfig.settings.copyAmount && copyAmount > 0L) {
        MinecraftClient.setClipboard(copyAmount.toString())
    } else if (command == null) {
        return false
    }
    if (command != null) {
        connection?.sendCommand(command)
        MinecraftClient.setScreen(null)
    }
    return true
}

private fun wasCraftingHelperScrollHandled(verticalAmount: Double): Boolean {
    if (!isCraftingHelperVisible() || verticalAmount == 0.0) return false
    if (craftingHelperItemPanel.wasSearchScrollHandled(verticalAmount)) return true
    if (!craftingHelperHovered) return false
    val maximumOffset = craftingHelperMaximumScrollOffset(craftingHelperLines().size)
    if (maximumOffset == 0) return false
    craftingHelperScrollOffset = OverlayListScroll.nextOffset(craftingHelperScrollOffset, verticalAmount, maximumOffset)
    return true
}
