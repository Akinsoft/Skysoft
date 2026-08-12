package com.skysoft.features.profit

import com.skysoft.config.ProfitTrackerPriceSource
import com.skysoft.data.skyblock.ItemListEntry
import com.skysoft.data.skyblock.ItemListEntryKind
import com.skysoft.data.skyblock.SkyBlockDataLoadState
import com.skysoft.data.skyblock.SkyBlockDataRepository
import com.skysoft.data.skyblock.SkyBlockItemRarity
import com.skysoft.data.skyblock.SkyBlockRarity
import com.skysoft.features.pets.PetRepository
import com.skysoft.gui.OverlayControlTooltips
import com.skysoft.utils.ColorUtilities.RGB_MASK
import com.skysoft.utils.ColorUtilities.withScaledAlpha
import com.skysoft.utils.TextUtilities.removeColor
import com.skysoft.utils.animation.PanelFadeTransition
import com.skysoft.utils.gui.OverlayPanelStyle
import com.skysoft.utils.gui.Rect
import com.skysoft.utils.gui.TextFieldState
import com.skysoft.utils.gui.elide
import com.skysoft.utils.renderables.primitives.ItemIconRenderable
import com.skysoft.utils.renderables.renderAt
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import net.minecraft.world.item.ItemStack
import org.lwjgl.glfw.GLFW

internal sealed interface ProfitTrackerControl {
    data object Period : ProfitTrackerControl
    data object PriceSource : ProfitTrackerControl
    data object Reset : ProfitTrackerControl
    data object CancelReset : ProfitTrackerControl
    data object ConfirmReset : ProfitTrackerControl
    data object More : ProfitTrackerControl
    data class ManageItem(
        val itemId: String,
        val stack: ItemStack,
        val formattedName: String,
    ) : ProfitTrackerControl
    data class ItemPriceSource(val itemId: String) : ProfitTrackerControl
    data class ExcludeItem(val itemId: String) : ProfitTrackerControl
    data class RestoreItem(val itemId: String) : ProfitTrackerControl
    data class RemoveCustomItem(val itemId: String) : ProfitTrackerControl
    data object AddItem : ProfitTrackerControl
    data object AddItemInventory : ProfitTrackerControl
    data object AddItemSearch : ProfitTrackerControl
    data class AddItemSearchField(val localMouseX: Int) : ProfitTrackerControl
    data class AddItemSearchResult(val itemId: String) : ProfitTrackerControl
    data object ResetCustomizations : ProfitTrackerControl
}

internal data class ProfitTrackerPanelControl(
    val action: ProfitTrackerControl,
    val bounds: Rect,
    val tooltipLines: List<String> = emptyList(),
)

