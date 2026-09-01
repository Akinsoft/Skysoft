package com.skysoft.features.profit

import com.skysoft.config.ProfitTrackerPriceSource
import com.skysoft.features.inventory.TrackedItemSelectionAction
import com.skysoft.features.inventory.TrackedItemSelectionMode
import com.skysoft.features.inventory.TrackedItemSelectionPanel
import com.skysoft.features.inventory.trackedItemPresentation
import com.skysoft.gui.OverlayControlTooltips
import com.skysoft.utils.ColorUtilities.RGB_MASK
import com.skysoft.utils.ColorUtilities.withScaledAlpha
import com.skysoft.utils.animation.PanelFadeTransition
import com.skysoft.utils.gui.OverlayPanelStyle
import com.skysoft.utils.gui.Rect
import com.skysoft.utils.gui.TextFieldState
import com.skysoft.utils.renderables.primitives.ItemIconRenderable
import com.skysoft.utils.renderables.renderAt
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import net.minecraft.world.item.ItemStack
import org.lwjgl.glfw.GLFW

internal sealed interface ProfitTrackerControl {
    data object Period : ProfitTrackerControl
    data object PriceSource : ProfitTrackerControl
    data object Reset : ProfitTrackerControl
    data object CancelReset : ProfitTrackerControl
    data object ConfirmReset : ProfitTrackerControl
    data object More : ProfitTrackerControl
    data class ManageItem(
        val itemId: String,
        val stack: ItemStack,
        val formattedName: String,
    ) : ProfitTrackerControl
    data class ItemPriceSource(val itemId: String) : ProfitTrackerControl
    data class ModifyItem(val itemId: String, val amount: Long) : ProfitTrackerControl
    data class BeginCustomModification(val direction: Int) : ProfitTrackerControl
    data class ModifyItemField(val localMouseX: Int, val bounds: Rect) : ProfitTrackerControl
    data class ExcludeItem(val itemId: String) : ProfitTrackerControl
    data class RestoreItem(val itemId: String) : ProfitTrackerControl
    data class RemoveCustomItem(val itemId: String) : ProfitTrackerControl
    data object AddItem : ProfitTrackerControl
    data object ManageItems : ProfitTrackerControl
    data class ItemSelection(val action: TrackedItemSelectionAction) : ProfitTrackerControl
    data object ResetCustomizations : ProfitTrackerControl
}

internal data class ProfitTrackerPanelControl(
    val action: ProfitTrackerControl,
    val bounds: Rect,
    val tooltipLines: List<String> = emptyList(),
)

