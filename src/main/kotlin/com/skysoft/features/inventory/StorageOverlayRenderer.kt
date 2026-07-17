package com.skysoft.features.inventory

import com.skysoft.data.ProfileStorage
import com.skysoft.gui.tooltip.SkysoftNativeTooltip
import com.skysoft.utils.gui.Rect
import com.skysoft.utils.gui.itemWithDecorations
import com.skysoft.utils.render.LegacyTextRenderer
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.inventory.ContainerScreen
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

internal var hoveredStorageItem: ItemStack = ItemStack.EMPTY
    private set

internal fun drawStoragePanel(context: GuiGraphicsExtractor, measurements: Measurements) {
    context.fill(
        measurements.storageX,
        measurements.storageY,
        measurements.storageX + measurements.storageWidth,
        measurements.storageY + measurements.storageHeight,
        StorageColors.PANEL,
    )
    context.outline(
        measurements.storageX,
        measurements.storageY,
        measurements.storageWidth,
        measurements.storageHeight,
        StorageColors.PANEL_OUTLINE,
    )
}

internal fun drawPages(
    context: GuiGraphicsExtractor,
    screen: ContainerScreen,
    measurements: Measurements,
    layouts: Map<Int, PageLayout>,
    activeHandle: StorageHandle?,
    mouseX: Int,
    mouseY: Int,
) {
    hoveredStorageItem = ItemStack.EMPTY
    val activePage = activeHandle?.entryIndex()
    val activeSlots = activeHandle?.let { activePageSlots(screen, it) }.orEmpty()
    context.enableScissor(
        measurements.scrollPanel.x,
        measurements.scrollPanel.y,
        measurements.scrollPanel.x + measurements.scrollPanel.width,
        measurements.scrollPanel.y + measurements.scrollPanel.height,
    )
    try {
        for (layout in layouts.values) {
            if (!layout.intersects(measurements.scrollPanel)) continue
            val page = storageEntry(layout.pageIndex) ?: continue
            drawPage(
                context,
                screen,
                page,
                layout,
                measurements.scrollPanel,
                layout.pageIndex == activePage,
                activeSlots,
                mouseX,
                mouseY,
            )
        }
    } finally {
        context.disableScissor()
    }
}

internal fun drawPage(
    context: GuiGraphicsExtractor,
    screen: ContainerScreen,
    page: ProfileStorage.SkyBlockStoragePageData,
    layout: PageLayout,
    visibleBounds: Rect,
    active: Boolean,
    activeSlots: Map<Int, Slot>,
    mouseX: Int,
    mouseY: Int,
) {
    val titleColor = if (active) "§b" else "§f"
    val title = titleDisplayText(titleText(layout.pageIndex))
    context.fill(
        layout.x,
        layout.y,
        layout.x + layout.width,
        layout.y + layout.height,
        StorageColors.PAGE_PANEL,
    )
    context.outline(
        layout.x,
        layout.y,
        layout.width,
        layout.height,
        if (active) StorageColors.SELECTED else StorageColors.PAGE_PANEL_OUTLINE,
    )
    if (editingTitlePage == layout.pageIndex) {
        val bounds = titleBounds(layout, title)
        context.fill(
            bounds.x - StorageTitle.EDIT_PADDING,
            bounds.y - StorageTitle.EDIT_PADDING,
            layout.x + layout.width - StorageTitle.BOX_END_INSET,
            bounds.y + bounds.height + StorageTitle.EDIT_PADDING,
            StorageColors.TITLE_EDIT_BACKGROUND,
        )
        context.outline(
            bounds.x - StorageTitle.EDIT_PADDING,
            bounds.y - StorageTitle.EDIT_PADDING,
            layout.width - StorageTitle.BOX_WIDTH_INSET,
            bounds.height + StorageTitle.BOX_END_INSET,
            StorageColors.SELECTED,
        )
    }
    LegacyTextRenderer.draw(
        context,
        titleColor + title,
        layout.x + StorageTitle.X_OFFSET,
        layout.y + StorageTitle.Y_OFFSET,
        defaultColor = StorageColors.TEXT_WHITE,
    )
    if (
        editingTitlePage == layout.pageIndex &&
        (System.currentTimeMillis() / StorageTitle.CURSOR_BLINK_MILLIS) % StorageTitle.CURSOR_BLINK_PHASES == 0L
    ) {
        val cursorX = (layout.x + StorageTitle.X_OFFSET + LegacyTextRenderer.width(title))
            .coerceAtMost(layout.x + layout.width - StorageTitle.CURSOR_RIGHT_INSET)
        context.fill(
            cursorX,
            layout.y + StorageTitle.CURSOR_MIN_Y_OFFSET,
            cursorX + StorageSlots.BORDER,
            layout.y + StorageTitle.CURSOR_MAX_Y_OFFSET,
            StorageColors.TEXT_WHITE,
        )
    }
    if (page.rows <= 0) {
        LegacyTextRenderer.draw(
            context,
            "§7Open this page to load it",
            layout.x + StorageTitle.X_OFFSET,
            layout.y + StorageTitle.UNLOADED_PAGE_TEXT_Y_OFFSET,
            shadow = false,
        )
        return
    }
    for (index in 0 until page.rows * StoragePages.COLUMNS) {
        val slotX = pageSlotX(layout, index)
        val slotY = pageSlotY(layout, index)
        if (!slotIntersects(visibleBounds, slotX, slotY)) continue
        drawSlotBackground(context, slotX, slotY)
        val hovered = isSlotHovered(mouseX, mouseY, slotX, slotY) && context.containsPointInScissor(mouseX, mouseY)
        val storedItem = page.items.getOrNull(index)
        val activeSlot = if (active) activeSlots[index] else null
        val stack = activeSlot?.item ?: if (active) ItemStack.EMPTY else stackFor(storedItem)
        if (!stack.isEmpty) {
            if (
                StorageSearchIndex.hasQuery &&
                (if (active) StorageSearchIndex.matches(stack) else StorageSearchIndex.matches(storedItem))
            ) {
                InventoryItemSearchHighlight.render(context, slotX, slotY)
            }
            if (!SmoothSwapping.shouldSuppressSlot(screen, activeSlot)) {
                context.itemWithDecorations(stack, slotX, slotY)
            }
        }
        if (hovered) {
            drawSlotHover(context, slotX, slotY)
            if (!stack.isEmpty) {
                hoveredStorageItem = stack
                context.setTooltipForNextFrame(Minecraft.getInstance().font, stack, mouseX, mouseY)
            }
        }
    }
}

