package com.skysoft.features.inventory

import com.skysoft.data.ProfileStorageApi
import com.skysoft.data.ProfileStorage
import com.skysoft.data.hypixel.HypixelLocationState
import com.skysoft.data.skyblock.SkyBlockItemUtilities.formattedHoverName
import com.skysoft.data.skyblock.SkyBlockOpenInventoryCell
import com.skysoft.data.skyblock.SkyBlockOpenInventorySnapshot
import com.skysoft.utils.MinecraftClient
import com.skysoft.utils.TextUtilities.cleanSkyBlockText
import com.skysoft.utils.input.InputHandlingResult
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.world.item.ItemStack

internal fun onClientTick() {
    val screen = MinecraftClient.screen() as? AbstractContainerScreen<*> ?: run {
        resetScreenState()
        return
    }
    if (!HypixelLocationState.inSkyBlock || !isStorageOverlayEnabled) {
        resetScreenState()
        return
    }
    val handle = handleFor(screen) ?: run {
        resetScreenState()
        return
    }
    if (routePendingOverviewShortcutClick(screen, handle) == InputHandlingResult.CONSUMED) {
        storageOverlayLayoutScreen(screen)
    }
}

private fun resetScreenState() {
    restoreStorageOverlaySlots()
    freezeStorageScroll()
    scrollbarDragOffset = null
    StorageCache.invalidateSnapshot()
    redirectedOverviewScreenId = null
    focusedPageKey = null
    StorageOverlayItemRenderer.reset()
    resetModernScreenState()
    resetStorageSettingsPanel()
}

internal fun resetTransientState() {
    resetScreenState()
    clearPageFocusRequest()
    preservedScrollPageIndex = null
    rememberedPageIndex = null
    storageSearchField.focused = false
    storageSearchField.text = ""
    resetTitleEdit()
    pendingOverviewShortcutClick = null
    resetModernTransientState()
    resetStorageScroll()
    StorageItemStacks.clear()
    StorageSearchIndex.clear()
}

internal fun readSnapshot(inventory: SkyBlockOpenInventorySnapshot, handle: StorageHandle) {
    if (isStorageOverlayEnabled) StorageSearchIndex.invalidatePages()
    ProfileStorageApi.updateProfile { profile ->
        with(profile) {
            when (handle) {
                StorageHandle.Overview -> readOverview(inventory.cells)
                is StorageHandle.Page -> readStoragePage(
                    inventory.cells,
                    handle.pageIndex,
                    handle.pageIndex,
                    handle.rows,
                    StoragePages.COLUMNS,
                    skyBlockStoragePages,
                )
                is StorageHandle.Rift -> {
                    repeat(ProfileStorage.SKYBLOCK_RIFT_STORAGE_PAGE_COUNT) { pageNumber ->
                        skyBlockRiftStoragePages.getOrPut(pageNumber) {
                            ProfileStorage.SkyBlockStoragePageData(defaultPageTitle(riftStoragePageIndex(pageNumber)), 0)
                        }
                    }
                    readStoragePage(
                        inventory.cells,
                        handle.pageIndex,
                        riftStoragePageNumber(handle.pageIndex),
                        handle.rows,
                        RiftStorage.SLOT_OFFSET,
                        skyBlockRiftStoragePages,
                    )
                }
                is StorageHandle.Toolkit -> readToolkit(inventory.cells, handle)
            }
        }
    }
}

private fun ProfileStorage.ProfileSpecific.readOverview(cells: List<SkyBlockOpenInventoryCell>) {
    for (cell in cells) {
        readOverviewCell(cell)
    }
}

private fun ProfileStorage.ProfileSpecific.readOverviewCell(cell: SkyBlockOpenInventoryCell) {
    val pageIndex = StorageOverviewSlots.pageIndexForSlot(cell.index)
        ?: run {
            if (isStorageOverlayEnabled) readToolkitOverviewCell(cell)
            return
        }
    val stack = cell.item
    if (stack.isEmpty) {
        if (isStorageOverlayEnabled) StorageItemStacks.removeOverviewPlaceholder(pageIndex)
        return
    }
    when (storageOverviewSlotState(stack)) {
        StorageOverviewSlotState.LOCKED -> readUnavailableOverviewSlot(pageIndex, stack)
        StorageOverviewSlotState.PLACEHOLDER -> readEmptyOverviewSlot(pageIndex, stack)
        StorageOverviewSlotState.PAGE -> readStorageOverviewSlot(pageIndex, stack)
    }
}

