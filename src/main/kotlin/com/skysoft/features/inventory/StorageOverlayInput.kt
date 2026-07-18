package com.skysoft.features.inventory

import com.skysoft.data.ProfileStorage
import com.skysoft.mixin.AbstractContainerScreenAccessor
import com.skysoft.utils.gui.Rect
import com.skysoft.utils.input.InputHandlingResult
import kotlin.math.roundToInt
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.world.inventory.ContainerInput
import net.minecraft.world.inventory.Slot
import org.lwjgl.glfw.GLFW

internal fun isSlotHovered(mouseX: Int, mouseY: Int, x: Int, y: Int): Boolean =
    mouseX in x until x + StorageSlots.INNER_SIZE && mouseY in y until y + StorageSlots.INNER_SIZE

internal fun routeActivePageSlotClick(
    screen: AbstractContainerScreen<*>,
    handle: StorageHandle,
    click: MouseButtonEvent,
): InputHandlingResult {
    val activePage = handle.entryIndex()
    val rows = handle.gridRows()
    if (activePage == null || rows == null) return InputHandlingResult.IGNORED
    val mouseX = click.x().toInt()
    val mouseY = click.y().toInt()
    if (!outsideVanillaContainer(screen, mouseX, mouseY)) return InputHandlingResult.IGNORED
    val measurements = measurements(screen.width, screen.height)
    val slotAndAction = pageLayouts(measurements, activePage).pages[activePage]?.let { activeLayout ->
        activePageSlotAt(screen, measurements, handle, rows, activeLayout, mouseX, mouseY)?.let { slot ->
            slotClickAction(screen, click, slot)?.let { action -> slot to action }
        }
    } ?: return InputHandlingResult.IGNORED
    val (slot, action) = slotAndAction
    (screen as AbstractContainerScreenAccessor).skysoftSlotClicked(
        slot,
        slot.index,
        action.button,
        action.input,
    )
    screen.skysoftSetSkipNextRelease(true)
    storageOverlayLayoutScreen(screen)
    return InputHandlingResult.CONSUMED
}

internal fun activePageSlotAt(
    screen: AbstractContainerScreen<*>,
    measurements: Measurements,
    handle: StorageHandle,
    rows: Int,
    layout: PageLayout,
    mouseX: Int,
    mouseY: Int,
): Slot? {
    if (!measurements.scrollPanel.contains(mouseX, mouseY)) return null
    for (slot in screen.menu.slots) {
        val pageSlot = slot.containerSlot - handle.slotOffset()
        if (pageSlot !in 0 until rows.coerceAtLeast(0) * StoragePages.COLUMNS) continue
        val slotX = pageSlotX(layout, pageSlot)
        val slotY = pageSlotY(layout, pageSlot)
        if (slotIntersects(measurements.scrollPanel, slotX, slotY) && isSlotHovered(mouseX, mouseY, slotX, slotY)) {
            return slot
        }
    }
    return null
}

internal fun slotClickAction(
    screen: AbstractContainerScreen<*>,
    click: MouseButtonEvent,
    slot: Slot,
): SlotClickAction? {
    val minecraft = Minecraft.getInstance()
    val options = minecraft.options
    if (options.keyPickItem.matchesMouse(click) && minecraft.player?.hasInfiniteMaterials() == true) {
        return SlotClickAction(click.button(), ContainerInput.CLONE)
    }
    if (screen.menu.carried.isEmpty && options.keySwapOffhand.matchesMouse(click)) {
        return SlotClickAction(StoragePlayerInventory.OFFHAND_SWAP_BUTTON, ContainerInput.SWAP)
    }
    if (screen.menu.carried.isEmpty) {
        options.keyHotbarSlots.forEachIndexed { index, key ->
            if (key.matchesMouse(click)) return SlotClickAction(index, ContainerInput.SWAP)
        }
    }
    if (click.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT && click.button() != GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
        return null
    }
    val input = if (click.hasShiftDown() && slot.hasItem()) ContainerInput.QUICK_MOVE else ContainerInput.PICKUP
    return SlotClickAction(click.button(), input)
}

internal fun outsideVanillaContainer(screen: AbstractContainerScreen<*>, mouseX: Int, mouseY: Int): Boolean {
    val accessor = screen as AbstractContainerScreenAccessor
    val left = accessor.skysoftGetLeftPos()
    val top = accessor.skysoftGetTopPos()
    return mouseX !in left until left + accessor.skysoftGetImageWidth() ||
        mouseY !in top until top + accessor.skysoftGetImageHeight()
}

