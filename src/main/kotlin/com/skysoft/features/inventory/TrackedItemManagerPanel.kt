package com.skysoft.features.inventory

import com.skysoft.utils.ColorUtilities.RGB_MASK
import com.skysoft.utils.ColorUtilities.withScaledAlpha
import com.skysoft.utils.animation.PanelFadeTransition
import com.skysoft.utils.gui.OverlayPanelStyle
import com.skysoft.utils.gui.Rect
import com.skysoft.utils.renderables.primitives.ItemIconRenderable
import com.skysoft.utils.renderables.renderAt
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import org.lwjgl.glfw.GLFW

internal sealed interface TrackedItemManagerAction {
    data object AddItems : TrackedItemManagerAction
    data object RemoveItems : TrackedItemManagerAction
    data class ItemSelection(val action: TrackedItemSelectionAction) : TrackedItemManagerAction
    data class Quantity(val action: TrackedItemQuantityAction) : TrackedItemManagerAction
}

internal data class TrackedItemManagerControl(
    val action: TrackedItemManagerAction,
    val bounds: Rect,
)

internal class TrackedItemManagerPanel(
    private val overviewTitle: String,
    private val addTitle: String,
    private val removeTitle: String,
    private val removeInstruction: String,
    private val isSelectable: (String) -> Boolean,
    private val selectItem: (String) -> Unit,
) {
    private var content: Content? = null
    private val transition = PanelFadeTransition()
    private val selectionPanel = TrackedItemSelectionPanel()
    private val quantityModifier = TrackedItemQuantityModifier()
    var isHovered = false
        private set

    fun toggleOverview() {
        if (content != null && !transition.isClosing) close() else open(Content.Overview)
    }

    fun toggleAddingItems() {
        if (content == Content.AddItem && !transition.isClosing) close() else beginAddingItems()
    }

    fun beginAddingItems() {
        selectionPanel.reset()
        open(Content.AddItem)
    }

    fun beginRemovingItems() = open(Content.RemoveItem)

    fun toggleItem(itemId: String) {
        val item = Content.Item(itemId)
        if (content == item && !transition.isClosing) close() else open(item)
    }

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
        content == Content.AddItem && selectionPanel.wasKeyPressHandled(event, isSelectable, selectItem)

    fun wasQuantityKeyPressHandled(event: KeyEvent, modifyItem: (String, Long) -> Unit): Boolean {
        val itemId = (content as? Content.Item)?.itemId ?: return false
        return quantityModifier.wasKeyPressHandled(event) { amount -> modifyItem(itemId, amount) }
    }

    fun wasCharTypedHandled(event: CharacterEvent): Boolean =
        quantityModifier.wasCharTypedHandled(event) ||
            content == Content.AddItem && selectionPanel.wasCharTypedHandled(event)

    fun wasQuantityClickHandled(
        action: TrackedItemQuantityAction,
        button: Int,
        modifyItem: (String, Long) -> Unit,
    ): Boolean {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return false
        val itemId = (content as? Content.Item)?.itemId ?: return false
        when (action) {
            is TrackedItemQuantityAction.Modify -> modifyItem(itemId, action.amount)
            is TrackedItemQuantityAction.BeginCustom -> quantityModifier.begin(action.direction)
            is TrackedItemQuantityAction.Field -> quantityModifier.focus(action.localMouseX, action.bounds)
        }
        return true
    }

    fun wasSearchScrollHandled(verticalAmount: Double): Boolean =
        content == Content.AddItem && selectionPanel.wasScrollHandled(verticalAmount, isSelectable)

    fun clear() {
        content = null
        selectionPanel.clear()
        quantityModifier.cancel()
        isHovered = false
        transition.reset()
    }

    fun render(
        context: GuiGraphicsExtractor,
        trackerWidth: Int,
        placeRight: Boolean,
        mouseX: Int,
        mouseY: Int,
    ): TrackedItemManagerControl? {
        val current = content ?: return null
        val opacity = transition.opacity()
        if (!transition.isVisible) {
            clear()
            return null
        }
        if (current == Content.AddItem) {
            val control = selectionPanel.render(
                context = context,
                title = addTitle,
                trackerWidth = trackerWidth,
                placeRight = placeRight,
                mouseX = mouseX,
                mouseY = mouseY,
                opacity = opacity,
                interactive = transition.isInteractive,
                isSelectable = isSelectable,
            )
            isHovered = selectionPanel.isHovered
            return control?.let {
                TrackedItemManagerControl(TrackedItemManagerAction.ItemSelection(it.action), it.bounds)
            }
        }
        if (current is Content.Item) {
            return renderItem(context, trackerWidth, placeRight, mouseX, mouseY, opacity, current.itemId)
        }
        val rows = if (current == Content.RemoveItem) {
            listOf(
                PanelRow(styledManagerText(removeTitle, TITLE_COLOR, bold = true)),
                PanelRow(styledManagerText(removeInstruction, MUTED_COLOR)),
            )
        } else {
            listOf(
                PanelRow(styledManagerText(overviewTitle, TITLE_COLOR, bold = true)),
                PanelRow(styledManagerText(addTitle, ACTION_COLOR), TrackedItemManagerAction.AddItems),
                PanelRow(styledManagerText(removeTitle, DANGER_COLOR), TrackedItemManagerAction.RemoveItems),
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
    ): TrackedItemManagerControl? {
        val font = Minecraft.getInstance().font
        val width = maxOf(PANEL_MINIMUM_WIDTH, rows.maxOf { font.width(it.text) }) + OverlayPanelStyle.PADDING * 2
        val height = rows.size * PANEL_ROW_HEIGHT + OverlayPanelStyle.PADDING * 2
        val x = if (placeRight) trackerWidth + PANEL_GAP else -width - PANEL_GAP
        isHovered = Rect(x, 0, width, height).contains(mouseX, mouseY)
        context.fill(x, 0, x + width, height, OverlayPanelStyle.BACKGROUND.withScaledAlpha(opacity))
        context.outline(x, 0, width, height, OverlayPanelStyle.OUTLINE.withScaledAlpha(opacity))
        var hovered: TrackedItemManagerControl? = null
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
                hovered = TrackedItemManagerControl(row.action, bounds)
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

    private fun renderItem(
        context: GuiGraphicsExtractor,
        trackerWidth: Int,
        placeRight: Boolean,
        mouseX: Int,
        mouseY: Int,
        opacity: Double,
        itemId: String,
    ): TrackedItemManagerControl? {
        val font = Minecraft.getInstance().font
        val item = trackedItemPresentation(itemId)
        val width = maxOf(
            PANEL_MINIMUM_WIDTH,
            (if (item.stack == null) 0 else PANEL_ICON_TEXT_OFFSET) + font.width(item.component),
            quantityModifier.width(),
        ) + OverlayPanelStyle.PADDING * 2
        val height = PANEL_ICON_ROW_HEIGHT + quantityModifier.height + OverlayPanelStyle.PADDING * 2
        val x = if (placeRight) trackerWidth + PANEL_GAP else -width - PANEL_GAP
        isHovered = Rect(x, 0, width, height).contains(mouseX, mouseY)
        context.fill(x, 0, x + width, height, OverlayPanelStyle.BACKGROUND.withScaledAlpha(opacity))
        context.outline(x, 0, width, height, OverlayPanelStyle.OUTLINE.withScaledAlpha(opacity))
        val itemY = OverlayPanelStyle.PADDING
        item.stack?.let { stack ->
            ItemIconRenderable(stack, PANEL_ICON_SCALE).renderAt(context, x + OverlayPanelStyle.PADDING, itemY)
        }
        context.text(
            font,
            item.component,
            x + OverlayPanelStyle.PADDING + if (item.stack == null) 0 else PANEL_ICON_TEXT_OFFSET,
            itemY + (PANEL_ICON_ROW_HEIGHT - PANEL_TEXT_HEIGHT) / 2,
            TEXT_BASE_COLOR.withScaledAlpha(opacity),
            false,
        )
        return quantityModifier.render(
            context,
            x,
            itemY + PANEL_ICON_ROW_HEIGHT,
            mouseX,
            mouseY,
            opacity,
            transition.isInteractive,
        )?.let { control ->
            TrackedItemManagerControl(TrackedItemManagerAction.Quantity(control.action), control.bounds)
        }
    }

    private fun open(next: Content) {
        quantityModifier.cancel()
        content = next
        transition.show()
    }

    fun close() {
        selectionPanel.clear()
        quantityModifier.cancel()
        transition.hide()
    }

    private sealed interface Content {
        data object Overview : Content
        data object AddItem : Content
        data object RemoveItem : Content
        data class Item(val itemId: String) : Content
    }

    private data class PanelRow(
        val text: Component,
        val action: TrackedItemManagerAction? = null,
    )
}

private fun styledManagerText(text: String, color: Int, bold: Boolean = false): MutableComponent =
    Component.literal(text).withStyle { style -> style.withColor(color and RGB_MASK).withBold(bold) }

private const val PANEL_MINIMUM_WIDTH = 130
private const val PANEL_ROW_HEIGHT = 11
private const val PANEL_ICON_ROW_HEIGHT = 16
private const val PANEL_ICON_TEXT_OFFSET = 16
private const val PANEL_ICON_SCALE = 0.75
private const val PANEL_TEXT_HEIGHT = 9
private const val PANEL_GAP = 4
private const val TEXT_BASE_COLOR = 0xFFFFFFFF.toInt()
private const val TITLE_COLOR = 0xFFFFFF55.toInt()
private const val ACTION_COLOR = 0xFF55FF55.toInt()
private const val DANGER_COLOR = 0xFFFF5555.toInt()
private const val MUTED_COLOR = 0xFFAAAAAA.toInt()
private const val PANEL_HOVER = 0x28FFFFFF
