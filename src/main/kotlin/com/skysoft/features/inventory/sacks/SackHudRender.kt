package com.skysoft.features.inventory.sacks

import com.skysoft.data.ProfileStorageApi
import com.skysoft.data.skyblock.SkyBlockDataRepository
import com.skysoft.features.inventory.InventoryOverlayInput
import com.skysoft.gui.OverlayControlArea
import com.skysoft.gui.OverlayControlMouse
import com.skysoft.gui.tooltip.SkysoftNativeTooltip
import com.skysoft.utils.MinecraftClient
import com.skysoft.utils.NumberUtilities.addSeparators
import com.skysoft.utils.TextUtilities.truncateLegacyText
import com.skysoft.utils.gui.OverlayItemRowStyle
import com.skysoft.utils.gui.OverlayPanelStyle
import com.skysoft.utils.gui.OverlayTextStyle
import com.skysoft.utils.gui.Rect
import com.skysoft.utils.render.LegacyTextRenderer
import com.skysoft.utils.renderables.GuiRenderable
import com.skysoft.utils.renderables.primitives.ItemIconRenderable
import com.skysoft.utils.renderables.renderAt
import com.skysoft.utils.renderables.withIsolatedPose
import kotlin.math.floor
import kotlin.math.roundToInt
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.world.item.ItemStack

internal fun renderSackHud(context: GuiGraphicsExtractor) {
    if (!isSackHudVisible()) {
        sackHudItemPanel.clear()
        clearSackHudInteraction()
        return
    }
    val minecraft = Minecraft.getInstance()
    val inventoryScreen = MinecraftClient.screen(minecraft) as? AbstractContainerScreen<*>
    val inventoryOpen = inventoryScreen != null
    if (!inventoryOpen) sackHudItemPanel.clear()
    val renderable = buildSackHudRenderable(inventoryOpen)
    if (renderable.width <= 0 || renderable.height <= 0) {
        clearSackHudInteraction()
        return
    }
    val window = minecraft.window
    val mouseX = minecraft.mouseHandler.getScaledXPos(window).toInt()
    val mouseY = minecraft.mouseHandler.getScaledYPos(window).toInt()
    val (normalMouseX, normalMouseY) = OverlayControlMouse.normalPoint(mouseX, mouseY)
    val (screenMouseX, screenMouseY) = OverlayControlMouse.screenPoint(mouseX, mouseY)
    val interactive = inventoryScreen != null &&
        !InventoryOverlayInput.isPointCovered(inventoryScreen, screenMouseX.toDouble(), screenMouseY.toDouble())
    val scale = sackHudConfig.position.effectiveScale
    val x = sackHudConfig.position.getAbsX0AllowingOverflow(0)
    val y = sackHudConfig.position.getAbsY0AllowingOverflow(0)
    val localMouseX = floor((normalMouseX - x) / scale).toInt()
    val localMouseY = floor((normalMouseY - y) / scale).toInt()
    val placePanelRight = x + ((renderable.width + SIDE_PANEL_ESTIMATED_WIDTH) * scale).roundToInt() <=
        window.guiScaledWidth

    context.nextStratum()
    val localControl = context.withIsolatedPose {
        pose().translate(x.toFloat(), y.toFloat())
        pose().scale(scale, scale)
        val trackerControl = renderable.renderInteractive(
            context,
            localMouseX.takeIf { interactive },
            localMouseY.takeIf { interactive },
        )
        sackHudItemPanel.render(
            context,
            renderable.width,
            placePanelRight,
            localMouseX.takeIf { interactive } ?: Int.MIN_VALUE,
            localMouseY.takeIf { interactive } ?: Int.MIN_VALUE,
        ) ?: trackerControl
    }

    sackHudHovered = interactive &&
        localMouseX in 0 until renderable.width &&
        localMouseY in 0 until renderable.height
    sackHudHoveredControl = localControl?.let { control ->
        OverlayControlArea(
            action = control.action,
            bounds = Rect(
                x = x + (control.bounds.x * scale).roundToInt(),
                y = y + (control.bounds.y * scale).roundToInt(),
                width = (control.bounds.width * scale).roundToInt().coerceAtLeast(1),
                height = (control.bounds.height * scale).roundToInt().coerceAtLeast(1),
            ),
            tooltipLines = control.tooltipLines,
        )
    }
    if (interactive) sackHudHoveredControl?.let { control ->
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
                screenMouseX,
                screenMouseY,
                actionLines = sackItemActionLines().takeUnless { removingItems }.orEmpty(),
            )
        } else {
            SkysoftNativeTooltip.setForNextFrame(
                context,
                control.tooltipLines,
                screenMouseX,
                screenMouseY,
                scrollable = false,
            )
        }
    }
}

