package com.skysoft.features.inventory.sacks

import com.skysoft.data.ProfileStorageApi
import com.skysoft.data.skyblock.SkyBlockDataRepository
import com.skysoft.features.inventory.InventoryTrackerFrame
import com.skysoft.features.inventory.InventoryTrackerLayout
import com.skysoft.features.inventory.TrackedItemManagerAction
import com.skysoft.gui.OverlayControlArea
import com.skysoft.gui.tooltip.SkysoftNativeTooltip
import com.skysoft.utils.MinecraftClient
import com.skysoft.utils.NumberUtilities.addSeparators
import com.skysoft.utils.TextUtilities.truncateLegacyText
import com.skysoft.utils.gui.OverlayItemRowStyle
import com.skysoft.utils.gui.OverlayTextStyle
import com.skysoft.utils.gui.Rect
import com.skysoft.utils.render.LegacyTextRenderer
import com.skysoft.utils.renderables.GuiRenderable
import com.skysoft.utils.renderables.primitives.ItemIconRenderable
import com.skysoft.utils.renderables.renderAt
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.world.item.ItemStack

internal fun renderSackHud(context: GuiGraphicsExtractor) {
    if (!isSackHudVisible()) {
        sackHudItemPanel.clear()
        clearSackHudInteraction()
        return
    }
    val inventoryOpen = MinecraftClient.screen() is AbstractContainerScreen<*>
    if (!inventoryOpen) sackHudItemPanel.clear()
    val renderable = buildSackHudRenderable(inventoryOpen)
    val frame = InventoryTrackerFrame(sackHudConfig.position, renderable.width, renderable.height)
    sackHudHoveredControl = frame.render(context) { mouseX, mouseY, placePanelRight ->
        val trackerControl = renderable.renderInteractive(context, mouseX, mouseY)
        sackHudItemPanel.render(
            context,
            renderable.width,
            placePanelRight,
            mouseX ?: Int.MIN_VALUE,
            mouseY ?: Int.MIN_VALUE,
        )?.let { control ->
            val action = when (val action = control.action) {
                TrackedItemManagerAction.AddItems -> SackHudControl.AddItems
                TrackedItemManagerAction.RemoveItems -> SackHudControl.RemoveItems
                is TrackedItemManagerAction.ItemSelection -> SackHudControl.ItemSelection(action.action)
                is TrackedItemManagerAction.Quantity -> error("Sacks Tracker does not edit quantities")
            }
            OverlayControlArea(action, control.bounds)
        } ?: trackerControl
    }
    sackHudHovered = frame.isHovered
    if (frame.interactive) sackHudHoveredControl?.let { control ->
        context.nextStratum()
        val itemId = (control.action as? SackHudControl.Item)?.itemId
        if (itemId != null) {
            val entry = trackedSackHudItem(itemId)
            val removingItems = sackHudItemPanel.isRemovingItems()
            SkysoftNativeTooltip.setItemActionForNextFrame(
                context,
                entry.stack ?: ItemStack.EMPTY,
                "§eRemove".takeIf { removingItems },
                entry.name,
                frame.screenMouseX,
                frame.screenMouseY,
                actionLines = sackItemActionLines().takeUnless { removingItems }.orEmpty(),
            )
        } else {
            SkysoftNativeTooltip.setForNextFrame(
                context,
                control.tooltipLines,
                frame.screenMouseX,
                frame.screenMouseY,
                scrollable = false,
            )
        }
    }
}

internal fun buildSackHudRenderable(inventoryOpen: Boolean): SackHudRenderable {
    sackHudScrollOffset = sackHudScrollOffset.coerceIn(0, sackHudMaximumScrollOffset(sackHudConfig.trackedItems.size))
    val displayed = sackHudConfig.trackedItems
        .asSequence()
        .drop(sackHudScrollOffset)
        .take(sackHudMaximumDisplayedItems())
        .map(::trackedSackHudItem)
        .toList()
    return SackHudRenderable(
        items = displayed,
        hiddenAbove = sackHudScrollOffset,
        hiddenBelow = (sackHudConfig.trackedItems.size - sackHudScrollOffset - displayed.size).coerceAtLeast(0),
        showTitle = sackHudConfig.details.showTitle,
        showItemNames = sackHudConfig.details.showItemNames,
        showIcons = sackHudConfig.details.showItemIcons,
        background = sackHudConfig.details.showBackground,
        inventoryOpen = inventoryOpen,
    )
}

internal fun trackedSackHudItem(itemId: String): SackHudItem {
    val key = SkyBlockDataRepository.itemKey(itemId)
    val entry = SkyBlockDataRepository.entry(key)
    val sackData = ProfileStorageApi.storage.sackContents[itemId]
    val name = entry?.formattedDisplayName
        ?: sackData?.displayName?.takeIf { it.isNotBlank() }
        ?: itemId
    return SackHudItem(
        itemId = itemId,
        name = name,
        amount = sackData?.amount ?: 0L,
        exact = sackData?.exact == true,
        known = sackData != null,
        highlighted = sackHudChangeHighlights.isHighlighted(itemId),
        stack = SkyBlockDataRepository.displayStack(key),
    )
}

