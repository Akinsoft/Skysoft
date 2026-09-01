package com.skysoft.features.inventory.sacks

import com.skysoft.features.inventory.TrackedItemSelectionAction
import com.skysoft.features.inventory.TrackedItemSelectionMode
import com.skysoft.features.inventory.TrackedItemSelectionPanel
import com.skysoft.utils.ColorUtilities.RGB_MASK
import com.skysoft.utils.ColorUtilities.withScaledAlpha
import com.skysoft.utils.animation.PanelFadeTransition
import com.skysoft.utils.gui.OverlayPanelStyle
import com.skysoft.utils.gui.Rect
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import org.lwjgl.glfw.GLFW

internal class SackHudItemPanel {
    private var content: Content? = null
    private val transition = PanelFadeTransition()
    private val selectionPanel = TrackedItemSelectionPanel()

    fun toggleOverview() {
        if (content != null && !transition.isClosing) close() else open(Content.Overview)
    }

    fun beginAddingItems() {
        selectionPanel.reset()
        open(Content.AddItem)
    }

    fun beginRemovingItems() = open(Content.RemoveItem)

    fun isSelectingFromInventory(): Boolean =
        content == Content.AddItem && selectionPanel.mode == TrackedItemSelectionMode.INVENTORY && !transition.isClosing

    fun isRemovingItems(): Boolean = content == Content.RemoveItem && !transition.isClosing

    fun wasInventoryItemSelected(itemId: String): Boolean {
        if (!isSelectingFromInventory()) return false
        if (isSelectable(itemId)) selectItem(itemId)
        return true
    }

    fun wasSelectionClickHandled(action: TrackedItemSelectionAction, button: Int): Boolean {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return false
        when (action) {
            TrackedItemSelectionAction.Inventory -> selectionPanel.selectMode(TrackedItemSelectionMode.INVENTORY)
            TrackedItemSelectionAction.Search -> selectionPanel.selectMode(TrackedItemSelectionMode.SEARCH)
            is TrackedItemSelectionAction.SearchField -> selectionPanel.focusSearch(action.localMouseX)
            is TrackedItemSelectionAction.SearchResult -> selectItem(action.itemId)
        }
        return true
    }

    fun wasKeyPressHandled(event: KeyEvent): Boolean =
        content == Content.AddItem && selectionPanel.wasKeyPressHandled(event, ::isSelectable, ::selectItem)

    fun wasCharTypedHandled(event: CharacterEvent): Boolean =
        content == Content.AddItem && selectionPanel.wasCharTypedHandled(event)

    fun wasSearchScrollHandled(verticalAmount: Double): Boolean =
        content == Content.AddItem && selectionPanel.wasScrollHandled(verticalAmount, ::isSelectable)

    fun clear() {
        content = null
        selectionPanel.clear()
        transition.reset()
    }

    fun render(
        context: GuiGraphicsExtractor,
        trackerWidth: Int,
        placeRight: Boolean,
        mouseX: Int,
        mouseY: Int,
    ): LocalSackHudControl? {
        val current = content ?: return null
        val opacity = transition.opacity()
        if (!transition.isVisible) {
            clear()
            return null
        }
        if (current == Content.AddItem) {
            return selectionPanel.render(
                context = context,
                title = "Add Item",
                trackerWidth = trackerWidth,
                placeRight = placeRight,
                mouseX = mouseX,
                mouseY = mouseY,
                opacity = opacity,
                interactive = transition.isInteractive,
                isSelectable = ::isSelectable,
            )?.let { control ->
                LocalSackHudControl(SackHudControl.ItemSelection(control.action), control.bounds, emptyList())
            }
        }
        val rows = if (current == Content.RemoveItem) {
            listOf(
                PanelRow(styledText("Remove Item", TITLE_COLOR, bold = true)),
                PanelRow(styledText("Click an item to remove", MUTED_COLOR)),
            )
        } else {
            listOf(
                PanelRow(styledText("Manage Tracked Items", TITLE_COLOR, bold = true)),
                PanelRow(styledText("Add Item", ACTION_COLOR), SackHudControl.AddItems),
                PanelRow(styledText("Remove Item", DANGER_COLOR), SackHudControl.RemoveItems),
            )
        }
        return renderRows(context, trackerWidth, placeRight, mouseX, mouseY, opacity, rows)
    }