internal class ProfitTrackerItemPanel(
    nanoTime: () -> Long = System::nanoTime,
) {
    private var content: Content? = null
    private val transition = PanelFadeTransition(nanoTime)
    private val addItemPanel = ProfitTrackerAddItemPanel()

    fun toggleOverview() {
        if (content != null && !transition.isClosing) close() else open(Content.Overview)
    }

    fun showOverview() = open(Content.Overview)

    fun openItem(itemId: String) = open(Content.Item(itemId))

    fun toggleItem(itemId: String) {
        if (content == Content.Item(itemId) && !transition.isClosing) close() else openItem(itemId)
    }

    fun beginAddingItem() {
        addItemPanel.reset()
        open(Content.AddItem)
    }

    fun isAddingFromInventory(): Boolean =
        content == Content.AddItem && addItemPanel.mode == AddItemMode.INVENTORY && !transition.isClosing

    fun selectAddItemMode(mode: AddItemMode) = addItemPanel.selectMode(mode)

    fun focusSearch(localMouseX: Int) = addItemPanel.focusSearch(localMouseX)

    fun wasKeyPressHandled(event: KeyEvent, select: (String) -> Unit): Boolean =
        content == Content.AddItem && addItemPanel.wasKeyPressHandled(event, select)

    fun wasCharTypedHandled(event: CharacterEvent): Boolean =
        content == Content.AddItem && addItemPanel.wasCharTypedHandled(event)

    fun wasSearchScrollHandled(verticalAmount: Double): Boolean =
        content == Content.AddItem && addItemPanel.wasScrollHandled(verticalAmount)

    fun close() {
        if (content == null) return
        transition.hide()
    }

    fun clear() {
        content = null
        addItemPanel.clear()
        transition.reset()
    }

    fun render(
        context: GuiGraphicsExtractor,
        target: ProfitTrackerTarget,
        trackerWidth: Int,
        placeRight: Boolean,
        mouseX: Int,
        mouseY: Int,
    ): ProfitTrackerPanelControl? {
        val current = content ?: return null
        val opacity = transition.opacity()
        if (!transition.isVisible) {
            clear()
            return null
        }
        if (current == Content.AddItem) {
            return addItemPanel.render(
                context,
                trackerWidth,
                placeRight,
                mouseX,
                mouseY,
                opacity,
                transition.isInteractive,
            )
        }
        val rows = rows(current, target)
        val width = maxOf(
            PANEL_MINIMUM_WIDTH,
            rows.maxOfOrNull { row -> Minecraft.getInstance().font.width(row.text) + row.iconOffset } ?: 0,
        ) + OverlayPanelStyle.PADDING * 2
        val height = rows.sumOf(PanelRow::height) + OverlayPanelStyle.PADDING * 2
        val x = if (placeRight) trackerWidth + PANEL_GAP else -width - PANEL_GAP
        context.fill(x, 0, x + width, height, OverlayPanelStyle.BACKGROUND.withScaledAlpha(opacity))
        context.outline(x, 0, width, height, OverlayPanelStyle.OUTLINE.withScaledAlpha(opacity))
        var hovered: ProfitTrackerPanelControl? = null
        var rowY = OverlayPanelStyle.PADDING
        rows.forEach { row ->
            val isHovered = transition.isInteractive && row.action != null &&
                mouseX in x until x + width && mouseY in rowY until rowY + row.height
            if (isHovered) context.fill(x, rowY, x + width, rowY + row.height, PANEL_HOVER.withScaledAlpha(opacity))
            if (opacity > ICON_VISIBILITY_THRESHOLD) {
                row.icon?.let { ItemIconRenderable(it, PANEL_ICON_SCALE).renderAt(context, x + OverlayPanelStyle.PADDING, rowY) }
            }
            context.text(
                Minecraft.getInstance().font,
                row.text,
                x + OverlayPanelStyle.PADDING + row.iconOffset,
                rowY + (row.height - PANEL_TEXT_HEIGHT) / 2,
                TEXT_BASE_COLOR.withScaledAlpha(opacity),
                false,
            )
            if (isHovered) {
                hovered = ProfitTrackerPanelControl(
                    row.action,
                    Rect(x, rowY, width, row.height),
                    row.tooltipLines,
                )
            }
            rowY += row.height
        }
        return hovered
    }

    private fun open(next: Content) {
        content = next
        transition.show()
    }

    private fun rows(content: Content, target: ProfitTrackerTarget): List<PanelRow> = when (content) {
        Content.Overview -> overviewRows(target)
        Content.AddItem -> emptyList()
        is Content.Item -> itemRows(target, content.itemId)
    }

    private fun overviewRows(target: ProfitTrackerTarget): List<PanelRow> {
        val customizations = ProfitTrackerItemCustomizations.data(target)
        return buildList {
            add(PanelRow(styledText("Item Settings", TITLE_COLOR, bold = true)))
            add(PanelRow(styledText("Add Item", ACTION_COLOR), ProfitTrackerControl.AddItem))
            customizations?.excludedItems?.takeIf { it.isNotEmpty() }?.let { excluded ->
                add(PanelRow(Component.empty(), heightOverride = PANEL_SECTION_GAP))
                add(PanelRow(styledText("Excluded", MUTED_COLOR)))
                excluded.forEach { itemId ->
                    val item = profitTrackerItemPresentation(itemId)
                    add(
                        PanelRow(
                            item.component,
                            ProfitTrackerControl.RestoreItem(itemId),
                            listOf("§7Restore"),
                            item.stack,
                        ),
                    )
                }
            }
            customizations?.customItems?.takeIf { it.isNotEmpty() }?.let { customItems ->
                add(PanelRow(styledText("Added Items", MUTED_COLOR)))
                customItems.forEach { itemId ->
                    val item = profitTrackerItemPresentation(itemId)
                    add(
                        PanelRow(
                            item.component,
                            ProfitTrackerControl.RemoveCustomItem(itemId),
                            listOf("§7Remove"),
                            item.stack,
                        ),
                    )
                }
            }
            add(
                PanelRow(
                    styledText("Reset Customizations", DANGER_COLOR),
                    ProfitTrackerControl.ResetCustomizations,
                ),
            )
        }
    }

    private fun itemRows(target: ProfitTrackerTarget, itemId: String): List<PanelRow> {
        val override = ProfitTrackerItemCustomizations.priceSourceOverride(target, itemId)
        val source = override?.toString() ?: "Tracker Default"
        val sources = listOf("Tracker Default") + ProfitTrackerPriceSource.entries.map { it.toString() }
        val presentation = profitTrackerItemPresentation(itemId)
        return listOf(
            PanelRow(presentation.component, icon = presentation.stack),
            PanelRow(
                styledText("Price Source ", MUTED_COLOR).append(styledText("[$source]", PRICE_COLOR, bold = true)),
                ProfitTrackerControl.ItemPriceSource(itemId),
                OverlayControlTooltips.cycle("Item Price Source", sources, (override?.ordinal ?: -1) + 1),
            ),
            if (target.custom == null) {
                PanelRow(styledText("Exclude", DANGER_COLOR), ProfitTrackerControl.ExcludeItem(itemId))
            } else {
                PanelRow(styledText("Remove", DANGER_COLOR), ProfitTrackerControl.RemoveCustomItem(itemId))
            },
        )
    }

    private sealed interface Content {
        data object Overview : Content
        data object AddItem : Content
        data class Item(val itemId: String) : Content
    }

    private data class PanelRow(
        val text: Component,
        val action: ProfitTrackerControl? = null,
        val tooltipLines: List<String> = emptyList(),
        val icon: ItemStack? = null,
        val heightOverride: Int? = null,
    ) {
        val height: Int = heightOverride ?: if (icon == null) PANEL_ROW_HEIGHT else PANEL_ICON_ROW_HEIGHT
        val iconOffset: Int = if (icon == null) 0 else PANEL_ICON_TEXT_OFFSET
    }
}