internal class SackHudRenderable(
    items: List<SackHudItem>,
    hiddenAbove: Int,
    hiddenBelow: Int,
    showTitle: Boolean,
    private val showItemNames: Boolean,
    private val showIcons: Boolean,
    background: Boolean,
    inventoryOpen: Boolean,
) : GuiRenderable {
    private val compactRows = !showItemNames
    private val itemNames = items.map { item ->
        item.name.truncateLegacyText(MAXIMUM_ITEM_NAME_LENGTH).takeIf { showItemNames }.orEmpty()
    }
    private val itemNameColumnWidth = itemNames.maxOfOrNull(LegacyTextRenderer::width) ?: 0
    private val rows = items.zip(itemNames) { item, name ->
        SackHudRow(
            item = item,
            name = name,
            nameColumnWidth = itemNameColumnWidth,
            value = item.displayAmount(),
            reserveIcon = showIcons,
            compact = compactRows,
        )
    }
    private val emptyText = if (sackHudConfig.trackedItems.isEmpty()) {
        "§7No tracked sack items."
    } else {
        "§7Loading item data..."
    }
    private val layout = InventoryTrackerLayout(
        title = "Sacks Tracker",
        emptyText = emptyText,
        indicatorText = OverlayTextStyle.hiddenRowsIndicator(hiddenAbove, hiddenBelow),
        rowWidths = rows.map(SackHudRow::width),
        minimumWidth = if (compactRows) COMPACT_MINIMUM_WIDTH else MINIMUM_WIDTH,
        showTitle = showTitle,
        background = background,
        inventoryOpen = inventoryOpen,
    )
    override val width: Int get() = layout.width
    override val height: Int get() = layout.height

    override fun render(context: GuiGraphicsExtractor) {
        renderInteractive(context, null, null)
    }

    fun renderInteractive(
        context: GuiGraphicsExtractor,
        mouseX: Int?,
        mouseY: Int?,
    ): OverlayControlArea<SackHudControl>? = layout.render(
        context, mouseX, mouseY, SackHudControl.More, listOf("§7Manage tracked items."),
    ) { index, left, right, y ->
        rows[index].renderInteractive(context, left, right, y, mouseX, mouseY)
    }
}

private data class SackHudRow(
    val item: SackHudItem,
    val name: String,
    val nameColumnWidth: Int,
    val value: String,
    val reserveIcon: Boolean,
    val compact: Boolean,
) {
    private val iconWidth = if (reserveIcon) OverlayItemRowStyle.ICON_TEXT_OFFSET else 0
    private val valueWidth = maxOf(
        LegacyTextRenderer.width(value),
        LegacyTextRenderer.width(item.displayAmount(showHighlight = true)),
    )
    private val afterIconGap = when {
        !reserveIcon -> 0
        compact || name.isEmpty() -> COMPACT_ICON_VALUE_GAP
        else -> 0
    }
    private val nameValueGap = if (nameColumnWidth > 0) OverlayItemRowStyle.QUANTITY_COLUMN_GAP else 0
    private val valueXOffset = iconWidth + afterIconGap + nameColumnWidth + nameValueGap
    val width: Int = valueXOffset + valueWidth

    fun renderInteractive(
        context: GuiGraphicsExtractor,
        left: Int,
        right: Int,
        y: Int,
        mouseX: Int?,
        mouseY: Int?,
    ): OverlayControlArea<SackHudControl>? {
        val bounds = Rect(left, y, (right - left).coerceAtLeast(1), OverlayItemRowStyle.HEIGHT)
        val hovered = bounds.contains(mouseX, mouseY)
        if (hovered) OverlayTextStyle.drawControlHover(context, bounds, 1.0)
        if (reserveIcon) {
            item.stack?.let { ItemIconRenderable(it, OverlayItemRowStyle.ICON_SCALE).renderAt(context, left, y) }
        }
        if (name.isNotEmpty()) {
            LegacyTextRenderer.draw(context, name, left + iconWidth, y + OverlayItemRowStyle.TEXT_Y_OFFSET)
        }
        LegacyTextRenderer.draw(context, value, left + valueXOffset, y + OverlayItemRowStyle.TEXT_Y_OFFSET)
        return OverlayControlArea<SackHudControl>(SackHudControl.Item(item.itemId), bounds).takeIf { hovered }
    }
}

private fun SackHudItem.displayAmount(showHighlight: Boolean = highlighted): String {
    val amountText = when {
        !known -> "§8?"
        showHighlight -> "§a§l${amount.addSeparators()}"
        !exact -> "§e~${amount.addSeparators()}"
        else -> "§e${amount.addSeparators()}"
    }
    return "§7x$amountText"
}

internal data class SackHudItem(
    val itemId: String,
    val name: String,
    val amount: Long,
    val exact: Boolean,
    val known: Boolean,
    val highlighted: Boolean,
    val stack: ItemStack?,
)

private const val MAXIMUM_ITEM_NAME_LENGTH = 30
private const val MINIMUM_WIDTH = 160
private const val COMPACT_MINIMUM_WIDTH = 36
private const val COMPACT_ICON_VALUE_GAP = 2
