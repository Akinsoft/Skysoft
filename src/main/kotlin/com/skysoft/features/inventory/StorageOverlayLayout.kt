package com.skysoft.features.inventory

import com.skysoft.config.StorageOverlayConfigBounds
import com.skysoft.data.ProfileStorage
import com.skysoft.utils.gui.Point
import com.skysoft.utils.gui.Rect
import com.skysoft.utils.gui.nonPlayerSlots
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.inventory.ContainerScreen
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.Blocks

internal fun measurements(
    width: Int,
    height: Int,
    isSelectorVisible: Boolean,
): Measurements = if (isModernStorageOverlay) {
    modernMeasurements(width, height)
} else {
    classicMeasurements(width, height, isSelectorVisible)
}

private fun classicMeasurements(
    width: Int,
    height: Int,
    isSelectorVisible: Boolean,
): Measurements {
    val columns = config.details.columns.coerceIn(
        StorageOverlayConfigBounds.MIN_COLUMNS,
        maximumStorageColumns(width),
    )
    val scrollPanelWidth = columns * StoragePages.WIDTH + (columns - 1) * StoragePages.PADDING
    val storageWidth = scrollPanelWidth + StorageScrollbar.GAP + StorageScrollbar.WIDTH + StoragePanel.PADDING * 2
    val storageX = (width - storageWidth) / 2
    val playerX = width / 2 - StoragePlayerInventory.WIDTH / 2
    val sideSelectorX = StorageSelectorLayout.sideX(playerX)
    val stackedSelectorHeight = if (isSelectorVisible && sideSelectorX == null) {
        StorageSelector.HEIGHT + StorageSelector.STACKED_GAP
    } else {
        0
    }
    val storageHeight = config.details.height.coerceIn(
        StorageOverlayConfigBounds.MIN_HEIGHT,
        maximumStorageHeight(height, stackedSelectorHeight),
    )
    val availableHeight = height -
        storageHeight -
        StoragePlayerInventory.HEIGHT -
        StorageSearch.HEIGHT -
        StoragePanel.PADDING -
        stackedSelectorHeight
    val storageTop = (availableHeight / 2).coerceAtLeast(StoragePanel.TOP_MIN)
    val scrollPanel = Rect(
        storageX + StoragePanel.PADDING,
        storageTop + StoragePanel.PADDING,
        scrollPanelWidth,
        storageHeight - StoragePanel.PADDING * 2,
    )
    val playerY = storageTop + storageHeight + StorageSearch.HEIGHT + StorageSearch.GAP
    val search = Rect(
        playerX,
        storageTop + storageHeight + StorageSearch.GAP,
        StoragePlayerInventory.WIDTH,
        StorageSearch.HEIGHT,
    )
    val selectorBounds = StorageSelectorLayout.bounds(width, sideSelectorX, playerY)
    val totalBounds = Rect(
        storageX,
        storageTop,
        storageWidth,
        storageHeight +
            StorageSearch.HEIGHT +
            StoragePlayerInventory.HEIGHT +
            StoragePanel.PADDING +
            stackedSelectorHeight,
    )
    return Measurements(
        storageX = storageX,
        storageY = storageTop,
        storageWidth = storageWidth,
        storageHeight = storageHeight,
        scrollPanel = scrollPanel,
        scrollbar = Rect(
            scrollPanel.x + scrollPanel.width + StorageScrollbar.GAP,
            scrollPanel.y,
            StorageScrollbar.WIDTH,
            scrollPanel.height,
        ),
        search = search,
        playerBounds = Rect(playerX, playerY, StoragePlayerInventory.WIDTH, StoragePlayerInventory.HEIGHT),
        selectorBounds = selectorBounds,
        totalBounds = totalBounds,
        columns = columns,
        isSelectorVisible = isSelectorVisible,
    )
}

internal object StorageSelectorLayout {
    fun sideX(playerX: Int): Int? =
        (playerX - StorageSelector.SIDE_GAP - StorageSelector.WIDTH)
            .takeIf { it >= StoragePanel.EDGE_MARGIN }