private class ProfitTrackerAddItemPanel {
    var mode = AddItemMode.INVENTORY
        private set
    private val searchField = TextFieldState(maxLength = SEARCH_MAXIMUM_LENGTH)
    private var selectedSearchIndex = 0
    private var searchOffset = 0
    private var searchResultsHovered = false
    private var searchCacheKey: SearchCacheKey? = null
    private var cachedSearchResults: List<ItemListEntry> = emptyList()
    private var searchFieldBounds: Rect? = null

    fun reset() {
        mode = AddItemMode.INVENTORY
        searchField.text = ""
        searchField.focused = false
        selectedSearchIndex = 0
        searchOffset = 0
        searchCacheKey = null
    }

    fun clear() {
        searchField.focused = false
        searchResultsHovered = false
        searchFieldBounds = null
    }

    fun selectMode(next: AddItemMode) {
        mode = next
        searchField.focused = next == AddItemMode.SEARCH
        if (searchField.focused) searchField.moveCursorToEnd()
    }

    fun focusSearch(localMouseX: Int) {
        val bounds = searchFieldBounds ?: return
        searchField.focused = true
        searchField.placeCursorAt(localMouseX, bounds.x, bounds.width)
    }

    fun wasKeyPressHandled(event: KeyEvent, select: (String) -> Unit): Boolean {
        if (mode != AddItemMode.SEARCH || !searchField.focused) return false
        val results = searchResults()
        when (event.key()) {
            GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> results.getOrNull(selectedSearchIndex)?.let {
                select(it.key.id)
            }
            GLFW.GLFW_KEY_TAB, GLFW.GLFW_KEY_DOWN, GLFW.GLFW_KEY_UP -> if (results.isNotEmpty()) {
                val step = if (event.key() == GLFW.GLFW_KEY_UP) -1 else 1
                selectedSearchIndex = Math.floorMod(selectedSearchIndex + step, results.size)
                searchOffset = when {
                    selectedSearchIndex < searchOffset -> selectedSearchIndex
                    selectedSearchIndex >= searchOffset + SEARCH_VISIBLE_RESULTS ->
                        selectedSearchIndex - SEARCH_VISIBLE_RESULTS + 1
                    else -> searchOffset
                }
            }
            GLFW.GLFW_KEY_ESCAPE -> searchField.focused = false
            else -> searchField.keyPressed(event)
        }
        return true
    }