    private fun renderRows(
        context: GuiGraphicsExtractor,
        trackerWidth: Int,
        placeRight: Boolean,
        mouseX: Int,
        mouseY: Int,
        opacity: Double,
        rows: List<PanelRow>,
    ): LocalSackHudControl? {
        val font = Minecraft.getInstance().font
        val width = maxOf(PANEL_MINIMUM_WIDTH, rows.maxOf { font.width(it.text) }) + OverlayPanelStyle.PADDING * 2
        val height = rows.size * PANEL_ROW_HEIGHT + OverlayPanelStyle.PADDING * 2
        val x = if (placeRight) trackerWidth + PANEL_GAP else -width - PANEL_GAP
        context.fill(x, 0, x + width, height, OverlayPanelStyle.BACKGROUND.withScaledAlpha(opacity))
        context.outline(x, 0, width, height, OverlayPanelStyle.OUTLINE.withScaledAlpha(opacity))
        var hovered: LocalSackHudControl? = null
        rows.forEachIndexed { index, row ->
            val y = OverlayPanelStyle.PADDING + index * PANEL_ROW_HEIGHT
            val bounds = Rect(x, y, width, PANEL_ROW_HEIGHT)
            if (transition.isInteractive && row.action != null && bounds.contains(mouseX, mouseY)) {
                context.fill(
                    bounds.x,
                    bounds.y,
                    bounds.x + bounds.width,
                    bounds.y + bounds.height,
                    PANEL_HOVER.withScaledAlpha(opacity),
                )
                hovered = LocalSackHudControl(row.action, bounds, emptyList())
            }
            context.text(
                font,
                row.text,
                x + OverlayPanelStyle.PADDING,
                y + (PANEL_ROW_HEIGHT - PANEL_TEXT_HEIGHT) / 2,
                TEXT_BASE_COLOR.withScaledAlpha(opacity),
                false,
            )
        }
        return hovered
    }

    private fun isSelectable(itemId: String): Boolean = when (content) {
        Content.AddItem -> itemId !in sackHudConfig.trackedItems
        Content.RemoveItem -> itemId in sackHudConfig.trackedItems
        Content.Overview, null -> false
    }

    private fun selectItem(itemId: String) {
        when (content) {
            Content.AddItem -> addSackHudTrackedItem(itemId)
            Content.RemoveItem -> removeSackHudTrackedItem(itemId)
            Content.Overview, null -> Unit
        }
    }

    private fun open(next: Content) {
        content = next
        transition.show()
    }

    private fun close() = transition.hide()

    private sealed interface Content {
        data object Overview : Content
        data object AddItem : Content
        data object RemoveItem : Content
    }

    private data class PanelRow(
        val text: Component,
        val action: SackHudControl? = null,
    )
}

private fun styledText(text: String, color: Int, bold: Boolean = false): MutableComponent =
    Component.literal(text).withStyle { style -> style.withColor(color and RGB_MASK).withBold(bold) }

private const val PANEL_MINIMUM_WIDTH = 130
private const val PANEL_ROW_HEIGHT = 11
private const val PANEL_TEXT_HEIGHT = 9
private const val PANEL_GAP = 4
private const val TEXT_BASE_COLOR = 0xFFFFFFFF.toInt()
private const val TITLE_COLOR = 0xFFFFFF55.toInt()
private const val ACTION_COLOR = 0xFF55FF55.toInt()
private const val DANGER_COLOR = 0xFFFF5555.toInt()
private const val MUTED_COLOR = 0xFFAAAAAA.toInt()
private const val PANEL_HOVER = 0x28FFFFFF