internal class ProfitTrackerItemPanel(
    nanoTime: () -> Long = System::nanoTime,
) {
    private var content: Content? = null
    private val transition = PanelFadeTransition(nanoTime)
    private val addItemPanel = TrackedItemSelectionPanel()
    val itemModifier = ProfitTrackerItemModifier()

    fun toggleOverview() {
        if (content != null && !transition.isClosing) close() else open(Content.Overview)
    }

    fun showOverview() = open(Content.Overview)

    fun openItem(itemId: String) = open(Content.Item(itemId))

    fun toggleItem(itemId: String) {
        if (content == Content.Item(itemId) && !transition.isClosing) close() else openItem(itemId)
    }

    fun beginAddingItem() {
        addItemPanel.reset()
        open(Content.AddItem)
    }

    fun beginManagingItem() {
        addItemPanel.reset()
        open(Content.ManageItem)
    }

    fun isAddingItems(): Boolean = content == Content.AddItem && !transition.isClosing

    fun isSelectingFromInventory(): Boolean =
        (content == Content.AddItem || content == Content.ManageItem) &&
            addItemPanel.mode == TrackedItemSelectionMode.INVENTORY && !transition.isClosing

    fun selectAddItemMode(mode: TrackedItemSelectionMode) = addItemPanel.selectMode(mode)

    fun focusSearch(localMouseX: Int) = addItemPanel.focusSearch(localMouseX)

    fun wasKeyPressHandled(
        event: KeyEvent,
        target: ProfitTrackerTarget,
        select: (String) -> Unit,
        modify: (String, Long) -> Unit,
    ): Boolean {
        val item = content as? Content.Item
        if (item != null && itemModifier.wasKeyPressHandled(event, item.itemId, modify)) return true
        val selecting = content == Content.AddItem || content == Content.ManageItem
        if (!selecting) return false
        val excludedItemIds = ProfitTracker.trackedItemIds(target).takeIf { isAddingItems() }.orEmpty()
        return addItemPanel.wasKeyPressHandled(event, { it !in excludedItemIds }, select)
    }

    fun wasCharTypedHandled(event: CharacterEvent): Boolean =
        itemModifier.wasCharTypedHandled(event) ||
            (content == Content.AddItem || content == Content.ManageItem) &&
            addItemPanel.wasCharTypedHandled(event)

    fun wasSearchScrollHandled(target: ProfitTrackerTarget, verticalAmount: Double): Boolean {
        if (content != Content.AddItem && content != Content.ManageItem) return false
        val excludedItemIds = ProfitTracker.trackedItemIds(target).takeIf { isAddingItems() }.orEmpty()
        return addItemPanel.wasScrollHandled(verticalAmount) { it !in excludedItemIds }
    }

    fun close() {
        if (content == null) return
        transition.hide()
    }

    fun clear() {
        content = null
        addItemPanel.clear()
        itemModifier.cancel()
        transition.reset()
    }

    fun render(
        context: GuiGraphicsExtractor,
        target: ProfitTrackerTarget,
        trackerWidth: Int,
        placeRight: Boolean,
        mouseX: Int,
        mouseY: Int,
    ): ProfitTrackerPanelControl? {
        val current = content ?: return null
        val opacity = transition.opacity()
        if (!transition.isVisible) {
            clear()
            return null
        }
        if (current == Content.AddItem || current == Content.ManageItem) {
            val excludedItemIds = ProfitTracker.trackedItemIds(target).takeIf { current == Content.AddItem }.orEmpty()
            return addItemPanel.render(
                context = context,
                title = if (current == Content.AddItem) "Add Item" else "Manage Item",
                trackerWidth = trackerWidth,
                placeRight = placeRight,
                mouseX = mouseX,
                mouseY = mouseY,
                opacity = opacity,
                interactive = transition.isInteractive,
                isSelectable = { it !in excludedItemIds },
            )?.let { control ->
                ProfitTrackerPanelControl(ProfitTrackerControl.ItemSelection(control.action), control.bounds)
            }
        }
        val rows = rows(current, target)
        val font = Minecraft.getInstance().font
        val width = maxOf(
            PANEL_MINIMUM_WIDTH,
            rows.maxOfOrNull { row ->
                font.width(row.text) + row.iconOffset +
                    (row.field?.let { PANEL_FIELD_GAP + it.width } ?: 0)
            } ?: 0,
        ) + OverlayPanelStyle.PADDING * 2
        val height = rows.sumOf(PanelRow::height) + OverlayPanelStyle.PADDING * 2
        val x = if (placeRight) trackerWidth + PANEL_GAP else -width - PANEL_GAP
        context.fill(x, 0, x + width, height, OverlayPanelStyle.BACKGROUND.withScaledAlpha(opacity))
        context.outline(x, 0, width, height, OverlayPanelStyle.OUTLINE.withScaledAlpha(opacity))
        var hovered: ProfitTrackerPanelControl? = null
        var rowY = OverlayPanelStyle.PADDING
        rows.forEach { row ->
            row.render(context, font, x, width, rowY, mouseX, mouseY, opacity, transition.isInteractive)
                ?.let { hovered = it }
            rowY += row.height
        }
        return hovered
    }

    private fun open(next: Content) {
        itemModifier.cancel()
        content = next
        transition.show()
    }

    private fun rows(content: Content, target: ProfitTrackerTarget): List<PanelRow> = when (content) {
        Content.Overview -> overviewRows(target)
        Content.AddItem, Content.ManageItem -> emptyList()
        is Content.Item -> itemRows(target, content.itemId)
    }

    private fun overviewRows(target: ProfitTrackerTarget): List<PanelRow> {
        val customizations = ProfitTrackerItemCustomizations.data(target)
        return buildList {
            add(PanelRow(styledText("Item Settings", TITLE_COLOR, bold = true)))
            add(PanelRow(styledText("Add Item", ACTION_COLOR), ProfitTrackerControl.AddItem))
            add(PanelRow(styledText("Manage Item", PRICE_COLOR), ProfitTrackerControl.ManageItems))
            customizations?.excludedItems?.takeIf { it.isNotEmpty() }?.let { excluded ->
                add(PanelRow(Component.empty(), heightOverride = PANEL_SECTION_GAP))
                add(PanelRow(styledText("Excluded", MUTED_COLOR)))
                excluded.forEach { itemId ->
                    val item = trackedItemPresentation(itemId)
                    add(
                        PanelRow(
                            item.component,
                            ProfitTrackerControl.RestoreItem(itemId),
                            listOf("§7Restore"),
                            item.stack,
                        ),
                    )
                }
            }
            customizations?.customItems?.takeIf { it.isNotEmpty() }?.let { customItems ->
                add(PanelRow(styledText("Added Items", MUTED_COLOR)))
                customItems.forEach { itemId ->
                    val item = trackedItemPresentation(itemId)
                    add(
                        PanelRow(
                            item.component,
                            ProfitTrackerControl.RemoveCustomItem(itemId),
                            listOf("§7Remove"),
                            item.stack,
                        ),
                    )
                }
            }
            add(
                PanelRow(
                    styledText("Reset Customizations", DANGER_COLOR),
                    ProfitTrackerControl.ResetCustomizations,
                ),
            )
        }
    }

    private fun itemRows(target: ProfitTrackerTarget, itemId: String): List<PanelRow> {
        val override = ProfitTrackerItemCustomizations.priceSourceOverride(target, itemId)
        val source = override?.toString() ?: "Tracker Default"
        val sources = listOf("Tracker Default") + ProfitTrackerPriceSource.entries.map { it.toString() }
        val presentation = trackedItemPresentation(itemId)
        return listOf(
            PanelRow(presentation.component, icon = presentation.stack),
            PanelRow(
                styledText("Price Source ", MUTED_COLOR).append(styledText("[$source]", PRICE_COLOR, bold = true)),
                ProfitTrackerControl.ItemPriceSource(itemId),
                OverlayControlTooltips.cycle("Item Price Source", sources, (override?.ordinal ?: -1) + 1),
            ),
            itemModifier.row(itemId),
            if (target.custom == null) {
                PanelRow(styledText("Exclude", DANGER_COLOR), ProfitTrackerControl.ExcludeItem(itemId))
            } else {
                PanelRow(styledText("Remove", DANGER_COLOR), ProfitTrackerControl.RemoveCustomItem(itemId))
            },
        )
    }

    private sealed interface Content {
        data object Overview : Content
        data object AddItem : Content
        data object ManageItem : Content
        data class Item(val itemId: String) : Content
    }

}