    fun wasCharTypedHandled(event: CharacterEvent): Boolean {
        if (mode != AddItemMode.SEARCH || !searchField.focused || !event.isAllowedChatCharacter) return false
        searchField.charTyped(event)
        return true
    }

    fun wasScrollHandled(verticalAmount: Double): Boolean {
        if (mode != AddItemMode.SEARCH || !searchResultsHovered || verticalAmount == 0.0) return false
        val results = searchResults()
        val maximumOffset = (results.size - SEARCH_VISIBLE_RESULTS).coerceAtLeast(0)
        searchOffset = (searchOffset + if (verticalAmount < 0.0) 1 else -1).coerceIn(0, maximumOffset)
        selectedSearchIndex = selectedSearchIndex.coerceIn(
            searchOffset,
            (searchOffset + SEARCH_VISIBLE_RESULTS - 1).coerceAtMost((results.size - 1).coerceAtLeast(0)),
        )
        return true
    }

    fun render(
        context: GuiGraphicsExtractor,
        trackerWidth: Int,
        placeRight: Boolean,
        mouseX: Int,
        mouseY: Int,
        opacity: Double,
        interactive: Boolean,
    ): ProfitTrackerPanelControl? {
        val height = if (mode == AddItemMode.SEARCH) SEARCH_PANEL_HEIGHT else INVENTORY_PANEL_HEIGHT
        val panelX = if (placeRight) trackerWidth + PANEL_GAP else -ADD_ITEM_PANEL_WIDTH - PANEL_GAP
        val frame = AddItemRenderFrame(context, panelX, mouseX, mouseY, opacity, interactive)
        context.fill(
            panelX,
            0,
            panelX + ADD_ITEM_PANEL_WIDTH,
            height,
            OverlayPanelStyle.BACKGROUND.withScaledAlpha(opacity),
        )
        context.outline(panelX, 0, ADD_ITEM_PANEL_WIDTH, height, OverlayPanelStyle.OUTLINE.withScaledAlpha(opacity))
        var y = OverlayPanelStyle.PADDING
        context.text(
            frame.font,
            styledText("Add Item", TITLE_COLOR, bold = true),
            frame.contentX,
            y + (PANEL_ROW_HEIGHT - PANEL_TEXT_HEIGHT) / 2,
            TEXT_BASE_COLOR.withScaledAlpha(opacity),
            false,
        )
        y += PANEL_ROW_HEIGHT
        val hoveredMode = renderModeSelector(frame, y)
        y += PANEL_ROW_HEIGHT
        if (mode == AddItemMode.SEARCH) return renderSearch(frame, y, hoveredMode)
        searchFieldBounds = null
        searchResultsHovered = false
        context.text(
            frame.font,
            styledText("Click an inventory item.", MUTED_COLOR),
            frame.contentX,
            y + (PANEL_ROW_HEIGHT - PANEL_TEXT_HEIGHT) / 2,
            TEXT_BASE_COLOR.withScaledAlpha(opacity),
            false,
        )
        return hoveredMode
    }

