package com.skysoft.features.inventory

import com.skysoft.data.ProfileStorageApi
import com.skysoft.data.ProfileStorage
import com.skysoft.data.hypixel.HypixelLocationState
import com.skysoft.data.skyblock.SkyBlockItemUtilities.formattedHoverName
import com.skysoft.data.skyblock.SkyBlockOpenInventorySnapshot
import com.skysoft.utils.ChangeResult
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
    lastInventoryKey = null
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
    editingTitlePage = null
    editingTitleText = ""
    editingTitleSelected = false
    pendingOverviewShortcutClick = null
    resetModernTransientState()
    resetStorageScroll()
    decodedStacks.clear()
    emptyOverviewStacks.clear()
    StorageSearchIndex.clear()
}

internal fun readSnapshot(snapshot: SkyBlockOpenInventorySnapshot, handle: StorageHandle) {
    readStorageInventory(
        StorageInventoryView(
            key = snapshot.key,
            cells = snapshot.cells.map { cell -> StorageInventoryCell(cell.index, cell.item) },
        ),
        handle,
    )
}

private fun readStorageInventory(inventory: StorageInventoryView, handle: StorageHandle) {
    if (inventory.key == lastInventoryKey) return
    lastInventoryKey = inventory.key
    if (isStorageOverlayEnabled) StorageSearchIndex.invalidatePages()
    when (handle) {
        StorageHandle.Overview -> readOverview(inventory.cells)
        is StorageHandle.Page -> readStoragePage(
            inventory.cells,
            handle.pageIndex,
            handle.pageIndex,
            handle.rows,
            StoragePages.COLUMNS,
            storage.skyBlockStoragePages,
        )
        is StorageHandle.Rift -> {
            var changed = false
            repeat(ProfileStorage.SKYBLOCK_RIFT_STORAGE_PAGE_COUNT) { pageNumber ->
                storage.skyBlockRiftStoragePages.getOrPut(pageNumber) {
                    changed = true
                    ProfileStorage.SkyBlockStoragePageData(defaultPageTitle(riftStoragePageIndex(pageNumber)), 0)
                }
            }
            readStoragePage(
                inventory.cells,
                handle.pageIndex,
                riftStoragePageNumber(handle.pageIndex),
                handle.rows,
                RiftStorage.SLOT_OFFSET,
                storage.skyBlockRiftStoragePages,
                changed,
            )
        }
        is StorageHandle.Toolkit -> readToolkit(inventory.cells, handle)
    }
}

private fun readOverview(cells: List<StorageInventoryCell>) {
    var changed = false
    for (cell in cells) {
        changed = readOverviewCell(cell) == ChangeResult.CHANGED || changed
    }
    if (changed) ProfileStorageApi.markDirty()
}

private fun readOverviewCell(cell: StorageInventoryCell): ChangeResult {
    val pageIndex = StorageOverviewSlots.pageIndexForSlot(cell.index)
        ?: return if (isStorageOverlayEnabled) readToolkitOverviewCell(cell) else ChangeResult.UNCHANGED
    val stack = cell.item
    if (stack.isEmpty) {
        if (isStorageOverlayEnabled) emptyOverviewStacks.remove(pageIndex)
        return ChangeResult.UNCHANGED
    }
    return when (storageOverviewSlotState(stack)) {
        StorageOverviewSlotState.LOCKED -> readUnavailableOverviewSlot(pageIndex, stack)
        StorageOverviewSlotState.PLACEHOLDER -> readEmptyOverviewSlot(pageIndex, stack)
        StorageOverviewSlotState.PAGE -> readStorageOverviewSlot(pageIndex, stack)
    }
}

private fun readToolkitOverviewCell(cell: StorageInventoryCell): ChangeResult {
    val stack = cell.item
    if (stack.isEmpty || stack.formattedHoverName().cleanSkyBlockText() != "Toolkits") return ChangeResult.UNCHANGED
    val overviewIcon = encodeItem(stack).encodedStack
    var changed = false
    if (storage.skyBlockToolkitIcon != overviewIcon) {
        storage.skyBlockToolkitIcon = overviewIcon
        changed = true
    }
    ToolkitType.entries.forEach { type ->
        storage.skyBlockToolkits.getOrPut(type.storageKey) {
            changed = true
            ProfileStorage.SkyBlockStoragePageData(type.title, 0)
        }
    }
    return ChangeResult.from(changed)
}

