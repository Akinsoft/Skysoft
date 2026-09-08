package com.skysoft.features.inventory.itemlist

import com.skysoft.config.ItemListSettingsConfig
import com.skysoft.config.ItemListSourcesConfig
import com.skysoft.config.SkysoftConfigGui
import com.skysoft.config.core.HudPosition
import com.skysoft.gui.HudEditorElement
import com.skysoft.gui.HudEditorSnapshot
import com.skysoft.gui.hudEditorSnapshot
import com.skysoft.utils.gui.Rect
import com.skysoft.utils.gui.TextFieldState
import com.skysoft.utils.input.InputHandlingResult
import com.skysoft.utils.render.LegacyTextRenderer
import net.minecraft.client.gui.GuiGraphicsExtractor
import java.util.Locale
import kotlin.math.roundToInt

internal class ItemListHudEditorElement(private val currentLayout: () -> ItemListLayout?) : HudEditorElement {
    private val editorPosition = HudPosition(
        -ItemListLayout.OUTER_MARGIN,
        ItemListLayout.OUTER_MARGIN,
        centerY = false,
    ).rememberDefault()
    private var editorIsResizing = false
    private var editorResizeStartColumns = ItemListSettingsConfig.DEFAULT_COLUMNS
    private var editorResizeMaximumColumns = ItemListSettingsConfig.MAX_COLUMNS

    override val id: String = "item_list"
    override val label: String = "Item List"
    override val position: HudPosition = editorPosition
    override val canMove: Boolean = false
    override val canScale: Boolean = false
    override val hasEditorBackground: Boolean = false
    override val editorSelectionPriority: Int = ITEM_LIST_EDITOR_SELECTION_PRIORITY
    override val editorLeftPadding: Int
        get() = LegacyTextRenderer.width(EDITOR_RESIZE_ARROW) + EDITOR_RESIZE_ARROW_GAP
    override val usesInventoryScale: Boolean = true
    override val requiresInventoryScreen: Boolean = true
    override fun width(): Int = currentLayout()?.panel?.width ?: 0
    override fun height(): Int = currentLayout()?.panel?.height ?: 0
    override fun isVisible(): Boolean = currentLayout() != null

    override fun renderEditor(context: GuiGraphicsExtractor) {
        LegacyTextRenderer.draw(
            context,
            EDITOR_RESIZE_ARROW,
            -editorLeftPadding,
            (height() - EDITOR_RESIZE_ARROW_HEIGHT) / 2,
        )
    }

    override fun beginEditorDrag(localX: Int, localY: Int, width: Int, height: Int) {
        val layout = currentLayout() ?: return
        val panelRight = layout.panel.x + layout.panel.width
        val arrowCenterY = height / 2
        val arrowTop = arrowCenterY - EDITOR_RESIZE_ARROW_HIT_RADIUS
        val arrowBottom = arrowCenterY + EDITOR_RESIZE_ARROW_HIT_RADIUS
        editorIsResizing = localX in -editorLeftPadding until 0 &&
            localY in arrowTop..arrowBottom
        editorResizeStartColumns = layout.panel.width / ItemListLayout.DEFAULT_SLOT_SIZE
        val availableColumns = (panelRight - ItemListLayout.OUTER_MARGIN) /
            ItemListLayout.DEFAULT_SLOT_SIZE
        editorResizeMaximumColumns = availableColumns
            .coerceIn(ItemListSettingsConfig.MIN_COLUMNS, ItemListSettingsConfig.MAX_COLUMNS)
    }

    override fun applyEditorDrag(deltaX: Int, deltaY: Int): InputHandlingResult {
        if (!editorIsResizing) return InputHandlingResult.IGNORED
        SkysoftConfigGui.config().inventory.itemList.settings.columns = itemListColumnsAfterEditorDrag(
            editorResizeStartColumns,
            -deltaX,
            ItemListLayout.DEFAULT_SLOT_SIZE,
            editorResizeMaximumColumns,
        )
        return InputHandlingResult.CONSUMED
    }

    override fun applyEditorScroll(scrollY: Double): InputHandlingResult {
        val settings = SkysoftConfigGui.config().inventory.itemList.settings
        settings.itemScale = itemListScaleAfterEditorScroll(settings.itemScale, scrollY)
        return InputHandlingResult.CONSUMED
    }