internal fun scrollbarKnobBounds(measurements: Measurements, contentHeight: Int): Rect {
    val bar = measurements.scrollbar
    val knobHeight = scrollbarKnobHeight(measurements, contentHeight)
    val knobTravel = (bar.height - knobHeight).coerceAtLeast(0)
    val maxScroll = maxScroll(measurements, contentHeight)
    val knobY = bar.y + if (maxScroll <= 0) {
        0
    } else {
        (scroll / maxScroll.toFloat() * knobTravel).roundToInt()
    }
    return Rect(bar.x, knobY, bar.width, knobHeight)
}

internal fun scrollbarKnobHeight(measurements: Measurements, contentHeight: Int): Int {
    val bar = measurements.scrollbar
    if (maxScroll(measurements, contentHeight) <= 0) return bar.height
    val height = contentHeight.coerceAtLeast(bar.height)
    return (bar.height * (bar.height / height.toFloat()))
        .roundToInt()
        .coerceIn(StorageScrollbar.MIN_KNOB_HEIGHT, bar.height)
}

internal fun slotIntersects(rect: Rect, x: Int, y: Int): Boolean =
    x < rect.x + rect.width &&
        x + StorageSlots.INNER_SIZE > rect.x &&
        y < rect.y + rect.height &&
        y + StorageSlots.INNER_SIZE > rect.y

internal fun slotInside(rect: Rect, x: Int, y: Int): Boolean =
    x >= rect.x &&
        x + StorageSlots.INNER_SIZE <= rect.x + rect.width &&
        y >= rect.y &&
        y + StorageSlots.INNER_SIZE <= rect.y + rect.height

internal fun setSlotPosition(slot: Slot, x: Int, y: Int) {
    slot.x = x
    slot.y = y
}

internal fun pageHeight(page: ProfileStorage.SkyBlockStoragePageData): Int =
    if (page.rows <= 0) {
        StoragePages.EMPTY_HEIGHT
    } else {
        Minecraft.getInstance().font.lineHeight +
            StoragePages.CONTENT_TOP_PADDING +
            page.rows * StorageSlots.SIZE +
            StorageSearch.GAP
    }

internal fun pointInSearch(measurements: Measurements, x: Int, y: Int): Boolean = measurements.search.contains(x, y)

internal fun updateSearchFocusFromClick(measurements: Measurements, mouseX: Int, mouseY: Int) {
    storageSearchField.focused = storageSearchField.focused && measurements.search.contains(mouseX, mouseY)
}

internal fun coerceScroll(measurements: Measurements, contentHeight: Int) {
    coerceStorageScroll(maxScroll(measurements, contentHeight))
}

internal fun maxScroll(measurements: Measurements, contentHeight: Int): Int =
    (contentHeight - measurements.scrollPanel.height).coerceAtLeast(0)

internal object StorageOverviewSlots {
    fun pageIndexForSlot(slot: Int): Int? = when (slot) {
        in FIRST_CHEST_SLOT until BACKPACK_SECTION_START -> slot - FIRST_CHEST_SLOT
        in FIRST_BACKPACK_SLOT until SLOT_END ->
            slot - FIRST_BACKPACK_SLOT + ProfileStorage.SKYBLOCK_STORAGE_ENDER_CHEST_PAGES
        else -> null
    }

    fun slotForPageIndex(pageIndex: Int): Int? = when (pageIndex) {
        in 0 until ProfileStorage.SKYBLOCK_STORAGE_ENDER_CHEST_PAGES -> FIRST_CHEST_SLOT + pageIndex
        in ProfileStorage.SKYBLOCK_STORAGE_ENDER_CHEST_PAGES until ProfileStorage.SKYBLOCK_STORAGE_PAGE_COUNT ->
            FIRST_BACKPACK_SLOT + pageIndex - ProfileStorage.SKYBLOCK_STORAGE_ENDER_CHEST_PAGES
        else -> null
    }

    private const val FIRST_CHEST_SLOT = StoragePlayerInventory.HOTBAR_SLOT_COUNT
    private const val BACKPACK_SECTION_START = 18
    private const val FIRST_BACKPACK_SLOT = 27
    private const val SLOT_END = 45
}

