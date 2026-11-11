package com.skysoft.features.inventory.itemlist

import com.mojang.blaze3d.platform.InputConstants
import com.skysoft.config.ItemListSettingsConfig
import com.skysoft.config.SkysoftConfigGui
import com.skysoft.gui.tooltip.SkysoftNativeTooltip
import com.skysoft.utils.SoundUtilities
import com.skysoft.utils.gui.PixelButtonRenderer
import com.skysoft.utils.gui.Rect
import com.skysoft.utils.renderables.withIsolatedPose
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

internal const val ITEM_LIST_SORT_BAR_HEIGHT = 16

internal data class ItemListSortBar(
    val bounds: Rect,
    val buttons: List<ItemListSortButton>,
)

internal data class ItemListSortButton(
    val bounds: Rect,
    val order: ItemListOrder?,
    val filter: ItemListTypeFilter?,
)

internal fun itemListSortBar(x: Int, width: Int, y: Int): ItemListSortBar {
    val orders = listOf(ItemListOrder.NAME, ItemListOrder.RARITY, ItemListOrder.PRICE)
    val filters = ItemListTypeFilter.entries
    val count = orders.size + filters.size
    val size = ((width - SORT_BUTTON_GAP * (count - 1)) / count).coerceIn(1, SORT_BUTTON_SIZE)
    val iconY = y + (ITEM_LIST_SORT_BAR_HEIGHT - size) / 2
    val buttons = buildList {
        orders.forEachIndexed { index, order ->
            add(
                ItemListSortButton(
                    Rect(x + index * (size + SORT_BUTTON_GAP), iconY, size, size),
                    order,
                    null,
                ),
            )
        }
        filters.forEachIndexed { index, filter ->
            val filterX = x + width - size - index * (size + SORT_BUTTON_GAP)
            add(ItemListSortButton(Rect(filterX, iconY, size, size), null, filter))
        }
    }
    return ItemListSortBar(Rect(x, y, width, ITEM_LIST_SORT_BAR_HEIGHT), buttons)
}

internal fun drawItemListSortBar(
    context: GuiGraphicsExtractor,
    bar: ItemListSortBar,
    mouseX: Int,
    mouseY: Int,
) {
    val activeFilter = itemListTypeFilter()
    val activeOrder = itemListOrder()
    val font = Minecraft.getInstance().font
    bar.buttons.forEach { button ->
        val selected = button.filter == activeFilter || button.order == activeOrder
        val hovered = button.bounds.contains(mouseX, mouseY)
        PixelButtonRenderer.draw(context, font, button.bounds, "", selected, hovered, true)
        drawSortIcon(context, button)
        if (selected && button.order != null) {
            drawDirectionArrow(context, font, button.bounds, itemListOrderIsDescending(button.order))
        }
        if (hovered) SkysoftNativeTooltip.setForNextFrame(context, sortButtonTooltip(button), mouseX, mouseY)
    }
}

internal fun handleItemListSortClick(bar: ItemListSortBar, button: Int, mouseX: Int, mouseY: Int): Boolean {
    if (!bar.bounds.contains(mouseX, mouseY)) return false
    val hit = bar.buttons.firstOrNull { it.bounds.contains(mouseX, mouseY) } ?: return true
    val settings = SkysoftConfigGui.config().items.itemList.settings
    val reorder = when (button) {
        InputConstants.MOUSE_BUTTON_LEFT -> selectSortButton(settings, hit)
        InputConstants.MOUSE_BUTTON_RIGHT -> flipSortDirection(settings, hit)
        else -> null
    }
    if (reorder == null) return true
    SkysoftConfigGui.config().saveNow()
    if (reorder) ItemListState.page = 0
    SoundUtilities.playClickSound()
    return true
}

private fun selectSortButton(settings: ItemListSettingsConfig, button: ItemListSortButton): Boolean? {
    button.filter?.let { filter ->
        if (settings.listFilter == filter.name) return null
        settings.listFilter = filter.name
        return true
    }
    val order = button.order ?: return null
    val next = if (settings.listOrder == order.name) ItemListOrder.RELEVANCE.name else order.name
    if (settings.listOrder == next) return null
    settings.listOrder = next
    return true
}