    fun bounds(screenWidth: Int, sideX: Int?, playerY: Int): Rect {
        val x = sideX ?: ((screenWidth - StorageSelector.WIDTH) / 2).coerceAtLeast(StoragePanel.EDGE_MARGIN)
        val y = if (sideX == null) {
            playerY + StoragePlayerInventory.HEIGHT + StorageSelector.STACKED_GAP
        } else {
            playerY + StoragePlayerInventory.HEIGHT - StorageSelector.HEIGHT
        }
        return Rect(x, y, StorageSelector.WIDTH, StorageSelector.HEIGHT)
    }
}

internal fun pageLayouts(measurements: Measurements, activePage: Int?): PageLayoutResult {
    val layouts = linkedMapOf<Int, PageLayout>()
    var xIndex = 0
    var y = measurements.scrollPanel.y - scroll
    var rowHeight = 0
    var completedRowHeight = 0
    for ((pageIndex, page) in displayStorageEntries(activePage)) {
        if (
            ToolkitType.fromPageIndex(pageIndex) == null &&
            !isRiftStoragePage(pageIndex) &&
            pageIndex !in 0 until ProfileStorage.SKYBLOCK_STORAGE_PAGE_COUNT
        ) {
            continue
        }
        if (pageIndex != activePage && !StorageSearchIndex.matches(page)) continue
        val pageHeight = pageHeight(page)
        val pageWidth = if (measurements.isModern) ModernStoragePanel.PAGE_WIDTH else StoragePages.WIDTH
        val x = if (measurements.isModern) {
            modernStoragePageX(measurements, xIndex)
        } else {
            measurements.scrollPanel.x + xIndex * (StoragePages.WIDTH + StoragePages.PADDING)
        }
        layouts[pageIndex] = PageLayout(pageIndex, x, y, pageWidth, pageHeight)
        rowHeight = maxOf(rowHeight, pageHeight)
        xIndex++
        if (xIndex >= measurements.columns) {
            completedRowHeight = rowHeight
            y += rowHeight + StoragePages.PADDING
            xIndex = 0
            rowHeight = 0
        }
    }
    val contentBottom = if (xIndex == 0) {
        y + scroll - measurements.scrollPanel.y
    } else {
        y + rowHeight + scroll - measurements.scrollPanel.y
    }.coerceAtLeast(0)
    val finalRowHeight = if (xIndex == 0) completedRowHeight else rowHeight
    val focusPadding = if (contentBottom > 0) {
        (measurements.scrollPanel.height - finalRowHeight).coerceAtLeast(0) / 2
    } else {
        0
    }
    val contentHeight = contentBottom + focusPadding
    val focusedPageIndex = measurements.focusedPageIndex
    if (measurements.isModern && focusedPageIndex != null && measurements.focusProgress > 0f) {
        val layout = layouts[focusedPageIndex]
        if (layout != null) {
            layouts[focusedPageIndex] = modernFocusedPageLayout(measurements, layout)
        }
    }
    return PageLayoutResult(layouts, contentHeight)
}

internal fun selectorSlotAt(measurements: Measurements, mouseX: Int, mouseY: Int): Int? {
    if (!measurements.selectorBounds.contains(mouseX, mouseY)) return null
    for (slot in 0 until StorageSelector.COLUMNS * StorageSelector.ROWS) {
        val pos = selectorSlotPosition(measurements, slot)
        if (selectorSlotBounds(pos).contains(mouseX, mouseY)) {
            return slot
        }
    }
    return null
}

internal fun selectorPageAt(
    measurements: Measurements,
    activePage: Int?,
    mouseX: Int,
    mouseY: Int,
): Int? {
    if (!measurements.selectorBounds.contains(mouseX, mouseY)) return null
    for (pageIndex in selectorPageIndices(activePage)) {
        val pos = selectorPagePosition(measurements, pageIndex)
        if (selectorSlotBounds(pos).contains(mouseX, mouseY)) {
            return pageIndex.takeIf {
                storageEntryExists(it) ||
                    emptyOverviewStacks[it]?.let(::storageOverviewSlotState) == StorageOverviewSlotState.PLACEHOLDER
            }
        }
    }
    if (activePage == null || !isRiftStoragePage(activePage)) {
        for (type in ToolkitType.entries) {
            val pos = selectorToolkitPosition(measurements, type)
            if (selectorSlotBounds(pos).contains(mouseX, mouseY)) {
                return type.pageIndex.takeIf(::storageEntryExists)
            }
        }
    }
    return null
}

