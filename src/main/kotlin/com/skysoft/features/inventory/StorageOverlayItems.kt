package com.skysoft.features.inventory

import com.skysoft.utils.TextUtilities.nonItalicText
import com.skysoft.data.ProfileStorage
import com.skysoft.data.skyblock.SkyBlockItemStackCodec
import com.skysoft.data.skyblock.SkyBlockItemUtilities.formattedHoverName
import com.skysoft.utils.MinecraftItems
import com.skysoft.utils.TextUtilities.cleanSkyBlockText
import net.minecraft.core.component.DataComponents
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Item

internal fun storageOverviewSlotState(stack: ItemStack): StorageOverviewSlotState {
    val name = stack.formattedHoverName().cleanSkyBlockText()
    return when {
        name == "Locked Page" || name.startsWith("Locked Backpack Slot") -> StorageOverviewSlotState.LOCKED
        stack.item in emptyOverviewItems -> StorageOverviewSlotState.PLACEHOLDER
        else -> StorageOverviewSlotState.PAGE
    }
}

internal fun emptyBackpackShortcutStack(backpackSlot: Int): ItemStack =
    namedShortcutStack(MinecraftItems.grayDye(), "Empty Backpack Slot $backpackSlot")

internal fun namedShortcutStack(item: Item, name: String): ItemStack = ItemStack(item).apply {
    set(DataComponents.CUSTOM_NAME, nonItalicText(name))
}

internal fun ProfileStorage.ProfileSpecific.ensureUnloadedPage(pageIndex: Int) {
    val page = skyBlockStoragePages[pageIndex] ?: run {
        skyBlockStoragePages[pageIndex] = unloadedStoragePage(pageIndex)
        return
    }
    ensurePageTitle(page, pageIndex)
    page.overviewIcon = ""
}

internal fun unloadedStoragePage(pageIndex: Int): ProfileStorage.SkyBlockStoragePageData =
    ProfileStorage.SkyBlockStoragePageData(defaultPageTitle(pageIndex), 0)

internal fun isEnderChestPage(pageIndex: Int): Boolean =
    pageIndex in 0 until ProfileStorage.SKYBLOCK_STORAGE_ENDER_CHEST_PAGES

internal fun fixedPageTitle(pageIndex: Int): String? = ToolkitType.fromPageIndex(pageIndex)?.title

internal fun defaultPageTitle(pageIndex: Int): String = fixedPageTitle(pageIndex) ?: when {
    isRiftStoragePage(pageIndex) -> "Rift Storage #${riftStoragePageNumber(pageIndex) + 1}"
    pageIndex < ProfileStorage.SKYBLOCK_STORAGE_ENDER_CHEST_PAGES -> "Ender Chest #${pageIndex + 1}"
    else -> "Backpack #${pageIndex - ProfileStorage.SKYBLOCK_STORAGE_ENDER_CHEST_PAGES + 1}"
}

internal fun ensurePageTitle(page: ProfileStorage.SkyBlockStoragePageData, pageIndex: Int) {
    page.title = fixedPageTitle(pageIndex)
        ?: page.title.takeIf { it.isNotBlank() }
        ?: defaultPageTitle(pageIndex)
}

internal fun encodeItem(stack: ItemStack): ProfileStorage.SkyBlockStorageItemData =
    if (stack.isEmpty) {
        ProfileStorage.SkyBlockStorageItemData()
    } else {
        ProfileStorage.SkyBlockStorageItemData(SkyBlockItemStackCodec.encode(stack.copy()))
    }

internal enum class StorageOverviewSlotState {
    LOCKED,
    PLACEHOLDER,
    PAGE,
}

private val emptyOverviewItems: Set<Item> = buildSet {
    addAll(MinecraftItems.stainedGlassPanes())
    add(MinecraftItems.grayDye())
}
