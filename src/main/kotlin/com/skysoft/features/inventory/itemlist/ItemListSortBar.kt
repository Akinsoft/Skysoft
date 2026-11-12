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
    val config = SkysoftConfigGui.config().items.itemList.sortBar
    val orders = SORT_ORDERS.filter(config::shows)
    val filters = SORT_FILTERS.filter(config::shows)
    if (!config.enabled || (orders.isEmpty() && filters.isEmpty())) {
        return ItemListSortBar(Rect(x, y, width, 0), emptyList())
    }
    return ItemListSortBar(Rect(x, y, width, ITEM_LIST_SORT_BAR_HEIGHT), sortButtons(x, width, y, orders, filters))
}

internal fun drawItemListSortBar(
    context: GuiGraphicsExtractor,
    bar: ItemListSortBar,
    mouseX: Int,
    mouseY: Int,
) {
    if (bar.buttons.isEmpty()) return
    val activeFilter = itemListTypeFilter()
    val activeOrder = itemListOrder()
    val font = Minecraft.getInstance().font
    bar.buttons.forEach { button ->
        val selected = button.filter == activeFilter || button.order == activeOrder
        val hovered = button.bounds.contains(mouseX, mouseY)
        PixelButtonRenderer.draw(context, font, button.bounds, "", selected, hovered, true)
        drawSortIcon(context, button)
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

private fun sortButtons(
    x: Int,
    width: Int,
    y: Int,
    orders: List<ItemListOrder>,
    filters: List<ItemListTypeFilter>,
): List<ItemListSortButton> {
    val orderGaps = SORT_BUTTON_GAP * (orders.size - 1).coerceAtLeast(0)
    val filterGaps = SORT_BUTTON_GAP * (filters.size - 1).coerceAtLeast(0)
    val split = if (orders.isNotEmpty() && filters.isNotEmpty()) SORT_SPLIT_GAP else 0
    val count = orders.size + filters.size
    val size = ((width - orderGaps - filterGaps - split) / count).coerceIn(1, SORT_BUTTON_SIZE)
    val iconY = y + (ITEM_LIST_SORT_BAR_HEIGHT - size) / 2
    return buildList {
        var cursor = x
        orders.forEachIndexed { index, order ->
            add(ItemListSortButton(Rect(cursor, iconY, size, size), order, null))
            cursor += size
            if (index < orders.lastIndex) cursor += SORT_BUTTON_GAP
        }
        var filterCursor = x + width - filters.size * size - filterGaps
        filters.forEachIndexed { index, filter ->
            add(ItemListSortButton(Rect(filterCursor, iconY, size, size), null, filter))
            filterCursor += size
            if (index < filters.lastIndex) filterCursor += SORT_BUTTON_GAP
        }
    }
}

private fun selectSortButton(settings: ItemListSettingsConfig, button: ItemListSortButton): Boolean? {
    button.filter?.let { filter ->
        settings.listFilter = if (settings.listFilter == filter.name) {
            ItemListTypeFilter.ALL.name
        } else {
            filter.name
        }
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
    val bounds = if (button.bounds.width > SORT_ICON_INSET * 2) {
        button.bounds.inset(SORT_ICON_INSET)
    } else {
        button.bounds
    }
    if (bounds.width <= 0 || bounds.height <= 0) return
    context.withIsolatedPose {
        val scale = bounds.width / SORT_ICON_PIXELS
        pose().translate(bounds.x.toFloat(), bounds.y.toFloat())
        pose().scale(scale, scale)
        item(ItemStack(sortButtonItem(button)), 0, 0)
    }
}

private fun sortButtonTooltip(button: ItemListSortButton): List<String> {
    button.filter?.let { filter ->
        val label = filterLabel(filter)
        return if (itemListTypeFilter() == filter) listOf(label, "Click again to clear") else listOf(label)
    }
    val order = button.order ?: return emptyList()
    val lines = listOf(orderLabel(order), directionLabel(order))
    return if (itemListOrder() == order) lines + "Click again to clear" else lines
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

private val SORT_ORDERS = listOf(ItemListOrder.NAME, ItemListOrder.RARITY, ItemListOrder.PRICE)
private val SORT_FILTERS = listOf(
    ItemListTypeFilter.MOBS,
    ItemListTypeFilter.PETS,
    ItemListTypeFilter.TOOLS,
    ItemListTypeFilter.ARMOR,
    ItemListTypeFilter.ACCESSORIES,
)

private const val SORT_BUTTON_GAP = 1
private const val SORT_SPLIT_GAP = 4
private const val SORT_BUTTON_SIZE = 16
private const val SORT_ICON_INSET = 2
private const val SORT_ICON_PIXELS = 16f
