package com.skysoft.features.inventory.itemlist

import com.skysoft.config.ItemListConfig
import com.skysoft.config.ItemListSettingsConfig
import com.skysoft.data.hypixel.HypixelLocationState
import com.skysoft.data.skyblock.ItemListEntryKey
import com.skysoft.data.skyblock.ItemListEntryKind
import com.skysoft.data.skyblock.SkyBlockDataRepository
import com.skysoft.data.skyblock.SkyBlockItemId.skyBlockId
import com.skysoft.features.inventory.StorageOverlayController
import com.skysoft.features.inventory.hoveredStorageItem
import com.skysoft.mixin.AbstractContainerScreenAccessor
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.item.ItemStack
import org.lwjgl.glfw.GLFW

internal var lastItemListShortcutOutcome = "none"

internal data class ItemListShortcutRequest(
    val mode: ItemListViewMode,
    val key: ItemListEntryKey,
)

internal fun itemListShortcutMode(key: Int, settings: ItemListSettingsConfig): ItemListViewMode? = when {
    key == GLFW.GLFW_KEY_UNKNOWN -> null
    key == settings.infoKey -> ItemListViewMode.INFO
    key == settings.recipesKey -> ItemListViewMode.RECIPES
    key == settings.usesKey -> ItemListViewMode.USAGES
    else -> null
}

internal fun resolveItemListShortcut(
    pressedKey: Int,
    config: ItemListConfig,
    screen: AbstractContainerScreen<*>,
    hoveredListKey: ItemListEntryKey?,
): ItemListShortcutRequest? {
    val mode = itemListShortcutMode(pressedKey, config.settings) ?: return null
    if (screen.focused is EditBox) {
        lastItemListShortcutOutcome = "$mode rejected:text-input-focused"
        return null
    }
    val source = when {
        hoveredListKey != null -> "item-list"
        StorageOverlayController.isActive(screen) -> "storage"
        else -> "container"
    }
    val target = if (config.enabled && HypixelLocationState.onHypixel) {
        hoveredListKey ?: hoveredItemListKey(screen)
    } else {
        null
    }
    lastItemListShortcutOutcome = when {
        !config.enabled -> "$mode rejected:item-list-disabled"
        !HypixelLocationState.onHypixel -> "$mode rejected:not-on-hypixel"
        target == null -> "$mode rejected:no-supported-item source:$source"
        else -> "$mode opened:${target.serialized()} source:$source"
    }
    return target?.let { ItemListShortcutRequest(mode, it) }
}

internal fun hoveredItemListKey(screen: AbstractContainerScreen<*>): ItemListEntryKey? {
    val storageStack = hoveredStorageItem.takeIf { StorageOverlayController.isActive(screen) && !it.isEmpty }
    val stack = storageStack ?: (screen as AbstractContainerScreenAccessor).skysoftGetHoveredSlot()?.item
    return stack?.itemListKey()
}

private fun ItemStack.itemListKey(): ItemListEntryKey? {
    if (isEmpty) return null
    val skyBlockId = skyBlockId()
    val key = if (skyBlockId != null) {
        SkyBlockDataRepository.itemKey(skyBlockId)
    } else {
        ItemListEntryKey(ItemListEntryKind.REGISTRY, BuiltInRegistries.ITEM.getKey(item).toString())
    }
    return key.takeIf { SkyBlockDataRepository.entry(it) != null }
}