internal fun buildSackHudRenderable(inventoryOpen: Boolean): SackHudRenderable {
    val maximumItems = sackHudConfig.settings.maximumItems.coerceIn(1, SACK_HUD_MAXIMUM_DISPLAY_ITEMS)
    val maximumOffset = (sackHudConfig.trackedItems.size - maximumItems).coerceAtLeast(0)
    sackHudScrollOffset = sackHudScrollOffset.coerceIn(0, maximumOffset)
    val displayed = sackHudConfig.trackedItems
        .asSequence()
        .drop(sackHudScrollOffset)
        .take(maximumItems)
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
        highlighted = isSackHudAmountHighlighted(itemId),
        stack = SkyBlockDataRepository.displayStack(key),
    )
}

private fun isSackHudAmountHighlighted(itemId: String): Boolean =
    sackHudChangeHighlights.isHighlighted(itemId)

internal class SackHudRenderable(
    items: List<SackHudItem>,
    private val hiddenAbove: Int,
    private val hiddenBelow: Int,
    private val showTitle: Boolean,
    private val showItemNames: Boolean,
    private val showIcons: Boolean,
    private val background: Boolean,
    private val inventoryOpen: Boolean,
) : GuiRenderable {
    private val padding = if (background) OverlayPanelStyle.PADDING else 0
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
            stack = item.stack,
            reserveIcon = showIcons,
            compact = compactRows,
        )
    }
    private val emptyText = if (sackHudConfig.trackedItems.isEmpty()) {
        "§7No tracked sack items."
    } else {
        "§7Loading item data..."
    }
    private val indicatorText = when {
        hiddenAbove <= 0 && hiddenBelow <= 0 -> ""
        else -> buildList {
            if (hiddenAbove > 0) add("$hiddenAbove above")
            if (hiddenBelow > 0) add("$hiddenBelow more")
        }.joinToString(" §8• §7", prefix = "§7", postfix = "...")
    }
    private val moreLine = "§7..."
    private val titleText = OverlayTextStyle.title("Sacks Tracker")
    private val contentWidth = maxOf(
        if (compactRows) COMPACT_MINIMUM_WIDTH else MINIMUM_WIDTH,
        if (showTitle) LegacyTextRenderer.width(titleText) else 0,
        rows.maxOfOrNull(SackHudRow::width) ?: LegacyTextRenderer.width(emptyText),
        LegacyTextRenderer.width(indicatorText),
        if (inventoryOpen) LegacyTextRenderer.width(moreLine) else 0,
    )

    override val width: Int = contentWidth + padding * 2
    override val height: Int = padding * 2 +
        (if (showTitle) OverlayTextStyle.TITLE_HEIGHT else 0) +
        (if (rows.isEmpty()) OverlayTextStyle.ROW_HEIGHT else rows.size * OverlayItemRowStyle.HEIGHT) +
        (if (indicatorText.isEmpty()) 0 else OverlayTextStyle.ROW_HEIGHT) +
        (if (inventoryOpen) CONTROL_ROW_HEIGHT else 0)

    override fun render(context: GuiGraphicsExtractor) {
        renderInteractive(context, null, null)
    }

    fun renderInteractive(context: GuiGraphicsExtractor, mouseX: Int?, mouseY: Int?): LocalSackHudControl? {
        if (background) OverlayPanelStyle.draw(context, 0, 0, width, height)
        var y = padding
        if (showTitle) {
            LegacyTextRenderer.draw(context, titleText, padding, y)
            y += OverlayTextStyle.TITLE_HEIGHT
        }
        var hovered: LocalSackHudControl? = null
        if (rows.isEmpty()) {
            LegacyTextRenderer.draw(context, emptyText, padding, y)
            y += OverlayTextStyle.ROW_HEIGHT
        } else {
            rows.forEach { row ->
                hovered = row.renderInteractive(context, padding, width - padding, y, mouseX, mouseY) ?: hovered
                y += OverlayItemRowStyle.HEIGHT
            }
        }
        if (indicatorText.isNotEmpty()) {
            LegacyTextRenderer.draw(context, indicatorText, padding, y)
            y += OverlayTextStyle.ROW_HEIGHT
        }
        if (inventoryOpen) {
            hovered = renderMoreControl(context, y, mouseX, mouseY) ?: hovered
        }
        return hovered
    }

    private fun renderMoreControl(
        context: GuiGraphicsExtractor,
        y: Int,
        mouseX: Int?,
        mouseY: Int?,
    ): LocalSackHudControl? {
        val width = LegacyTextRenderer.width(moreLine)
        val bounds = Rect(this.width - padding - width, y, width, CONTROL_ROW_HEIGHT)
        val hovered = mouseX != null && mouseY != null && bounds.contains(mouseX, mouseY)
        if (hovered) OverlayTextStyle.drawControlHover(context, bounds, 1.0)
        LegacyTextRenderer.draw(context, moreLine, bounds.x, y + CONTROL_TEXT_Y_OFFSET)
        return LocalSackHudControl(
            action = SackHudControl.More,
            bounds = bounds,
            tooltipLines = listOf("§7Manage tracked items."),
        ).takeIf { hovered }
    }
}

