package com.skysoft.features.inventory.sacks

import com.skysoft.features.inventory.TrackedItemManagerAction
import com.skysoft.features.inventory.TrackedItemManagerPanel
import com.skysoft.features.inventory.TrackedItemSelectionAction
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent

internal class SackHudItemPanel {
    private val panel = TrackedItemManagerPanel(
        overviewTitle = "Manage Tracked Items",
        addTitle = "Add Item",
        removeTitle = "Remove Item",
        removeInstruction = "Click an item to remove",
        isSelectable = { itemId -> itemId !in sackHudConfig.trackedItems },
        selectItem = ::addSackHudTrackedItem,
    )

    fun toggleOverview() = panel.toggleOverview()

    fun beginAddingItems() = panel.beginAddingItems()

    fun beginRemovingItems() = panel.beginRemovingItems()

    fun isSelectingFromInventory(): Boolean = panel.isSelectingFromInventory()

    fun isRemovingItems(): Boolean = panel.isRemovingItems()

    val isHovered: Boolean
        get() = panel.isHovered

    fun wasInventoryItemSelected(itemId: String): Boolean = panel.wasInventoryItemSelected(itemId)

    fun wasSelectionClickHandled(action: TrackedItemSelectionAction, button: Int): Boolean =
        panel.wasSelectionClickHandled(action, button)

    fun wasKeyPressHandled(event: KeyEvent): Boolean = panel.wasKeyPressHandled(event)

    fun wasCharTypedHandled(event: CharacterEvent): Boolean = panel.wasCharTypedHandled(event)

    fun wasSearchScrollHandled(verticalAmount: Double): Boolean = panel.wasSearchScrollHandled(verticalAmount)

    fun close() = panel.close()

    fun clear() = panel.clear()

    fun render(
        context: GuiGraphicsExtractor,
        trackerWidth: Int,
        placeRight: Boolean,
        mouseX: Int,
        mouseY: Int,
    ): LocalSackHudControl? = panel.render(context, trackerWidth, placeRight, mouseX, mouseY)?.let { control ->
        val action = when (val action = control.action) {
            TrackedItemManagerAction.AddItems -> SackHudControl.AddItems
            TrackedItemManagerAction.RemoveItems -> SackHudControl.RemoveItems
            is TrackedItemManagerAction.ItemSelection -> SackHudControl.ItemSelection(action.action)
            is TrackedItemManagerAction.Quantity -> error("Sacks Tracker does not edit quantities")
        }
        LocalSackHudControl(action, control.bounds, emptyList())
    }
}