private fun flipSortDirection(settings: ItemListSettingsConfig, button: ItemListSortButton): Boolean? {
    val order = button.order ?: return null
    when (order) {
        ItemListOrder.NAME -> settings.sortNameDescending = !settings.sortNameDescending
        ItemListOrder.RARITY -> settings.sortRarityDescending = !settings.sortRarityDescending
        ItemListOrder.PRICE -> settings.sortPriceDescending = !settings.sortPriceDescending
        ItemListOrder.RELEVANCE -> return null
    }
    return settings.listOrder == order.name
}

private fun drawSortIcon(context: GuiGraphicsExtractor, button: ItemListSortButton) {
    val bounds = if (button.bounds.width > SORT_ICON_INSET * 2) button.bounds.inset(SORT_ICON_INSET) else button.bounds
    if (bounds.width <= 0 || bounds.height <= 0) return
    context.withIsolatedPose {
        val scale = bounds.width / SORT_ICON_PIXELS
        pose().translate(bounds.x.toFloat(), bounds.y.toFloat())
        pose().scale(scale, scale)
        item(ItemStack(sortButtonItem(button)), 0, 0)
    }
}

private fun drawDirectionArrow(
    context: GuiGraphicsExtractor,
    font: Font,
    bounds: Rect,
    descending: Boolean,
) {
    if (bounds.width < MIN_DIRECTION_ARROW_SIZE) return
    val label = if (descending) "v" else "^"
    context.text(
        font,
        label,
        bounds.x + bounds.width - font.width(label) - 1,
        bounds.y + 1,
        DIRECTION_ARROW_COLOR,
        true,
    )
}

private fun sortButtonTooltip(button: ItemListSortButton): List<String> {
    button.filter?.let { return listOf(filterLabel(it)) }
    val order = button.order ?: return emptyList()
    return listOf(orderLabel(order), directionLabel(order))
}

private fun filterLabel(filter: ItemListTypeFilter): String = when (filter) {
    ItemListTypeFilter.ALL -> "No Filter"
    ItemListTypeFilter.MOBS -> "Filter Mobs"
    ItemListTypeFilter.PETS -> "Filter Pets"
    ItemListTypeFilter.TOOLS -> "Filter Tools"
    ItemListTypeFilter.ARMOR -> "Filter Armor"
    ItemListTypeFilter.ACCESSORIES -> "Filter Accessories"
}

private fun orderLabel(order: ItemListOrder): String = when (order) {
    ItemListOrder.NAME -> "Order Alphabetically"
    ItemListOrder.RARITY -> "Order by Rarity"
    ItemListOrder.PRICE -> "Order by Item Worth"
    ItemListOrder.RELEVANCE -> "Order by Search"
}

private fun directionLabel(order: ItemListOrder): String {
    val descending = itemListOrderIsDescending(order)
    return when (order) {
        ItemListOrder.NAME -> if (descending) "Z to A" else "A to Z"
        ItemListOrder.RARITY -> if (descending) "Highest first" else "Lowest first"
        ItemListOrder.PRICE -> if (descending) "Most expensive first" else "Least expensive first"
        ItemListOrder.RELEVANCE -> "Best match first"
    }
}

private fun sortButtonItem(button: ItemListSortButton): Item = when (button.order) {
    ItemListOrder.NAME -> Items.PAPER
    ItemListOrder.RARITY -> Items.NETHER_STAR
    ItemListOrder.PRICE -> Items.GOLD_INGOT
    ItemListOrder.RELEVANCE, null -> when (button.filter) {
        ItemListTypeFilter.MOBS -> Items.ZOMBIE_HEAD
        ItemListTypeFilter.PETS -> Items.BONE
        ItemListTypeFilter.TOOLS -> Items.DIAMOND_SWORD
        ItemListTypeFilter.ARMOR -> Items.DIAMOND_CHESTPLATE
        ItemListTypeFilter.ACCESSORIES -> Items.EMERALD
        ItemListTypeFilter.ALL, null -> Items.CHEST
    }
}

private const val SORT_BUTTON_GAP = 1
private const val SORT_BUTTON_SIZE = 16
private const val SORT_ICON_INSET = 1
private const val SORT_ICON_PIXELS = 16f
private const val MIN_DIRECTION_ARROW_SIZE = 10
private val DIRECTION_ARROW_COLOR = 0xFFFFFFFF.toInt()
