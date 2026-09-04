package com.skysoft.features.profit

import com.skysoft.config.ProfitTrackerPriceSource
import com.skysoft.features.inventory.TrackedItemQuantityAction
import com.skysoft.features.inventory.TrackedItemQuantityModifier
import com.skysoft.features.inventory.TrackedItemSelectionAction
import com.skysoft.features.inventory.TrackedItemSelectionMode
import com.skysoft.features.inventory.TrackedItemSelectionPanel
import com.skysoft.features.inventory.trackedItemPresentation
import com.skysoft.gui.OverlayControlTooltips
import com.skysoft.gui.tooltip.SkysoftNativeTooltip
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
import net.minecraft.world.item.ItemStack

internal sealed interface ProfitTrackerControl {
    data object Period : ProfitTrackerControl
    data object PriceSource : ProfitTrackerControl
    data object Reset : ProfitTrackerControl
    data object CancelReset : ProfitTrackerControl
    data object ConfirmReset : ProfitTrackerControl
    data object More : ProfitTrackerControl
    data class PestBreakdown(val rows: List<SkysoftNativeTooltip.ItemRow>) : ProfitTrackerControl
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
    val itemModifier = TrackedItemQuantityModifier()
    var isHovered = false
        private set

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
        if (item != null && itemModifier.wasKeyPressHandled(event) { amount -> modify(item.itemId, amount) }) return true
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
        addItemPanel.clear()
        itemModifier.cancel()
        transition.hide()
    }

    fun clear() {
        content = null
        addItemPanel.clear()
        itemModifier.cancel()
        isHovered = false
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
            val control = addItemPanel.render(
                context = context,
                title = if (current == Content.AddItem) "Add Item" else "Manage Item",
                trackerWidth = trackerWidth,
                placeRight = placeRight,
                mouseX = mouseX,
                mouseY = mouseY,
                opacity = opacity,
                interactive = transition.isInteractive,
                isSelectable = { it !in excludedItemIds },
            )
            isHovered = addItemPanel.isHovered
            return control?.let {
                ProfitTrackerPanelControl(ProfitTrackerControl.ItemSelection(it.action), it.bounds)
            }
        }
        val rows = rows(current, target)
        val font = Minecraft.getInstance().font
        val width = maxOf(
            PANEL_MINIMUM_WIDTH,
            rows.maxOfOrNull { row ->
                row.quantityModifier?.width() ?: font.width(row.text) + row.iconOffset
            } ?: 0,
        ) + OverlayPanelStyle.PADDING * 2
        val height = rows.sumOf(PanelRow::height) + OverlayPanelStyle.PADDING * 2
        val x = if (placeRight) trackerWidth + PANEL_GAP else -width - PANEL_GAP
        isHovered = Rect(x, 0, width, height).contains(mouseX, mouseY)
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
            PanelRow(Component.empty(), quantityItemId = itemId, quantityModifier = itemModifier),
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

internal data class PanelRow(
    val text: Component,
    val action: ProfitTrackerControl? = null,
    val tooltipLines: List<String> = emptyList(),
    val icon: ItemStack? = null,
    val heightOverride: Int? = null,
    val quantityItemId: String? = null,
    val quantityModifier: TrackedItemQuantityModifier? = null,
) {
    val height: Int
        get() = quantityModifier?.height ?: heightOverride ?: if (icon == null) PANEL_ROW_HEIGHT else PANEL_ICON_ROW_HEIGHT
    val iconOffset: Int = if (icon == null) 0 else PANEL_ICON_TEXT_OFFSET
}

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
    quantityModifier?.let { modifier ->
        val itemId = requireNotNull(quantityItemId)
        return modifier.render(context, panelX, y, mouseX, mouseY, opacity, interactive)?.let { control ->
            val action = when (val quantityAction = control.action) {
                is TrackedItemQuantityAction.Modify ->
                    ProfitTrackerControl.ModifyItem(itemId, quantityAction.amount)
                is TrackedItemQuantityAction.BeginCustom ->
                    ProfitTrackerControl.BeginCustomModification(quantityAction.direction)
                is TrackedItemQuantityAction.Field ->
                    ProfitTrackerControl.ModifyItemField(quantityAction.localMouseX, quantityAction.bounds)
            }
            ProfitTrackerPanelControl(action, control.bounds, control.tooltipLines)
        }
    }
    val textX = panelX + OverlayPanelStyle.PADDING + iconOffset
    val rowHovered = interactive && action != null &&
        mouseX in panelX until panelX + panelWidth && mouseY in y until y + height
    val hovered = action?.takeIf { rowHovered }?.let {
        ProfitTrackerPanelControl(it, Rect(panelX, y, panelWidth, height), tooltipLines)
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
    return hovered
}

private fun styledText(text: String, color: Int, bold: Boolean = false): MutableComponent =
    Component.literal(text).withStyle { style -> style.withColor(color and RGB_MASK).withBold(bold) }

private const val PANEL_MINIMUM_WIDTH = 130
private const val PANEL_ROW_HEIGHT = 11
private const val PANEL_SECTION_GAP = 6
private const val PANEL_ICON_ROW_HEIGHT = 16
private const val PANEL_TEXT_HEIGHT = 9
private const val PANEL_ICON_TEXT_OFFSET = 16
private const val PANEL_ICON_SCALE = 0.75
private const val ICON_VISIBILITY_THRESHOLD = 0.35
private const val PANEL_GAP = 4
private const val TEXT_BASE_COLOR = 0xFFFFFFFF.toInt()
private const val TITLE_COLOR = 0xFFFFFF55.toInt()
private const val MUTED_COLOR = 0xFFAAAAAA.toInt()
private const val ACTION_COLOR = 0xFF55FF55.toInt()
private const val PRICE_COLOR = 0xFFFFFF55.toInt()
private const val DANGER_COLOR = 0xFFFF5555.toInt()
private const val PANEL_HOVER = 0x28FFFFFF
