package com.skysoft.features.inventory.crafting

import com.skysoft.config.SkysoftConfigGui
import com.skysoft.data.skyblock.SkyBlockItemId.skyBlockId
import com.skysoft.features.inventory.InventoryOverlayInput
import com.skysoft.features.inventory.TrackedItemManagerAction
import com.skysoft.features.inventory.TrackedItemManagerPanel
import com.skysoft.features.inventory.TrackedItemQuantityAction
import com.skysoft.features.inventory.TrackedItemSelectionAction
import com.skysoft.features.inventory.itemlist.ItemListViewerScreen
import com.skysoft.features.inventory.itemlist.itemListQuickCraftCommand
import com.skysoft.mixin.AbstractContainerScreenAccessor
import com.skysoft.utils.MinecraftClient
import com.skysoft.utils.SoundUtilities
import com.skysoft.utils.TextUtilities.cleanSkyBlockText
import com.skysoft.utils.gui.Rect
import com.skysoft.utils.input.InputHandlingResult
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import org.lwjgl.glfw.GLFW

internal sealed interface CraftingHelperControl {
    data object More : CraftingHelperControl
    data object AddItems : CraftingHelperControl
    data object DecreaseItems : CraftingHelperControl
    data class ItemSelection(val action: TrackedItemSelectionAction) : CraftingHelperControl
    data class Quantity(val action: TrackedItemQuantityAction) : CraftingHelperControl
    data class Line(val line: CraftingHelperLine) : CraftingHelperControl
}

internal data class LocalCraftingHelperControl(
    val action: CraftingHelperControl,
    val bounds: Rect,
)

internal class CraftingHelperItemPanel {
    private val panel = TrackedItemManagerPanel(
        overviewTitle = "Manage Crafting Targets",
        addTitle = "Add Item",
        removeTitle = "Decrease Amount",
        removeInstruction = "Click a target to manage it",
        isSelectable = { itemId -> isCraftingHelperTarget(itemId) && itemId !in craftingHelperConfig.targets },
        selectItem = ::addCraftingHelperTarget,
    )

    fun toggleAddingItems() = panel.toggleAddingItems()

    fun beginAddingItems() = panel.beginAddingItems()

    fun toggleItem(itemId: String) = panel.toggleItem(itemId)

    fun beginRemovingItems() = panel.beginRemovingItems()

    fun isSelectingFromInventory(): Boolean = panel.isSelectingFromInventory()

    val isHovered: Boolean
        get() = panel.isHovered

    fun wasInventoryItemSelected(itemId: String): Boolean = panel.wasInventoryItemSelected(itemId)

    fun wasSelectionClickHandled(action: TrackedItemSelectionAction, button: Int): Boolean =
        panel.wasSelectionClickHandled(action, button)

    fun wasKeyPressHandled(event: KeyEvent): Boolean =
        panel.wasQuantityKeyPressHandled(event, ::modifyCraftingHelperTarget) || panel.wasKeyPressHandled(event)

    fun wasCharTypedHandled(event: CharacterEvent): Boolean = panel.wasCharTypedHandled(event)

    fun wasSearchScrollHandled(verticalAmount: Double): Boolean = panel.wasSearchScrollHandled(verticalAmount)

    fun wasQuantityClickHandled(action: TrackedItemQuantityAction, button: Int): Boolean =
        panel.wasQuantityClickHandled(action, button, ::modifyCraftingHelperTarget)

    fun close() = panel.close()

    fun clear() = panel.clear()

    fun render(
        context: GuiGraphicsExtractor,
        trackerWidth: Int,
        placeRight: Boolean,
        mouseX: Int,
        mouseY: Int,
    ): LocalCraftingHelperControl? = panel.render(context, trackerWidth, placeRight, mouseX, mouseY)?.let { control ->
        val action = when (val action = control.action) {
            TrackedItemManagerAction.AddItems -> CraftingHelperControl.AddItems
            TrackedItemManagerAction.RemoveItems -> CraftingHelperControl.DecreaseItems
            is TrackedItemManagerAction.ItemSelection -> CraftingHelperControl.ItemSelection(action.action)
            is TrackedItemManagerAction.Quantity -> CraftingHelperControl.Quantity(action.action)
        }
        LocalCraftingHelperControl(action, control.bounds)
    }
}

