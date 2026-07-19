package com.skysoft.features.inventory

import com.skysoft.data.ProfileStorageApi
import com.skysoft.data.ProfileStorage
import com.skysoft.data.hypixel.HypixelLocationState
import com.skysoft.data.skyblock.SkyBlockItemUtilities.formattedHoverName
import com.skysoft.utils.ChangeResult
import com.skysoft.utils.TextUtilities.cleanSkyBlockText
import com.skysoft.utils.MinecraftClient
import com.skysoft.utils.gui.nonPlayerInventoryKey
import com.skysoft.utils.gui.nonPlayerSlots
import com.skysoft.utils.input.InputHandlingResult
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.world.inventory.Slot
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

internal fun resetScreenState() {
    restoreStorageOverlaySlots()
    freezeStorageScroll()
    scrollbarDragOffset = null
    lastInventoryKey = null
    redirectedOverviewScreenId = null
    focusedPageKey = null
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
    resetStorageScroll()
    decodedStacks.clear()
    emptyOverviewStacks.clear()
    StorageSearchIndex.clear()
}

internal fun handleFor(screen: AbstractContainerScreen<*>?): StorageHandle? {
    if (screen == null || !HypixelLocationState.inSkyBlock || !isStorageOverlayEnabled) return null
    return storageHandleFor(screen)
}

internal fun storagePageHandle(title: String, rows: Int): StorageHandle.Page? {
    val enderChestPage = enderChestTitlePattern.matchEntire(title)?.groupValues?.get(1)?.toIntOrNull()
    if (enderChestPage != null && enderChestPage in 1..ProfileStorage.SKYBLOCK_STORAGE_ENDER_CHEST_PAGES) {
        return StorageHandle.Page(enderChestPage - 1, rows - 1)
    }
    val backpackPage = backpackTitlePattern.matchEntire(title)?.groupValues?.get(1)?.toIntOrNull()
    return if (backpackPage != null && backpackPage in 1..ProfileStorage.SKYBLOCK_STORAGE_BACKPACK_PAGES) {
        StorageHandle.Page(ProfileStorage.SKYBLOCK_STORAGE_ENDER_CHEST_PAGES + backpackPage - 1, rows - 1)
    } else {
        null
    }
}

internal fun readScreen(screen: AbstractContainerScreen<*>, handle: StorageHandle) {
    val key = buildInventoryKey(screen)
    if (key == lastInventoryKey) return
    lastInventoryKey = key
    if (isStorageOverlayEnabled) StorageSearchIndex.invalidatePages()
    when (handle) {
        StorageHandle.Overview -> readOverview(screen)
        is StorageHandle.Page -> readStoragePage(
            screen,
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
                screen,
                handle.pageIndex,
                riftStoragePageNumber(handle.pageIndex),
                handle.rows,
                RiftStorage.SLOT_OFFSET,
                storage.skyBlockRiftStoragePages,
                changed,
            )
        }
        is StorageHandle.Toolkit -> readToolkit(screen, handle)
    }
}

internal fun buildInventoryKey(screen: AbstractContainerScreen<*>): String = screen.nonPlayerInventoryKey()

internal fun readOverview(screen: AbstractContainerScreen<*>) {
    var changed = false
    for (slot in screen.nonPlayerSlots()) {
        changed = readOverviewSlot(slot) == ChangeResult.CHANGED || changed
    }
    if (changed) ProfileStorageApi.markDirty()
}

internal fun readOverviewSlot(slot: Slot): ChangeResult {
    val pageIndex = StorageOverviewSlots.pageIndexForSlot(slot.containerSlot)
        ?: return if (isStorageOverlayEnabled) readToolkitOverviewSlot(slot) else ChangeResult.UNCHANGED
    val stack = slot.item
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

internal fun readToolkitOverviewSlot(slot: Slot): ChangeResult {
    val stack = slot.item
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

internal fun readEmptyOverviewSlot(pageIndex: Int, stack: ItemStack): ChangeResult {
    return if (isEnderChestPage(pageIndex)) {
        if (isStorageOverlayEnabled) emptyOverviewStacks[pageIndex] = stack.copy()
        ensureUnloadedPage(pageIndex)
    } else {
        readUnavailableOverviewSlot(pageIndex, stack)
    }
}

internal fun readUnavailableOverviewSlot(pageIndex: Int, stack: ItemStack): ChangeResult {
    if (isStorageOverlayEnabled) emptyOverviewStacks[pageIndex] = stack.copy()
    return ChangeResult.from(storage.skyBlockStoragePages.remove(pageIndex) != null)
}

internal fun readStorageOverviewSlot(pageIndex: Int, stack: ItemStack): ChangeResult {
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

internal fun readStoragePage(
    screen: AbstractContainerScreen<*>,
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
    for (slot in screen.nonPlayerSlots()) {
        val pageSlot = slot.containerSlot - slotOffset
        if (pageSlot !in 0 until rows * StoragePages.COLUMNS) continue
        val itemData = encodeItem(slot.item)
        if (items[pageSlot].encodedStack != itemData.encodedStack) {
            items[pageSlot] = itemData
            changed = true
        }
    }
    if (changed) ProfileStorageApi.markDirty()
}

internal fun readToolkit(screen: AbstractContainerScreen<*>, handle: StorageHandle.Toolkit) {
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
    for (slot in screen.nonPlayerSlots()) {
        val pageSlot = slot.containerSlot
        if (pageSlot !in 0 until rows * StoragePages.COLUMNS) continue
        val itemData = encodeItem(slot.item)
        if (items[pageSlot].encodedStack != itemData.encodedStack) {
            items[pageSlot] = itemData
            changed = true
        }
    }
    if (changed) ProfileStorageApi.markDirty()
}