    private fun renderModeSelector(frame: AddItemRenderFrame, y: Int): ProfitTrackerPanelControl? {
        val inventoryLabel = styledText(
            "[Inventory]",
            if (mode == AddItemMode.INVENTORY) TITLE_COLOR else ACTION_COLOR,
            bold = mode == AddItemMode.INVENTORY,
        )
        val searchLabel = styledText(
            "[Search]",
            if (mode == AddItemMode.SEARCH) TITLE_COLOR else ACTION_COLOR,
            bold = mode == AddItemMode.SEARCH,
        )
        val inventoryBounds = Rect(frame.contentX, y, frame.font.width(inventoryLabel), PANEL_ROW_HEIGHT)
        val searchBounds = Rect(
            inventoryBounds.x + inventoryBounds.width + MODE_GAP,
            y,
            frame.font.width(searchLabel),
            PANEL_ROW_HEIGHT,
        )
        val hovered = when {
            frame.interactive && inventoryBounds.contains(frame.mouseX, frame.mouseY) ->
                ProfitTrackerPanelControl(ProfitTrackerControl.AddItemInventory, inventoryBounds)
            frame.interactive && searchBounds.contains(frame.mouseX, frame.mouseY) ->
                ProfitTrackerPanelControl(ProfitTrackerControl.AddItemSearch, searchBounds)
            else -> null
        }
        hovered?.bounds?.let { bounds ->
            frame.context.fill(
                bounds.x,
                bounds.y,
                bounds.x + bounds.width,
                bounds.y + bounds.height,
                PANEL_HOVER.withScaledAlpha(frame.opacity),
            )
        }
        frame.context.text(
            frame.font,
            inventoryLabel,
            inventoryBounds.x,
            y + 1,
            TEXT_BASE_COLOR.withScaledAlpha(frame.opacity),
            false,
        )
        frame.context.text(
            frame.font,
            searchLabel,
            searchBounds.x,
            y + 1,
            TEXT_BASE_COLOR.withScaledAlpha(frame.opacity),
            false,
        )
        return hovered
    }

    private fun renderSearch(
        frame: AddItemRenderFrame,
        modeY: Int,
        hoveredMode: ProfitTrackerPanelControl?,
    ): ProfitTrackerPanelControl? {
        val fieldBounds = Rect(
            frame.contentX,
            modeY + SEARCH_FIELD_TOP_GAP,
            frame.contentWidth,
            SEARCH_FIELD_HEIGHT,
        )
        searchFieldBounds = fieldBounds
        searchField.render(
            frame.context,
            fieldBounds.x,
            fieldBounds.y,
            fieldBounds.width,
            fieldBounds.height,
            "Search items...",
            alpha = frame.opacity,
        )
        val fieldControl = ProfitTrackerPanelControl(
            ProfitTrackerControl.AddItemSearchField(frame.mouseX),
            fieldBounds,
        ).takeIf { frame.interactive && fieldBounds.contains(frame.mouseX, frame.mouseY) }
        val resultBounds = Rect(
            frame.contentX,
            fieldBounds.y + SEARCH_FIELD_HEIGHT + SEARCH_RESULTS_GAP,
            frame.contentWidth,
            SEARCH_VISIBLE_RESULTS * SEARCH_RESULT_HEIGHT,
        )
        searchResultsHovered = frame.interactive && resultBounds.contains(frame.mouseX, frame.mouseY)
        return renderResults(frame, resultBounds) ?: fieldControl ?: hoveredMode
    }