    override fun resetEditorState() {
        val settings = SkysoftConfigGui.config().inventory.itemList.settings
        settings.itemScale = ItemListSettingsConfig.DEFAULT_ITEM_SCALE
        settings.columns = ItemListSettingsConfig.DEFAULT_COLUMNS
        settings.rows = ItemListSettingsConfig.DEFAULT_ROWS
    }

    override fun captureEditorState(): HudEditorSnapshot {
        val positionSnapshot = position.snapshot()
        val settings = SkysoftConfigGui.config().inventory.itemList.settings
        val settingsSnapshot = Triple(settings.itemScale, settings.columns, settings.rows)
        return hudEditorSnapshot(positionSnapshot to settingsSnapshot) {
            position.restore(positionSnapshot)
            settings.itemScale = settingsSnapshot.first
            settings.columns = settingsSnapshot.second
            settings.rows = settingsSnapshot.third
        }
    }

    override fun editorDetailsLines(): List<String> {
        val settings = SkysoftConfigGui.config().inventory.itemList.settings
        val visibleRows = currentLayout()?.rows ?: settings.rows
        val rows = if (settings.rows == ItemListSettingsConfig.DEFAULT_ROWS) {
            "$visibleRows (auto)"
        } else {
            visibleRows.toString()
        }
        val width = currentLayout()?.panel?.width?.div(ItemListLayout.DEFAULT_SLOT_SIZE) ?: settings.columns
        val itemScale = currentLayout()?.itemScale ?: settings.itemScale
        return listOf(
            "§7Width: §e$width§7, visible grid: §e${currentLayout()?.columns ?: 0} x $rows",
            "§7Item size: §e${"%.1f".format(Locale.US, itemScale)}x",
        )
    }

    override fun editorActionLines(): List<String> = listOf(
        "§eDrag the centered ↔ arrow §7to resize width",
        "§eScroll-Wheel §7to resize items",
        "§eRight-click §7to open settings",
        "§eR §7to reset",
    )

    override fun openConfig() = SkysoftConfigGui.open("Item List")
}