internal fun drawScrollBar(context: GuiGraphicsExtractor, measurements: Measurements, contentHeight: Int) {
    val bar = measurements.scrollbar
    context.fill(bar.x, bar.y, bar.x + bar.width, bar.y + bar.height, StorageColors.SCROLLBAR_TRACK)
    context.outline(bar.x, bar.y, bar.width, bar.height, StorageColors.SCROLLBAR_OUTLINE)
    val knob = scrollbarKnobBounds(measurements, contentHeight)
    context.fill(knob.x, knob.y, knob.x + knob.width, knob.y + knob.height, StorageColors.SCROLLBAR_KNOB)
}

internal fun drawSearchBox(context: GuiGraphicsExtractor, measurements: Measurements) {
    val box = measurements.search
    context.fill(box.x, box.y, box.x + box.width, box.y + box.height, StorageColors.SEARCH_BACKGROUND)
    context.outline(
        box.x,
        box.y,
        box.width,
        box.height,
        if (searchFocused) StorageColors.SELECTED else StorageColors.PANEL_OUTLINE,
    )
    val display = if (searchText.isEmpty() && !searchFocused) "§8Search..." else "§f$searchText"
    LegacyTextRenderer.draw(
        context,
        display,
        box.x + StorageSearch.TEXT_X_OFFSET,
        box.y + StorageSearch.TEXT_Y_OFFSET,
        shadow = false,
    )
}

internal fun drawPlayerInventoryPanel(
    context: GuiGraphicsExtractor,
    screen: ContainerScreen,
    measurements: Measurements,
    mouseX: Int,
    mouseY: Int,
) {
    val bounds = measurements.playerBounds
    val playerInventory = Minecraft.getInstance().player?.inventory
    context.fill(bounds.x, bounds.y, bounds.x + bounds.width, bounds.y + bounds.height, StorageColors.PLAYER_PANEL)
    context.outline(bounds.x, bounds.y, bounds.width, bounds.height, StorageColors.PANEL_OUTLINE)
    for (slot in 9 until 36) {
        drawPlayerSlot(context, screen, playerInventory, measurements, slot, mouseX, mouseY)
    }
    for (slot in 0 until 9) {
        drawPlayerSlot(context, screen, playerInventory, measurements, slot, mouseX, mouseY)
    }
}

internal fun drawPlayerSlot(
    context: GuiGraphicsExtractor,
    screen: ContainerScreen,
    playerInventory: net.minecraft.world.entity.player.Inventory?,
    measurements: Measurements,
    containerSlot: Int,
    mouseX: Int,
    mouseY: Int,
) {
    val pos = playerSlotPosition(measurements, containerSlot)
    drawSlotBackground(context, pos.x, pos.y)
    val slot = screen.menu.slots.firstOrNull {
        playerInventory != null && it.container === playerInventory && it.containerSlot == containerSlot
    }
    val stack = slot?.item ?: ItemStack.EMPTY
    if (!stack.isEmpty && !SmoothSwapping.shouldSuppressSlot(screen, slot)) {
        context.itemWithDecorations(stack, pos.x, pos.y)
    }
    if (isSlotHovered(mouseX, mouseY, pos.x, pos.y)) {
        drawSlotHover(context, pos.x, pos.y)
        if (!stack.isEmpty) {
            hoveredStorageItem = stack
            context.setTooltipForNextFrame(Minecraft.getInstance().font, stack, mouseX, mouseY)
        }
    }
}