    private fun renderResults(frame: AddItemRenderFrame, resultBounds: Rect): ProfitTrackerPanelControl? {
        val results = searchResults()
        var hoveredResult: ProfitTrackerPanelControl? = null
        results.drop(searchOffset).take(SEARCH_VISIBLE_RESULTS).forEachIndexed { visibleIndex, entry ->
            val resultIndex = searchOffset + visibleIndex
            val rowY = resultBounds.y + visibleIndex * SEARCH_RESULT_HEIGHT
            val bounds = Rect(resultBounds.x, rowY, resultBounds.width, SEARCH_RESULT_HEIGHT)
            val hovered = frame.interactive && bounds.contains(frame.mouseX, frame.mouseY)
            if (hovered || resultIndex == selectedSearchIndex) {
                frame.context.fill(
                    bounds.x,
                    bounds.y,
                    bounds.x + bounds.width,
                    bounds.y + bounds.height,
                    PANEL_HOVER.withScaledAlpha(frame.opacity),
                )
            }
            val presentation = profitTrackerItemPresentation(entry.key.id)
            presentation.stack?.let {
                ItemIconRenderable(it, PANEL_ICON_SCALE).renderAt(frame.context, bounds.x, bounds.y)
            }
            frame.context.text(
                frame.font,
                frame.font.elide(
                    presentation.component,
                    bounds.width - PANEL_ICON_TEXT_OFFSET - SEARCH_SCROLLBAR_INSET,
                ),
                bounds.x + PANEL_ICON_TEXT_OFFSET,
                bounds.y + (bounds.height - PANEL_TEXT_HEIGHT) / 2,
                TEXT_BASE_COLOR.withScaledAlpha(frame.opacity),
                false,
            )
            if (hovered) {
                hoveredResult = ProfitTrackerPanelControl(
                    ProfitTrackerControl.AddItemSearchResult(entry.key.id),
                    bounds,
                )
            }
        }
        if (results.isEmpty()) renderEmptySearch(frame, resultBounds)
        renderScrollbar(frame, resultBounds, results.size)
        return hoveredResult
    }

    private fun renderEmptySearch(frame: AddItemRenderFrame, bounds: Rect) {
        val text = when {
            searchField.text.isBlank() -> "Type to search."
            SkyBlockDataRepository.status.state == SkyBlockDataLoadState.READY -> "No matches."
            SkyBlockDataRepository.status.state == SkyBlockDataLoadState.FAILED -> "Item search unavailable."
            else -> "Loading items..."
        }
        frame.context.text(
            frame.font,
            styledText(text, MUTED_COLOR),
            bounds.x,
            bounds.y + 1,
            TEXT_BASE_COLOR.withScaledAlpha(frame.opacity),
            false,
        )
    }

    private fun renderScrollbar(frame: AddItemRenderFrame, bounds: Rect, resultCount: Int) {
        val maximumOffset = (resultCount - SEARCH_VISIBLE_RESULTS).coerceAtLeast(0)
        if (maximumOffset == 0) return
        val scrollbarX = bounds.x + bounds.width - SEARCH_SCROLLBAR_WIDTH
        val knobHeight = (bounds.height * SEARCH_VISIBLE_RESULTS / resultCount)
            .coerceAtLeast(SEARCH_SCROLLBAR_MINIMUM_HEIGHT)
        val knobY = bounds.y + (bounds.height - knobHeight) * searchOffset / maximumOffset
        frame.context.fill(
            scrollbarX,
            bounds.y,
            scrollbarX + SEARCH_SCROLLBAR_WIDTH,
            bounds.y + bounds.height,
            SEARCH_SCROLLBAR_TRACK.withScaledAlpha(frame.opacity),
        )
        frame.context.fill(
            scrollbarX,
            knobY,
            scrollbarX + SEARCH_SCROLLBAR_WIDTH,
            knobY + knobHeight,
            SEARCH_SCROLLBAR_KNOB.withScaledAlpha(frame.opacity),
        )
    }

    private fun searchResults(): List<ItemListEntry> {
        val key = SearchCacheKey(searchField.text.trim(), SkyBlockDataRepository.snapshotVersion)
        if (searchCacheKey == key) return cachedSearchResults
        if (searchCacheKey?.query != key.query) {
            selectedSearchIndex = 0
            searchOffset = 0
        }
        cachedSearchResults = if (
            key.query.isBlank() || SkyBlockDataRepository.status.state != SkyBlockDataLoadState.READY
        ) {
            emptyList()
        } else {
            SkyBlockDataRepository.search(key.query).filter { it.key.kind == ItemListEntryKind.SKYBLOCK }
        }
        searchCacheKey = key
        selectedSearchIndex = selectedSearchIndex.coerceIn(0, (cachedSearchResults.size - 1).coerceAtLeast(0))
        searchOffset = searchOffset.coerceIn(0, (cachedSearchResults.size - SEARCH_VISIBLE_RESULTS).coerceAtLeast(0))
        return cachedSearchResults
    }
}

