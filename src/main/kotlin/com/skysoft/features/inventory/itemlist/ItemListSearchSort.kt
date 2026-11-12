package com.skysoft.features.inventory.itemlist

import com.skysoft.config.SkysoftConfigGui
import com.skysoft.data.hypixel.HypixelLocationState
import com.skysoft.data.skyblock.ItemListEntry
import com.skysoft.data.skyblock.ItemListEntryKind
import com.skysoft.data.skyblock.SkyBlockDataRepository
import com.skysoft.data.skyblock.SkyBlockRarity
import com.skysoft.data.skyblock.price.SkyBlockPriceData
import com.skysoft.features.inventory.storage.StorageOverlayController
import com.skysoft.utils.MinecraftClient
import java.util.Locale
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen

internal enum class ItemListTypeFilter {
    ALL,
    MOBS,
    PETS,
    TOOLS,
    ARMOR,
    ACCESSORIES,
    ;

    fun matches(entry: ItemListEntry): Boolean = when (this) {
        ALL -> true
        MOBS -> entry.key.kind == ItemListEntryKind.ENTITY
        PETS -> itemCategory(entry) == "PET"
        TOOLS -> itemCategory(entry) in TOOL_CATEGORIES
        ARMOR -> itemCategory(entry) in ARMOR_CATEGORIES
        ACCESSORIES -> itemCategory(entry) in ACCESSORY_CATEGORIES
    }

    companion object {
        fun fromStoredName(name: String?): ItemListTypeFilter =
            entries.firstOrNull { it.name == name } ?: ALL
    }
}

internal enum class ItemListOrder {
    RELEVANCE,
    NAME,
    RARITY,
    PRICE,
    ;

    companion object {
        fun fromStoredName(name: String?): ItemListOrder =
            entries.firstOrNull { it.name == name } ?: RELEVANCE
    }
}

internal fun itemListTypeFilter(): ItemListTypeFilter {
    val itemList = SkysoftConfigGui.config().items.itemList
    if (!itemList.sortBar.enabled) return ItemListTypeFilter.ALL
    val filter = ItemListTypeFilter.fromStoredName(itemList.settings.listFilter)
    return if (itemList.sortBar.shows(filter)) filter else ItemListTypeFilter.ALL
}

internal fun itemListOrder(): ItemListOrder {
    val itemList = SkysoftConfigGui.config().items.itemList
    if (!itemList.sortBar.enabled) return ItemListOrder.RELEVANCE
    val order = ItemListOrder.fromStoredName(itemList.settings.listOrder)
    return if (itemList.sortBar.shows(order)) order else ItemListOrder.RELEVANCE
}

internal fun itemListOrderIsDescending(order: ItemListOrder): Boolean {
    val settings = SkysoftConfigGui.config().items.itemList.settings
    return when (order) {
        ItemListOrder.NAME -> settings.sortNameDescending
        ItemListOrder.RARITY -> settings.sortRarityDescending
        ItemListOrder.PRICE -> settings.sortPriceDescending
        ItemListOrder.RELEVANCE -> false
    }
}

internal fun compareItemListEntries(
    left: ItemListEntry,
    right: ItemListEntry,
    order: ItemListOrder,
    descending: Boolean,
): Int {
    val ranked = when (order) {
        ItemListOrder.RELEVANCE -> 0
        ItemListOrder.NAME -> {
            val name = nameOrder(left, right)
            if (descending) -name else name
        }
        ItemListOrder.RARITY -> compareOptional(rarityRank(left), rarityRank(right), descending)
        ItemListOrder.PRICE -> compareOptional(itemListWorth(left), itemListWorth(right), descending)
    }
    if (ranked != 0) return ranked
    if (order != ItemListOrder.NAME) {
        val name = nameOrder(left, right)
        if (name != 0) return name
    }
    return left.key.serialized().compareTo(right.key.serialized())
}

internal fun itemListPriceVersion(): Long {
    val lowestBinsUpdatedAt = SkyBlockPriceData.lowestBinsStatus.updatedAtMillis
    return SkyBlockPriceData.getBazaarUpdatedAtMillis() xor lowestBinsUpdatedAt.shl(1)
}

internal fun itemListSortNeedsMarketPrices(): Boolean {
    if (itemListOrder() != ItemListOrder.PRICE) return false
    if (!SkysoftConfigGui.config().items.itemList.enabled || ItemListState.isTemporarilyHidden) return false
    if (!HypixelLocationState.onHypixel) return false
    val screen = MinecraftClient.screen() as? AbstractContainerScreen<*> ?: return false
    return !StorageOverlayController.isActive(screen)
}

private fun itemCategory(entry: ItemListEntry): String? =
    SkyBlockDataRepository.info(entry.key)?.category?.trim()?.uppercase(Locale.ROOT)

private fun nameOrder(left: ItemListEntry, right: ItemListEntry): Int =
    left.displayName.lowercase(Locale.ROOT).compareTo(right.displayName.lowercase(Locale.ROOT))

private fun <T : Comparable<T>> compareOptional(left: T?, right: T?, descending: Boolean): Int {
    if (left == null && right == null) return 0
    if (left == null) return 1
    if (right == null) return -1
    val comparison = left.compareTo(right)
    return if (descending) -comparison else comparison
}

private fun rarityRank(entry: ItemListEntry): Int? {
    val rarity = SkyBlockDataRepository.info(entry.key)?.rarity ?: return null
    return SkyBlockRarity.getByName(rarity.replace(' ', '_'))?.id
}

private fun itemListWorth(entry: ItemListEntry): Double? {
    if (entry.key.kind != ItemListEntryKind.SKYBLOCK) return null
    val instantBuy = SkyBlockPriceData.getBazaarProduct(entry.key.id)?.instantBuyPrice ?: 0.0
    if (instantBuy > 0.0) return instantBuy
    val lowestBin = SkyBlockPriceData.getLowestBin(entry.key.id) ?: 0L
    return lowestBin.takeIf { it > 0L }?.toDouble()
}

private val TOOL_CATEGORIES = setOf(
    "SWORD",
    "BOW",
    "AXE",
    "PICKAXE",
    "FISHING ROD",
    "WAND",
    "SHOVEL",
    "HOE",
    "DUNGEON SWORD",
    "DUNGEON BOW",
    "DRILL",
    "GAUNTLET",
    "LONGSWORD",
    "DEPLOYABLE",
)

private val ARMOR_CATEGORIES = setOf(
    "HELMET",
    "CHESTPLATE",
    "LEGGINGS",
    "BOOTS",
    "DUNGEON HELMET",
    "DUNGEON CHESTPLATE",
    "DUNGEON LEGGINGS",
    "DUNGEON BOOTS",
    "BELT",
    "GLOVES",
    "CLOAK",
    "NECKLACE",
    "BRACELET",
)

private val ACCESSORY_CATEGORIES = setOf(
    "ACCESSORY",
    "HATCESSORY",
    "DUNGEON ACCESSORY",
)