internal object CraftingHelperInput {
    @JvmStatic
    fun handleKeyPress(event: KeyEvent): InputHandlingResult =
        if (craftingHelperConfig.enabled && craftingHelperItemPanel.wasKeyPressHandled(event)) {
            InputHandlingResult.CONSUMED
        } else {
            InputHandlingResult.IGNORED
        }

    @JvmStatic
    fun handleCharTyped(event: CharacterEvent): InputHandlingResult =
        if (craftingHelperConfig.enabled && craftingHelperItemPanel.wasCharTypedHandled(event)) {
            InputHandlingResult.CONSUMED
        } else {
            InputHandlingResult.IGNORED
        }
}

internal fun registerCraftingHelperInput() {
    val isActive = { craftingHelperConfig.enabled }
    InventoryOverlayInput.registerClickHandler("Crafting Helper mouse click", isActive) { screen, click ->
        if (shouldAllowCraftingHelperClick(screen, click)) InputHandlingResult.IGNORED else InputHandlingResult.CONSUMED
    }
    InventoryOverlayInput.registerScrollHandler("Crafting Helper mouse scroll", isActive) {
            screen, mouseX, mouseY, verticalAmount ->
        val allowed = InventoryOverlayInput.isPointCovered(screen, mouseX, mouseY) ||
            !wasCraftingHelperScrollHandled(verticalAmount)
        if (allowed) InputHandlingResult.IGNORED else InputHandlingResult.CONSUMED
    }
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
            craftingHelperItemPanel.wasQuantityClickHandled(control.action, click.button())
        is CraftingHelperControl.Line -> wasCraftingHelperLineClickHandled(screen, control.line, click.button())
        null -> wasInventoryTargetSelected(screen, click.button())
    }
    val keepsPanelOpen = panelHovered || control == CraftingHelperControl.More ||
        control is CraftingHelperControl.Line && control.line.isTarget &&
        click.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT || control == null && handled
    if (!keepsPanelOpen) craftingHelperItemPanel.close()
    if (handled) SoundUtilities.playClickSound()
    return !handled && !panelHovered
}

private fun wasInventoryTargetSelected(screen: AbstractContainerScreen<*>, button: Int): Boolean {
    if (!craftingHelperItemPanel.isSelectingFromInventory() || button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return false
    val player = Minecraft.getInstance().player ?: return false
    val slot = (screen as AbstractContainerScreenAccessor).skysoftGetHoveredSlot()
    val itemId = slot?.takeIf { it.container === player.inventory }?.item?.skyBlockId() ?: return false
    return craftingHelperItemPanel.wasInventoryItemSelected(itemId)
}

private fun wasCraftingHelperLineClickHandled(
    screen: AbstractContainerScreen<*>,
    line: CraftingHelperLine,
    button: Int,
): Boolean = when (button) {
    GLFW.GLFW_MOUSE_BUTTON_LEFT -> if (line.isTarget) {
        craftingHelperItemPanel.toggleItem(line.itemId)
        true
    } else {
        wasAcquisitionClickHandled(line)
    }
    GLFW.GLFW_MOUSE_BUTTON_RIGHT -> {
        val key = line.key ?: return false
        if (!SkysoftConfigGui.config().inventory.itemList.enabled) return false
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
        Minecraft.getInstance().keyboardHandler.setClipboard(copyAmount.toString())
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
    craftingHelperScrollOffset = (craftingHelperScrollOffset + if (verticalAmount < 0.0) 1 else -1)
        .coerceIn(0, maximumOffset)
    return true
}

private inline fun wasLeftClickHandled(button: Int, action: () -> Unit): Boolean {
    if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return false
    action()
    return true
}