internal fun drawStorageSelectorPanel(
    context: GuiGraphicsExtractor,
    measurements: Measurements,
    activePage: Int?,
    mouseX: Int,
    mouseY: Int,
) {
    val bounds = measurements.selectorBounds
    context.fill(bounds.x, bounds.y, bounds.x + bounds.width, bounds.y + bounds.height, StorageColors.PAGE_PANEL)
    context.outline(bounds.x, bounds.y, bounds.width, bounds.height, StorageColors.PAGE_PANEL_OUTLINE)
    LegacyTextRenderer.draw(
        context,
        "§fShortcut",
        bounds.x + StorageSelector.TEXT_X_OFFSET,
        bounds.y + StorageSelector.TEXT_Y_OFFSET,
        defaultColor = StorageColors.TEXT_WHITE,
    )
    for (slot in 0 until StorageSelector.COLUMNS * StorageSelector.ROWS) {
        val pos = selectorSlotPosition(measurements, slot)
        drawSelectorSlotBackground(context, pos.x, pos.y)
    }
    for (pageIndex in 0 until ProfileStorage.SKYBLOCK_STORAGE_PAGE_COUNT) {
        val page = storage.skyBlockStoragePages[pageIndex]
        val pos = selectorPagePosition(measurements, pageIndex)
        val stack = selectorIconStack(pageIndex, page)
        if (!stack.isEmpty) drawMiniItem(context, stack, pos.x, pos.y)
        if (pageIndex == activePage) {
            context.outline(
                pos.x - 1,
                pos.y - 1,
                StorageSelector.SLOT_SIZE,
                StorageSelector.SLOT_SIZE,
                StorageColors.SELECTED,
            )
        }
    }
    for (type in ToolkitType.entries) {
        val pos = selectorToolkitPosition(measurements, type)
        drawMiniItem(context, toolkitShortcutStack(type), pos.x, pos.y)
        if (type.pageIndex == activePage) {
            context.outline(
                pos.x - 1,
                pos.y - 1,
                StorageSelector.SLOT_SIZE,
                StorageSelector.SLOT_SIZE,
                StorageColors.SELECTED,
            )
        }
    }
    selectorSlotAt(measurements, mouseX, mouseY)?.let { hoveredSlot ->
        val pos = selectorSlotPosition(measurements, hoveredSlot)
        drawSlotHover(context, pos.x, pos.y)
        val hoveredToolkit = ToolkitType.entries.firstOrNull { it.selectorSlot == hoveredSlot }
        val stack = when {
            hoveredSlot in 0 until ProfileStorage.SKYBLOCK_STORAGE_PAGE_COUNT ->
                selectorIconStack(hoveredSlot, storage.skyBlockStoragePages[hoveredSlot])

            hoveredToolkit != null -> toolkitShortcutStack(hoveredToolkit)
            else -> ItemStack.EMPTY
        }
        if (hoveredToolkit != null) {
            SkysoftNativeTooltip.setForNextFrame(context, hoveredToolkit.shortcutTooltip(), mouseX, mouseY)
        } else if (!stack.isEmpty) {
            context.setTooltipForNextFrame(Minecraft.getInstance().font, stack, mouseX, mouseY)
        }
    }
}

internal fun toolkitShortcutStack(type: ToolkitType): ItemStack {
    val isAvailable = storageEntryExists(type.pageIndex)
    if (isAvailable) {
        val stack = stackFor(ProfileStorage.SkyBlockStorageItemData(storage.skyBlockToolkitIcon))
        if (!stack.isEmpty) return stack
    }
    return ItemStack(if (isAvailable) Items.CHEST else Items.BARRIER).apply {
        set(
            DataComponents.CUSTOM_NAME,
            Component.literal(type.shortcutTitle(isAvailable))
                .withStyle { it.withItalic(false) },
        )
    }
}

internal fun drawSlotHover(context: GuiGraphicsExtractor, x: Int, y: Int) {
    context.fill(x, y, x + StorageSlots.INNER_SIZE, y + StorageSlots.INNER_SIZE, StorageColors.SLOT_HOVER)
}

internal fun drawSelectorSlotBackground(context: GuiGraphicsExtractor, x: Int, y: Int) {
    context.fill(x, y, x + StorageSlots.INNER_SIZE, y + StorageSlots.INNER_SIZE, StorageColors.SELECTOR_SLOT)
}

internal fun drawMiniItem(context: GuiGraphicsExtractor, stack: ItemStack, x: Int, y: Int) {
    context.itemWithDecorations(stack, x, y)
}

internal fun drawSlotBackground(context: GuiGraphicsExtractor, x: Int, y: Int) {
    context.fill(
        x - StorageSlots.BORDER,
        y - StorageSlots.BORDER,
        x + StorageSlots.SIZE - StorageSlots.BORDER,
        y + StorageSlots.SIZE - StorageSlots.BORDER,
        StorageColors.SLOT_BACKGROUND,
    )
    context.fill(x, y, x + StorageSlots.INNER_SIZE, y + StorageSlots.INNER_SIZE, StorageColors.SELECTOR_SLOT)
}

