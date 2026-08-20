package com.skysoft.features.inventory.sacks

import com.skysoft.config.SkysoftConfigGui
import com.skysoft.data.ProfileStorageApi
import com.skysoft.data.skyblock.SkyBlockDataRepository
import com.skysoft.features.inventory.InventoryOverlayInput
import com.skysoft.gui.OverlayControlArea
import com.skysoft.gui.OverlayControlMouse
import com.skysoft.gui.tooltip.SkysoftNativeTooltip
import com.skysoft.utils.MinecraftClient
import com.skysoft.utils.NumberUtilities.addSeparators
import com.skysoft.utils.TextUtilities.truncateLegacyText
import com.skysoft.utils.gui.OverlayPanelStyle
import com.skysoft.utils.gui.Rect
import com.skysoft.utils.render.LegacyTextRenderer
import com.skysoft.utils.renderables.GuiRenderable
import com.skysoft.utils.renderables.primitives.ItemIconRenderable
import com.skysoft.utils.renderables.renderAt
import kotlin.math.floor
import kotlin.math.roundToInt
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.world.item.ItemStack

internal fun renderSackHud(context: GuiGraphicsExtractor) {
    if (!isSackHudVisible()) {
        if (!sackHudConfig.enabled) sackHudAddingItem = false
        clearSackHudInteraction()
        return
    }
    val minecraft = Minecraft.getInstance()
    val inventoryScreen = MinecraftClient.screen(minecraft) as? AbstractContainerScreen<*>
    val inventoryOpen = inventoryScreen != null
    if (!inventoryOpen) sackHudAddingItem = false
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
    val scaledWidth = (renderable.width * scale).roundToInt()
    val scaledHeight = (renderable.height * scale).roundToInt()
    val x = sackHudConfig.position.getAbsX0AllowingOverflow(scaledWidth)
    val y = sackHudConfig.position.getAbsY0AllowingOverflow(scaledHeight)
    val localMouseX = floor((normalMouseX - x) / scale).toInt()
    val localMouseY = floor((normalMouseY - y) / scale).toInt()

    context.nextStratum()
    context.pose().pushMatrix()
    context.pose().translate(x.toFloat(), y.toFloat())
    context.pose().scale(scale, scale)
    val localControl = renderable.renderInteractive(
        context,
        localMouseX.takeIf { interactive },
        localMouseY.takeIf { interactive },
    )
    context.pose().popMatrix()

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
            SkysoftNativeTooltip.setItemActionForNextFrame(
                context,
                entry.stack ?: ItemStack.EMPTY,
                null,
                entry.name,
                screenMouseX,
                screenMouseY,
                actionLines = buildList {
                    add("§eLeft-click §7to open Bazaar")
                    add("§eRight-click §7to remove from Sack HUD")
                    if (SkysoftConfigGui.config().inventory.itemList.enabled) {
                        add("§eMiddle-click §7to open Item List")
                    }
                },
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
        showIcons = sackHudConfig.details.showItemIcons,
        background = sackHudConfig.details.showBackground,
        inventoryOpen = inventoryOpen,
        addingItem = sackHudAddingItem && inventoryOpen,
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

private fun isSackHudAmountHighlighted(itemId: String): Boolean {
    val expiresAt = sackHudChangeHighlights[itemId] ?: return false
    if (System.currentTimeMillis() >= expiresAt) {
        sackHudChangeHighlights.remove(itemId)
        return false
    }
    return true
}

internal class SackHudRenderable(
    items: List<SackHudItem>,
    private val hiddenAbove: Int,
    private val hiddenBelow: Int,
    private val showIcons: Boolean,
    private val background: Boolean,
    private val inventoryOpen: Boolean,
    private val addingItem: Boolean,
) : GuiRenderable {
    private val padding = if (background) OverlayPanelStyle.PADDING else 0
    private val rows = items.map { item ->
        SackHudRow(
            item = item,
            name = item.name.truncateLegacyText(MAXIMUM_ITEM_NAME_LENGTH),
            value = item.displayAmount(),
            stack = item.stack,
            reserveIcon = showIcons,
        )
    }
    private val emptyText = if (sackHudConfig.trackedItems.isEmpty()) {
        "§7No tracked sack items."
    } else {
        "§7Loading item data..."
    }
    private val indicatorText = buildList {
        if (hiddenAbove > 0) add("$hiddenAbove above")
        if (hiddenBelow > 0) add("$hiddenBelow more")
    }.joinToString(" §8• §7", prefix = "§7", postfix = if (hiddenAbove > 0 || hiddenBelow > 0) "..." else "")
    private val addLine = if (addingItem) "§a§l[+ Click Inventory Item]" else "§e[+ Add Item]"
    private val contentWidth = maxOf(
        MINIMUM_WIDTH,
        LegacyTextRenderer.width("§e§lSack HUD"),
        rows.maxOfOrNull(SackHudRow::width) ?: LegacyTextRenderer.width(emptyText),
        LegacyTextRenderer.width(indicatorText),
        if (inventoryOpen) LegacyTextRenderer.width(addLine) else 0,
    )

    override val width: Int = contentWidth + padding * 2
    override val height: Int = padding * 2 + TITLE_HEIGHT +
        (if (rows.isEmpty()) TEXT_ROW_HEIGHT else rows.size * ITEM_ROW_HEIGHT) +
        (if (indicatorText.isEmpty()) 0 else TEXT_ROW_HEIGHT) +
        (if (inventoryOpen) CONTROL_ROW_HEIGHT else 0)

    override fun render(context: GuiGraphicsExtractor) {
        renderInteractive(context, null, null)
    }

    fun renderInteractive(context: GuiGraphicsExtractor, mouseX: Int?, mouseY: Int?): LocalSackHudControl? {
        if (background) OverlayPanelStyle.draw(context, 0, 0, width, height)
        var y = padding
        LegacyTextRenderer.draw(context, "§e§lSack HUD", padding, y)
        y += TITLE_HEIGHT
        var hovered: LocalSackHudControl? = null
        if (rows.isEmpty()) {
            LegacyTextRenderer.draw(context, emptyText, padding, y)
            y += TEXT_ROW_HEIGHT
        } else {
            rows.forEach { row ->
                hovered = row.renderInteractive(context, padding, width - padding, y, mouseX, mouseY) ?: hovered
                y += ITEM_ROW_HEIGHT
            }
        }
        if (indicatorText.isNotEmpty()) {
            LegacyTextRenderer.draw(context, indicatorText, (width - LegacyTextRenderer.width(indicatorText)) / 2, y)
            y += TEXT_ROW_HEIGHT
        }
        if (inventoryOpen) {
            hovered = renderAddControl(context, y, mouseX, mouseY) ?: hovered
        }
        return hovered
    }

    private fun renderAddControl(
        context: GuiGraphicsExtractor,
        y: Int,
        mouseX: Int?,
        mouseY: Int?,
    ): LocalSackHudControl? {
        val bounds = Rect(padding, y, LegacyTextRenderer.width(addLine), CONTROL_ROW_HEIGHT)
        val hovered = mouseX != null && mouseY != null && bounds.contains(mouseX, mouseY)
        if (hovered) {
            context.fill(bounds.x, bounds.y, bounds.x + bounds.width, bounds.y + bounds.height, CONTROL_HOVER_COLOR)
        }
        LegacyTextRenderer.draw(context, addLine, bounds.x, y + CONTROL_TEXT_Y_OFFSET)
        return LocalSackHudControl(
            action = SackHudControl.AddItem,
            bounds = bounds,
            tooltipLines = listOf(
                if (addingItem) {
                    "§7Click an inventory item to track its sack amount."
                } else {
                    "§7Click, then click an inventory item to add it."
                },
            ),
        ).takeIf { hovered }
    }
}

private data class SackHudRow(
    val item: SackHudItem,
    val name: String,
    val value: String,
    val stack: ItemStack?,
    val reserveIcon: Boolean,
) {
    private val contentOffset = if (reserveIcon) ITEM_TEXT_OFFSET else 0
    val width: Int = contentOffset + LegacyTextRenderer.width(name) + COLUMN_GAP + LegacyTextRenderer.width(value)

    fun renderInteractive(
        context: GuiGraphicsExtractor,
        left: Int,
        right: Int,
        y: Int,
        mouseX: Int?,
        mouseY: Int?,
    ): LocalSackHudControl? {
        val bounds = Rect(left, y, right - left, ITEM_ROW_HEIGHT)
        val hovered = mouseX != null && mouseY != null && bounds.contains(mouseX, mouseY)
        if (hovered) {
            context.fill(bounds.x, bounds.y, bounds.x + bounds.width, bounds.y + bounds.height, CONTROL_HOVER_COLOR)
        }
        if (reserveIcon) stack?.let { ItemIconRenderable(it, ICON_SCALE).renderAt(context, left, y) }
        LegacyTextRenderer.draw(context, name, left + contentOffset, y + ITEM_TEXT_Y_OFFSET)
        LegacyTextRenderer.draw(context, value, right - LegacyTextRenderer.width(value), y + ITEM_TEXT_Y_OFFSET)
        return LocalSackHudControl(SackHudControl.Item(item.itemId), bounds, emptyList()).takeIf { hovered }
    }
}

private fun SackHudItem.displayAmount(): String {
    val amountText = when {
        !known -> "§8?"
        highlighted -> "§a§l${amount.addSeparators()}"
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
private const val TITLE_HEIGHT = 13
private const val TEXT_ROW_HEIGHT = 11
private const val ITEM_ROW_HEIGHT = 14
private const val CONTROL_ROW_HEIGHT = 13
private const val CONTROL_TEXT_Y_OFFSET = 1
private const val ITEM_TEXT_Y_OFFSET = 2
private const val ICON_SCALE = 0.75
private const val ITEM_TEXT_OFFSET = 14
private const val COLUMN_GAP = 8
private const val CONTROL_HOVER_COLOR = 0x20FFFFFF