private fun ProfileStorage.ProfileSpecific.readToolkitOverviewCell(cell: SkyBlockOpenInventoryCell) {
    val stack = cell.item
    if (stack.isEmpty || stack.formattedHoverName().cleanSkyBlockText() != "Toolkits") return
    val overviewIcon = encodeItem(stack).encodedStack
    if (skyBlockToolkitIcon != overviewIcon) {
        skyBlockToolkitIcon = overviewIcon
    }
    ToolkitType.entries.forEach { type ->
        skyBlockToolkits.getOrPut(type.storageKey) {
            ProfileStorage.SkyBlockStoragePageData(type.title, 0)
        }
    }
}

private fun ProfileStorage.ProfileSpecific.readEmptyOverviewSlot(pageIndex: Int, stack: ItemStack) {
    if (isEnderChestPage(pageIndex)) {
        if (isStorageOverlayEnabled) StorageItemStacks.rememberOverviewPlaceholder(pageIndex, stack.copy())
        ensureUnloadedPage(pageIndex)
    } else {
        readUnavailableOverviewSlot(pageIndex, stack)
    }
}

private fun ProfileStorage.ProfileSpecific.readUnavailableOverviewSlot(pageIndex: Int, stack: ItemStack) {
    if (isStorageOverlayEnabled) StorageItemStacks.rememberOverviewPlaceholder(pageIndex, stack.copy())
    skyBlockStoragePages.remove(pageIndex)
}

private fun ProfileStorage.ProfileSpecific.readStorageOverviewSlot(pageIndex: Int, stack: ItemStack) {
    if (isStorageOverlayEnabled) StorageItemStacks.removeOverviewPlaceholder(pageIndex)
    val page = skyBlockStoragePages.getOrPut(pageIndex) {
        ProfileStorage.SkyBlockStoragePageData(defaultPageTitle(pageIndex), 0)
    }
    ensurePageTitle(page, pageIndex)
    val overviewIcon = encodeItem(stack).encodedStack
    if (page.overviewIcon != overviewIcon) {
        page.overviewIcon = overviewIcon
    }
}

private fun readStoragePage(
    cells: List<SkyBlockOpenInventoryCell>,
    pageIndex: Int,
    storedPageIndex: Int,
    menuRows: Int,
    slotOffset: Int,
    pages: MutableMap<Int, ProfileStorage.SkyBlockStoragePageData>,
) {
    val rows = menuRows.coerceIn(1, ProfileStorage.SKYBLOCK_STORAGE_PAGE_MAX_ROWS)
    val page = pages.getOrPut(storedPageIndex) {
        ProfileStorage.SkyBlockStoragePageData(defaultPageTitle(pageIndex), rows)
    }
    ensurePageTitle(page, pageIndex)
    page.readItems(cells, rows, slotOffset)
}

private fun ProfileStorage.ProfileSpecific.readToolkit(
    cells: List<SkyBlockOpenInventoryCell>,
    handle: StorageHandle.Toolkit,
) {
    val rows = handle.rows.coerceIn(1, ProfileStorage.SKYBLOCK_CONTAINER_MAX_ROWS)
    val page = skyBlockToolkits.getOrPut(handle.type.storageKey) {
        ProfileStorage.SkyBlockStoragePageData(handle.type.title, rows)
    }
    ensurePageTitle(page, handle.type.pageIndex)
    page.readItems(cells, rows, slotOffset = 0)
}

private fun ProfileStorage.SkyBlockStoragePageData.readItems(
    cells: List<SkyBlockOpenInventoryCell>,
    rows: Int,
    slotOffset: Int,
) {
    if (this.rows != rows) {
        this.rows = rows
    }
    repairLoadedValues()
    for (cell in cells) {
        val pageSlot = cell.index - slotOffset
        if (pageSlot !in 0 until rows * StoragePages.COLUMNS) continue
        val itemData = encodeItem(cell.item)
        if (items[pageSlot].encodedStack != itemData.encodedStack) {
            items[pageSlot] = itemData
        }
    }
}