internal class ItemListSearchHudEditorElement(
    private val currentLayout: () -> ItemListLayout?,
    private val searchField: TextFieldState,
) : HudEditorElement {
    override val id: String = "item_list_search"
    override val label: String = "Item List Search"
    override val position get() = SkysoftConfigGui.config().inventory.itemList.sources.searchPosition
    override val canScale: Boolean = false
    override val keepsInsideScreen: Boolean = true
    override val editorSelectionPriority: Int = ITEM_LIST_EDITOR_SELECTION_PRIORITY
    override val usesInventoryScale: Boolean = true
    override val requiresInventoryScreen: Boolean = true
    override fun width(): Int = currentLayout()?.footer?.width ?: ItemListLayout.DEFAULT_FOOTER_WIDTH
    override fun height(): Int = ItemListLayout.FOOTER_HEIGHT
    override fun isVisible(): Boolean = currentLayout() != null
    override fun renderEditor(context: GuiGraphicsExtractor) {
        val isSettingsButtonHidden = SkysoftConfigGui.config().inventory.itemList.sources.isSettingsButtonHidden
        val footerWidth = width()
        val searchWidth = if (isSettingsButtonHidden) {
            footerWidth
        } else {
            footerWidth - ItemListLayout.CONFIG_BUTTON_WIDTH - ItemListLayout.CONTROL_GAP
        }
        searchField.render(context, 0, 0, searchWidth, ItemListLayout.FOOTER_HEIGHT, "Search items and mobs...")
        if (!isSettingsButtonHidden) {
            drawItemListSettingsButton(
                context,
                Rect(
                    searchWidth + ItemListLayout.CONTROL_GAP,
                    0,
                    ItemListLayout.CONFIG_BUTTON_WIDTH,
                    ItemListLayout.FOOTER_HEIGHT,
                ),
                hovered = false,
            )
        }
    }

    override fun beginEditorDrag(localX: Int, localY: Int, width: Int, height: Int) {
        val sources = SkysoftConfigGui.config().inventory.itemList.sources
        if (sources.searchPosition.isAtDefault()) sources.searchWidth = width
    }

    override fun applyEditorScroll(scrollY: Double): InputHandlingResult {
        val itemList = SkysoftConfigGui.config().inventory.itemList
        if (itemList.sources.searchPosition.isAtDefault()) {
            itemList.settings.itemScale = itemListScaleAfterEditorScroll(itemList.settings.itemScale, scrollY)
        } else {
            itemList.sources.searchWidth = itemListSearchWidthAfterEditorScroll(
                width(),
                scrollY,
            )
        }
        return InputHandlingResult.CONSUMED
    }

    override fun resetEditorState() {
        val sources = SkysoftConfigGui.config().inventory.itemList.sources
        sources.searchPosition.resetToDefault()
        sources.searchWidth = ItemListSourcesConfig.DEFAULT_SEARCH_WIDTH
    }

    override fun captureEditorState(): HudEditorSnapshot {
        val itemList = SkysoftConfigGui.config().inventory.itemList
        val sources = itemList.sources
        val positionSnapshot = sources.searchPosition.snapshot()
        val values = positionSnapshot to (sources.searchWidth to itemList.settings.itemScale)
        return hudEditorSnapshot(values) {
            sources.searchPosition.restore(positionSnapshot)
            sources.searchWidth = values.second.first
            itemList.settings.itemScale = values.second.second
        }
    }

    override fun editorDetailsLines(): List<String> {
        val sources = SkysoftConfigGui.config().inventory.itemList.sources
        return listOf(
            if (sources.searchPosition.isAtDefault()) {
                "§7Attached to Item List sizing"
            } else {
                "§7Independent width: §e${width()}px"
            },
        )
    }

    override fun editorActionLines(): List<String> {
        val isAttached = SkysoftConfigGui.config().inventory.itemList.sources.searchPosition.isAtDefault()
        return listOf(
            if (isAttached) "§eDrag §7to detach and move" else "§eDrag §7to move",
            if (isAttached) "§eScroll-Wheel §7to resize item slots" else "§eScroll-Wheel §7to resize width",
            "§eHold Shift §7to snap",
            "§eRight-click §7to open settings",
            if (isAttached) "§eR §7to reset" else "§eR §7to reconnect",
        )
    }

    override fun openConfig() = SkysoftConfigGui.open("Item List")
}

internal fun itemListScaleAfterEditorScroll(currentScale: Float, scrollY: Double): Float = when {
    scrollY > 0.0 -> currentScale + ItemListSettingsConfig.ITEM_SCALE_STEP
    scrollY < 0.0 -> currentScale - ItemListSettingsConfig.ITEM_SCALE_STEP
    else -> currentScale
}.coerceIn(ItemListSettingsConfig.MIN_ITEM_SCALE, ItemListSettingsConfig.MAX_ITEM_SCALE)

internal fun itemListColumnsAfterEditorDrag(
    startColumns: Int,
    horizontalDelta: Int,
    slotSize: Int,
    maximumColumns: Int = ItemListSettingsConfig.MAX_COLUMNS,
): Int {
    require(slotSize > 0)
    require(maximumColumns >= ItemListSettingsConfig.MIN_COLUMNS)
    return (startColumns + (horizontalDelta.toFloat() / slotSize).roundToInt()).coerceIn(
        ItemListSettingsConfig.MIN_COLUMNS,
        maximumColumns,
    )
}

internal fun itemListSearchWidthAfterEditorScroll(currentWidth: Int, scrollY: Double): Int = when {
    scrollY > 0.0 -> currentWidth + ItemListSourcesConfig.SEARCH_WIDTH_STEP
    scrollY < 0.0 -> currentWidth - ItemListSourcesConfig.SEARCH_WIDTH_STEP
    else -> currentWidth
}.coerceIn(ItemListSourcesConfig.MIN_SEARCH_WIDTH, ItemListSourcesConfig.MAX_SEARCH_WIDTH)

private const val ITEM_LIST_EDITOR_SELECTION_PRIORITY = -1
private const val EDITOR_RESIZE_ARROW = "↔"
private const val EDITOR_RESIZE_ARROW_GAP = 4
private const val EDITOR_RESIZE_ARROW_HEIGHT = 9
private const val EDITOR_RESIZE_ARROW_HIT_RADIUS = 8