internal fun selectorPagePosition(measurements: Measurements, pageIndex: Int): Point =
    selectorSlotPosition(measurements, pageIndex)

internal fun selectorToolkitPosition(measurements: Measurements, type: ToolkitType): Point =
    selectorSlotPosition(measurements, type.selectorSlot)

internal fun selectorSlotPosition(measurements: Measurements, slot: Int): Point =
    Point(
        measurements.selectorBounds.x +
            StorageSelector.SLOT_OFFSET_X +
            (slot % StorageSelector.COLUMNS) * StorageSelector.SLOT_SIZE,
        measurements.selectorBounds.y +
            StorageSelector.SLOT_OFFSET_Y +
            (slot / StorageSelector.COLUMNS) * StorageSelector.SLOT_SIZE,
    )

internal fun selectorSlotBounds(pos: Point): Rect =
    Rect(pos.x - 1, pos.y - 1, StorageSelector.SLOT_SIZE, StorageSelector.SLOT_SIZE)

internal fun selectorIconStack(pageIndex: Int, page: ProfileStorage.SkyBlockStoragePageData?): ItemStack {
    if (page != null && page.overviewIcon.isNotBlank()) {
        val stack = stackFor(ProfileStorage.SkyBlockStorageItemData(page.overviewIcon))
        if (!stack.isEmpty) return stack
    }
    emptyOverviewStacks[pageIndex]?.let { return it }
    if (page == null) return ItemStack.EMPTY
    return if (
        pageIndex < ProfileStorage.SKYBLOCK_STORAGE_ENDER_CHEST_PAGES ||
        isRiftStoragePage(pageIndex)
    ) {
        ItemStack(Blocks.ENDER_CHEST.asItem())
    } else {
        ItemStack(Blocks.CHEST.asItem())
    }
}

internal fun playerSlotPosition(measurements: Measurements, slot: Int): Point =
    if (slot in 0 until StoragePlayerInventory.HOTBAR_SLOT_COUNT) {
        Point(
            measurements.playerBounds.x + StoragePlayerInventory.SLOT_X_OFFSET + slot * StorageSlots.SIZE,
            measurements.playerBounds.y + StoragePlayerInventory.HOTBAR_Y_OFFSET,
        )
    } else {
        val inventorySlot = slot - StoragePlayerInventory.HOTBAR_SLOT_COUNT
        Point(
            measurements.playerBounds.x +
                StoragePlayerInventory.SLOT_X_OFFSET +
                (inventorySlot % StoragePages.COLUMNS) * StorageSlots.SIZE,
            measurements.playerBounds.y +
                StoragePlayerInventory.INVENTORY_Y_OFFSET +
                (inventorySlot / StoragePages.COLUMNS) * StorageSlots.SIZE,
        )
    }

internal fun pageSlotX(layout: PageLayout, slot: Int): Int =
    layout.x +
        (layout.width - StoragePages.COLUMNS * StorageSlots.SIZE) / 2 +
        StorageSlots.BORDER +
        (slot % StoragePages.COLUMNS) * StorageSlots.SIZE

internal fun pageSlotY(layout: PageLayout, slot: Int): Int =
    layout.y +
        Minecraft.getInstance().font.lineHeight +
        StoragePages.TITLE_SLOT_GAP +
        (slot / StoragePages.COLUMNS) * StorageSlots.SIZE

internal fun pageSlotPosition(
    measurements: Measurements,
    handle: StorageHandle,
    layout: PageLayout?,
    slot: Slot,
): Point? {
    val rows = handle.gridRows()?.coerceAtLeast(0) ?: return null
    val pageSlot = slot.containerSlot - handle.slotOffset()
    if (layout == null || pageSlot !in 0 until rows * StoragePages.COLUMNS) return null
    val x = pageSlotX(layout, pageSlot)
    val y = pageSlotY(layout, pageSlot)
    if (!slotInside(storagePageVisibleBounds(measurements, layout), x, y)) return null
    return Point(x, y)
}

internal fun activePageSlots(screen: ContainerScreen, handle: StorageHandle): Map<Int, Slot> =
    screen.nonPlayerSlots()
        .mapNotNull { slot ->
            val pageSlot = slot.containerSlot - handle.slotOffset()
            if (pageSlot < 0) null else pageSlot to slot
        }
        .toMap()