private data class SackHudRow(
    val item: SackHudItem,
    val name: String,
    val nameColumnWidth: Int,
    val value: String,
    val stack: ItemStack?,
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
    ): LocalSackHudControl? {
        val bounds = Rect(left, y, (right - left).coerceAtLeast(1), OverlayItemRowStyle.HEIGHT)
        val hovered = mouseX != null && mouseY != null && bounds.contains(mouseX, mouseY)
        if (hovered) OverlayTextStyle.drawControlHover(context, bounds, 1.0)
        if (reserveIcon) {
            stack?.let { ItemIconRenderable(it, OverlayItemRowStyle.ICON_SCALE).renderAt(context, left, y) }
        }
        if (name.isNotEmpty()) {
            LegacyTextRenderer.draw(context, name, left + iconWidth, y + OverlayItemRowStyle.TEXT_Y_OFFSET)
        }
        LegacyTextRenderer.draw(context, value, left + valueXOffset, y + OverlayItemRowStyle.TEXT_Y_OFFSET)
        return LocalSackHudControl(SackHudControl.Item(item.itemId), bounds, emptyList()).takeIf { hovered }
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

internal data class LocalSackHudControl(
    val action: SackHudControl,
    val bounds: Rect,
    val tooltipLines: List<String>,
)

private const val MAXIMUM_ITEM_NAME_LENGTH = 30
private const val MINIMUM_WIDTH = 160
private const val COMPACT_MINIMUM_WIDTH = 36
private const val CONTROL_ROW_HEIGHT = 13
private const val CONTROL_TEXT_Y_OFFSET = 1
private const val COMPACT_ICON_VALUE_GAP = 2
private const val SIDE_PANEL_ESTIMATED_WIDTH = 190