private data class AddItemRenderFrame(
    val context: GuiGraphicsExtractor,
    val panelX: Int,
    val mouseX: Int,
    val mouseY: Int,
    val opacity: Double,
    val interactive: Boolean,
) {
    val font get() = Minecraft.getInstance().font
    val contentX = panelX + OverlayPanelStyle.PADDING
    val contentWidth = ADD_ITEM_PANEL_WIDTH - OverlayPanelStyle.PADDING * 2
}

internal enum class AddItemMode {
    INVENTORY,
    SEARCH,
}

private data class SearchCacheKey(
    val query: String,
    val snapshotVersion: Long,
)

internal fun profitTrackerItemPresentation(itemId: String): ProfitTrackerItemPresentation {
    val key = SkyBlockDataRepository.itemKey(itemId)
    val entry = SkyBlockDataRepository.entry(key)
    val stack = SkyBlockDataRepository.displayStack(key) ?: PetRepository.itemStackOrNull(itemId)
    val formattedName = (entry?.formattedDisplayName ?: PetRepository.itemName(itemId) ?: itemId)
        .replace("Enchanted ", "Ench ")
    val name = formattedName.removeColor()
    val rarity = LEGACY_COLOR_PATTERN.find(formattedName)?.groupValues?.get(1)?.singleOrNull()
        ?.let(SkyBlockRarity::getByColorCode) ?: stack?.let(SkyBlockItemRarity::from)
    val color = rarity?.color?.rgb ?: ITEM_DEFAULT_COLOR
    return ProfitTrackerItemPresentation(name, formattedName, stack, styledText(name, color))
}

internal data class ProfitTrackerItemPresentation(
    val name: String,
    val formattedName: String,
    val stack: ItemStack?,
    val component: Component,
)

private fun styledText(text: String, color: Int, bold: Boolean = false): MutableComponent =
    Component.literal(text).withStyle { style -> style.withColor(color and RGB_MASK).withBold(bold) }

private val LEGACY_COLOR_PATTERN = Regex("§([0-9a-f])", RegexOption.IGNORE_CASE)

private const val PANEL_MINIMUM_WIDTH = 130
private const val ADD_ITEM_PANEL_WIDTH = 180
private const val PANEL_ROW_HEIGHT = 11
private const val PANEL_SECTION_GAP = 6
private const val PANEL_ICON_ROW_HEIGHT = 16
private const val PANEL_TEXT_HEIGHT = 9
private const val PANEL_ICON_TEXT_OFFSET = 16
private const val PANEL_ICON_SCALE = 0.75
private const val ICON_VISIBILITY_THRESHOLD = 0.35
private const val PANEL_GAP = 4
private const val MODE_GAP = 8
private const val SEARCH_MAXIMUM_LENGTH = 128
private const val SEARCH_FIELD_TOP_GAP = 2
private const val SEARCH_FIELD_HEIGHT = 18
private const val SEARCH_RESULTS_GAP = 3
private const val SEARCH_VISIBLE_RESULTS = 8
private const val SEARCH_RESULT_HEIGHT = 16
private const val INVENTORY_PANEL_HEIGHT = 41
private const val SEARCH_PANEL_HEIGHT = 177
private const val SEARCH_SCROLLBAR_INSET = 4
private const val SEARCH_SCROLLBAR_WIDTH = 2
private const val SEARCH_SCROLLBAR_MINIMUM_HEIGHT = 10
private const val TEXT_BASE_COLOR = 0xFFFFFFFF.toInt()
private const val TITLE_COLOR = 0xFFFFFF55.toInt()
private const val ITEM_DEFAULT_COLOR = 0xFFFFFFFF.toInt()
private const val MUTED_COLOR = 0xFFAAAAAA.toInt()
private const val ACTION_COLOR = 0xFF55FF55.toInt()
private const val PRICE_COLOR = 0xFFFFFF55.toInt()
private const val DANGER_COLOR = 0xFFFF5555.toInt()
private const val PANEL_HOVER = 0x28FFFFFF
private const val SEARCH_SCROLLBAR_TRACK = 0xFF303030.toInt()
private const val SEARCH_SCROLLBAR_KNOB = 0xFF909090.toInt()