private fun readEmptyOverviewSlot(pageIndex: Int, stack: ItemStack): ChangeResult {
    return if (isEnderChestPage(pageIndex)) {
        if (isStorageOverlayEnabled) emptyOverviewStacks[pageIndex] = stack.copy()
        ensureUnloadedPage(pageIndex)
    } else {
        readUnavailableOverviewSlot(pageIndex, stack)
    }
}

private fun readUnavailableOverviewSlot(pageIndex: Int, stack: ItemStack): ChangeResult {
    if (isStorageOverlayEnabled) emptyOverviewStacks[pageIndex] = stack.copy()
    return ChangeResult.from(storage.skyBlockStoragePages.remove(pageIndex) != null)
}

private fun readStorageOverviewSlot(pageIndex: Int, stack: ItemStack): ChangeResult {
    var changed = false
    if (isStorageOverlayEnabled) emptyOverviewStacks.remove(pageIndex)
    val page = storage.skyBlockStoragePages.getOrPut(pageIndex) {
        changed = true
        ProfileStorage.SkyBlockStoragePageData(defaultPageTitle(pageIndex), 0)
    }
    changed = ensurePageTitle(page, pageIndex) == ChangeResult.CHANGED || changed
    val overviewIcon = encodeItem(stack).encodedStack
    if (page.overviewIcon != overviewIcon) {
        page.overviewIcon = overviewIcon
        changed = true
    }
    return ChangeResult.from(changed)
}

private fun readStoragePage(
    cells: List<StorageInventoryCell>,
    pageIndex: Int,
    storedPageIndex: Int,
    menuRows: Int,
    slotOffset: Int,
    pages: MutableMap<Int, ProfileStorage.SkyBlockStoragePageData>,
    wasChanged: Boolean = false,
) {
    val rows = menuRows.coerceIn(1, ProfileStorage.SKYBLOCK_STORAGE_PAGE_MAX_ROWS)
    var changed = wasChanged
    val page = pages.getOrPut(storedPageIndex) {
        changed = true
        ProfileStorage.SkyBlockStoragePageData(defaultPageTitle(pageIndex), rows)
    }
    changed = ensurePageTitle(page, pageIndex) == ChangeResult.CHANGED || changed
    if (page.rows != rows) {
        page.rows = rows
        changed = true
    }
    page.repairLoadedValues()
    val items = page.items
    for (cell in cells) {
        val pageSlot = cell.index - slotOffset
        if (pageSlot !in 0 until rows * StoragePages.COLUMNS) continue
        val itemData = encodeItem(cell.item)
        if (items[pageSlot].encodedStack != itemData.encodedStack) {
            items[pageSlot] = itemData
            changed = true
        }
    }
    if (changed) ProfileStorageApi.markDirty()
}

private fun readToolkit(cells: List<StorageInventoryCell>, handle: StorageHandle.Toolkit) {
    val rows = handle.rows.coerceIn(1, ProfileStorage.SKYBLOCK_CONTAINER_MAX_ROWS)
    var changed = false
    val page = storage.skyBlockToolkits.getOrPut(handle.type.storageKey) {
        changed = true
        ProfileStorage.SkyBlockStoragePageData(handle.type.title, rows)
    }
    changed = ensurePageTitle(page, handle.type.pageIndex) == ChangeResult.CHANGED || changed
    if (page.rows != rows) {
        page.rows = rows
        changed = true
    }
    page.repairLoadedValues()
    val items = page.items
    for (cell in cells) {
        val pageSlot = cell.index
        if (pageSlot !in 0 until rows * StoragePages.COLUMNS) continue
        val itemData = encodeItem(cell.item)
        if (items[pageSlot].encodedStack != itemData.encodedStack) {
            items[pageSlot] = itemData
            changed = true
        }
    }
    if (changed) ProfileStorageApi.markDirty()
}

private data class StorageInventoryView(
    val key: String,
    val cells: List<StorageInventoryCell>,
)

private data class StorageInventoryCell(
    val index: Int,
    val item: ItemStack,
)