internal class ProfitTrackerItemModifier {
    private val field = TextFieldState(maxLength = QUANTITY_MAXIMUM_LENGTH)
    private var direction = 0

    fun begin(nextDirection: Int) {
        require(nextDirection == -1 || nextDirection == 1)
        direction = nextDirection
        field.text = ""
        field.focused = true
    }

    fun focus(localMouseX: Int, bounds: Rect) {
        field.focused = true
        field.placeCursorAt(localMouseX, bounds.x, bounds.width)
    }

    fun cancel() {
        direction = 0
        field.focused = false
    }

    fun wasKeyPressHandled(event: KeyEvent, itemId: String, modify: (String, Long) -> Unit): Boolean {
        if (direction == 0 || !field.focused) return false
        when (event.key()) {
            GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> field.text.toLongOrNull()
                ?.takeIf { it > 0L }
                ?.let { amount ->
                    modify(itemId, amount * direction)
                    cancel()
                }
            GLFW.GLFW_KEY_ESCAPE -> cancel()
            else -> {
                field.keyPressed(event)
                field.text = field.text.filter { it in '0'..'9' }
            }
        }
        return true
    }

    fun wasCharTypedHandled(event: CharacterEvent): Boolean {
        if (direction == 0 || !field.focused) return false
        if (event.codepointAsString().singleOrNull() in '0'..'9') field.charTyped(event)
        return true
    }

    fun row(itemId: String): PanelRow {
        if (direction != 0) {
            val color = if (direction < 0) DANGER_COLOR else ACTION_COLOR
            return PanelRow(
                styledText("Modify ", MUTED_COLOR).append(
                    styledText(if (direction < 0) "-" else "+", color, bold = true),
                ),
                heightOverride = QUANTITY_FIELD_HEIGHT,
                field = PanelRowField(field, QUANTITY_FIELD_WIDTH, color),
            )
        }
        val font = Minecraft.getInstance().font
        val text = styledText("Modify ", MUTED_COLOR)
        val controls = mutableListOf<PanelRowControl>()
        MODIFY_ITEM_AMOUNTS.forEachIndexed { index, amount ->
            if (index > 0) text.append(" ")
            val buttonDirection = if (index < MODIFY_MINUS_BUTTON_COUNT) -1 else 1
            val button = styledText(
                "[${amount?.let { if (it > 0) "+$it" else it.toString() } ?: if (buttonDirection < 0) "-" else "+"}]",
                if (buttonDirection < 0) DANGER_COLOR else ACTION_COLOR,
                bold = true,
            )
            val offset = font.width(text)
            text.append(button)
            controls += PanelRowControl(
                offset,
                font.width(button),
                amount?.let { ProfitTrackerControl.ModifyItem(itemId, it) }
                    ?: ProfitTrackerControl.BeginCustomModification(buttonDirection),
            )
        }
        return PanelRow(text, controls = controls)
    }
}

internal data class PanelRow(
    val text: Component,
    val action: ProfitTrackerControl? = null,
    val tooltipLines: List<String> = emptyList(),
    val icon: ItemStack? = null,
    val heightOverride: Int? = null,
    val controls: List<PanelRowControl> = emptyList(),
    val field: PanelRowField? = null,
) {
    val height: Int = heightOverride ?: if (icon == null) PANEL_ROW_HEIGHT else PANEL_ICON_ROW_HEIGHT
    val iconOffset: Int = if (icon == null) 0 else PANEL_ICON_TEXT_OFFSET
}

internal data class PanelRowControl(
    val offset: Int,
    val width: Int,
    val action: ProfitTrackerControl,
)

internal data class PanelRowField(
    val state: TextFieldState,
    val width: Int,
    val color: Int,
)

private fun PanelRow.render(
    context: GuiGraphicsExtractor,
    font: net.minecraft.client.gui.Font,
    panelX: Int,
    panelWidth: Int,
    y: Int,
    mouseX: Int,
    mouseY: Int,
    opacity: Double,
    interactive: Boolean,
): ProfitTrackerPanelControl? {
    val textX = panelX + OverlayPanelStyle.PADDING + iconOffset
    val button = controls.firstOrNull { control ->
        interactive && Rect(textX + control.offset, y, control.width, height).contains(mouseX, mouseY)
    }
    val fieldBounds = field?.let { Rect(textX + font.width(text) + PANEL_FIELD_GAP, y, it.width, height) }
    val rowHovered = interactive && action != null &&
        mouseX in panelX until panelX + panelWidth && mouseY in y until y + height
    val fieldHovered = interactive && fieldBounds?.contains(mouseX, mouseY) == true
    val hovered = when {
        button != null -> ProfitTrackerPanelControl(
            button.action,
            Rect(textX + button.offset, y, button.width, height),
        )
        fieldHovered -> ProfitTrackerPanelControl(
            ProfitTrackerControl.ModifyItemField(mouseX, requireNotNull(fieldBounds)),
            fieldBounds,
            listOf("§7Press Enter to confirm. Escape to cancel."),
        )
        rowHovered -> ProfitTrackerPanelControl(
            requireNotNull(action),
            Rect(panelX, y, panelWidth, height),
            tooltipLines,
        )
        else -> null
    }
    hovered?.bounds?.let { bounds ->
        context.fill(
            bounds.x,
            bounds.y,
            bounds.x + bounds.width,
            bounds.y + bounds.height,
            PANEL_HOVER.withScaledAlpha(opacity),
        )
    }
    if (opacity > ICON_VISIBILITY_THRESHOLD) {
        icon?.let { ItemIconRenderable(it, PANEL_ICON_SCALE).renderAt(context, panelX + OverlayPanelStyle.PADDING, y) }
    }
    context.text(
        font,
        text,
        textX,
        y + (height - PANEL_TEXT_HEIGHT) / 2,
        TEXT_BASE_COLOR.withScaledAlpha(opacity),
        false,
    )
    field?.let {
        val bounds = requireNotNull(fieldBounds)
        it.state.render(
            context,
            bounds.x,
            bounds.y,
            bounds.width,
            bounds.height,
            "Quantity...",
            alpha = opacity,
            textColor = it.color,
        )
    }
    return hovered
}

private fun styledText(text: String, color: Int, bold: Boolean = false): MutableComponent =
    Component.literal(text).withStyle { style -> style.withColor(color and RGB_MASK).withBold(bold) }

private val MODIFY_ITEM_AMOUNTS = listOf<Long?>(null, -4, -1, 1, 4, null)

private const val MODIFY_MINUS_BUTTON_COUNT = 3
private const val PANEL_MINIMUM_WIDTH = 130
private const val PANEL_ROW_HEIGHT = 11
private const val PANEL_SECTION_GAP = 6
private const val PANEL_ICON_ROW_HEIGHT = 16
private const val PANEL_TEXT_HEIGHT = 9
private const val PANEL_ICON_TEXT_OFFSET = 16
private const val PANEL_ICON_SCALE = 0.75
private const val ICON_VISIBILITY_THRESHOLD = 0.35
private const val PANEL_GAP = 4
private const val PANEL_FIELD_GAP = 3
private const val QUANTITY_MAXIMUM_LENGTH = 19
private const val QUANTITY_FIELD_WIDTH = 80
private const val QUANTITY_FIELD_HEIGHT = 18
private const val TEXT_BASE_COLOR = 0xFFFFFFFF.toInt()
private const val TITLE_COLOR = 0xFFFFFF55.toInt()
private const val MUTED_COLOR = 0xFFAAAAAA.toInt()
private const val ACTION_COLOR = 0xFF55FF55.toInt()
private const val PRICE_COLOR = 0xFFFFFF55.toInt()
private const val DANGER_COLOR = 0xFFFF5555.toInt()
private const val PANEL_HOVER = 0x28FFFFFF
