package com.skysoft.features.inventory

import com.skysoft.data.skyblock.ItemListEntry
import com.skysoft.data.skyblock.ItemListEntryKind
import com.skysoft.data.skyblock.SkyBlockDataLoadState
import com.skysoft.data.skyblock.SkyBlockDataRepository
import com.skysoft.data.skyblock.SkyBlockItemRarity
import com.skysoft.data.skyblock.SkyBlockRarity
import com.skysoft.data.skyblock.pets.PetRepository
import com.skysoft.utils.ColorUtilities.RGB_MASK
import com.skysoft.utils.ColorUtilities.withScaledAlpha
import com.skysoft.utils.TextUtilities.removeColor
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

internal sealed interface TrackedItemSelectionAction {
    data object Inventory : TrackedItemSelectionAction
    data object Search : TrackedItemSelectionAction
    data class SearchField(val localMouseX: Int) : TrackedItemSelectionAction
    data class SearchResult(val itemId: String) : TrackedItemSelectionAction
}

internal data class TrackedItemSelectionControl(
    val action: TrackedItemSelectionAction,
    val bounds: Rect,
)

internal class TrackedItemSelectionPanel {
    var mode = TrackedItemSelectionMode.INVENTORY
        private set
    var isHovered = false
        private set
    private val searchField = TextFieldState(maxLength = SEARCH_MAXIMUM_LENGTH)
    private var selectedSearchIndex = 0
    private var searchOffset = 0
    private var searchResultsHovered = false
    private var searchCacheKey: SearchCacheKey? = null
    private var cachedSearchResults: List<ItemListEntry> = emptyList()
    private var searchFieldBounds: Rect? = null

    fun reset() {
        mode = TrackedItemSelectionMode.INVENTORY
        searchField.text = ""
        searchField.focused = false
        selectedSearchIndex = 0
        searchOffset = 0
        searchCacheKey = null
        isHovered = false
    }

    fun clear() {
        searchField.focused = false
        searchResultsHovered = false
        searchFieldBounds = null
        isHovered = false
    }

    fun selectMode(next: TrackedItemSelectionMode) {
        mode = next
        searchField.focused = next == TrackedItemSelectionMode.SEARCH
        if (searchField.focused) searchField.moveCursorToEnd()
    }

    fun focusSearch(localMouseX: Int) {
        val bounds = searchFieldBounds ?: return
        searchField.focused = true
        searchField.placeCursorAt(localMouseX, bounds.x, bounds.width)
    }

    fun wasKeyPressHandled(
        event: KeyEvent,
        isSelectable: (String) -> Boolean,
        select: (String) -> Unit,
    ): Boolean {
        if (mode != TrackedItemSelectionMode.SEARCH || !searchField.focused) return false
        val results = searchResults(isSelectable)
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
        if (mode != TrackedItemSelectionMode.SEARCH || !searchField.focused || !event.isAllowedChatCharacter) return false
        searchField.charTyped(event)
        return true
    }

    fun wasScrollHandled(verticalAmount: Double, isSelectable: (String) -> Boolean): Boolean {
        if (mode != TrackedItemSelectionMode.SEARCH || !searchResultsHovered || verticalAmount == 0.0) return false
        val results = searchResults(isSelectable)
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
        title: String,
        trackerWidth: Int,
        placeRight: Boolean,
        mouseX: Int,
        mouseY: Int,
        opacity: Double,
        interactive: Boolean,
        isSelectable: (String) -> Boolean,
    ): TrackedItemSelectionControl? {
        val height = if (mode == TrackedItemSelectionMode.SEARCH) SEARCH_PANEL_HEIGHT else INVENTORY_PANEL_HEIGHT
        val panelX = if (placeRight) trackerWidth + PANEL_GAP else -PANEL_WIDTH - PANEL_GAP
        isHovered = Rect(panelX, 0, PANEL_WIDTH, height).contains(mouseX, mouseY)
        val frame = RenderFrame(context, panelX, mouseX, mouseY, opacity, interactive)
        context.fill(panelX, 0, panelX + PANEL_WIDTH, height, OverlayPanelStyle.BACKGROUND.withScaledAlpha(opacity))
        context.outline(panelX, 0, PANEL_WIDTH, height, OverlayPanelStyle.OUTLINE.withScaledAlpha(opacity))
        var y = OverlayPanelStyle.PADDING
        context.text(
            frame.font,
            styledText(title, TITLE_COLOR, bold = true),
            frame.contentX,
            y + (PANEL_ROW_HEIGHT - PANEL_TEXT_HEIGHT) / 2,
            TEXT_BASE_COLOR.withScaledAlpha(opacity),
            false,
        )
        y += PANEL_ROW_HEIGHT
        val hoveredMode = renderModeSelector(frame, y)
        y += PANEL_ROW_HEIGHT
        if (mode == TrackedItemSelectionMode.SEARCH) {
            return renderSearch(frame, isSelectable, y, hoveredMode)
        }
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

    private fun renderModeSelector(frame: RenderFrame, y: Int): TrackedItemSelectionControl? {
        val inventoryLabel = styledText(
            "[Inventory]",
            if (mode == TrackedItemSelectionMode.INVENTORY) TITLE_COLOR else ACTION_COLOR,
            bold = mode == TrackedItemSelectionMode.INVENTORY,
        )
        val searchLabel = styledText(
            "[Search]",
            if (mode == TrackedItemSelectionMode.SEARCH) TITLE_COLOR else ACTION_COLOR,
            bold = mode == TrackedItemSelectionMode.SEARCH,
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
                TrackedItemSelectionControl(TrackedItemSelectionAction.Inventory, inventoryBounds)
            frame.interactive && searchBounds.contains(frame.mouseX, frame.mouseY) ->
                TrackedItemSelectionControl(TrackedItemSelectionAction.Search, searchBounds)
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
        frame: RenderFrame,
        isSelectable: (String) -> Boolean,
        modeY: Int,
        hoveredMode: TrackedItemSelectionControl?,
    ): TrackedItemSelectionControl? {
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
        val fieldControl = TrackedItemSelectionControl(
            TrackedItemSelectionAction.SearchField(frame.mouseX),
            fieldBounds,
        ).takeIf { frame.interactive && fieldBounds.contains(frame.mouseX, frame.mouseY) }
        val resultBounds = Rect(
            frame.contentX,
            fieldBounds.y + SEARCH_FIELD_HEIGHT + SEARCH_RESULTS_GAP,
            frame.contentWidth,
            SEARCH_VISIBLE_RESULTS * SEARCH_RESULT_HEIGHT,
        )
        searchResultsHovered = frame.interactive && resultBounds.contains(frame.mouseX, frame.mouseY)
        return renderResults(frame, isSelectable, resultBounds) ?: fieldControl ?: hoveredMode
    }

    private fun renderResults(
        frame: RenderFrame,
        isSelectable: (String) -> Boolean,
        resultBounds: Rect,
    ): TrackedItemSelectionControl? {
        val results = searchResults(isSelectable)
        var hoveredResult: TrackedItemSelectionControl? = null
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
            val presentation = trackedItemPresentation(entry.key.id)
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
                hoveredResult = TrackedItemSelectionControl(
                    TrackedItemSelectionAction.SearchResult(entry.key.id),
                    bounds,
                )
            }
        }
        if (results.isEmpty()) renderEmptySearch(frame, resultBounds)
        renderScrollbar(frame, resultBounds, results.size)
        return hoveredResult
    }

    private fun renderEmptySearch(frame: RenderFrame, bounds: Rect) {
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

    private fun renderScrollbar(frame: RenderFrame, bounds: Rect, resultCount: Int) {
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

    private fun searchResults(isSelectable: (String) -> Boolean): List<ItemListEntry> {
        val key = SearchCacheKey(searchField.text.trim(), SkyBlockDataRepository.snapshotVersion)
        if (searchCacheKey != key) {
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
        }
        val results = cachedSearchResults.filter { entry -> isSelectable(entry.key.id) }
        selectedSearchIndex = selectedSearchIndex.coerceIn(0, (results.size - 1).coerceAtLeast(0))
        searchOffset = searchOffset.coerceIn(0, (results.size - SEARCH_VISIBLE_RESULTS).coerceAtLeast(0))
        return results
    }
}

internal enum class TrackedItemSelectionMode {
    INVENTORY,
    SEARCH,
}

internal data class TrackedItemPresentation(
    val name: String,
    val formattedName: String,
    val stack: ItemStack?,
    val component: Component,
)

internal fun trackedItemPresentation(itemId: String): TrackedItemPresentation {
    val key = SkyBlockDataRepository.itemKey(itemId)
    val entry = SkyBlockDataRepository.entry(key)
    val stack = SkyBlockDataRepository.displayStack(key) ?: PetRepository.itemStackOrNull(itemId)
    val formattedName = (entry?.formattedDisplayName ?: PetRepository.itemName(itemId) ?: itemId)
        .replace("Enchanted ", "Ench ")
    val name = formattedName.removeColor()
    val rarity = LEGACY_COLOR_PATTERN.find(formattedName)?.groupValues?.get(1)?.singleOrNull()
        ?.let(SkyBlockRarity::getByColorCode) ?: stack?.let(SkyBlockItemRarity::from)
    val color = rarity?.color?.rgb ?: ITEM_DEFAULT_COLOR
    return TrackedItemPresentation(name, formattedName, stack, styledText(name, color))
}

private data class RenderFrame(
    val context: GuiGraphicsExtractor,
    val panelX: Int,
    val mouseX: Int,
    val mouseY: Int,
    val opacity: Double,
    val interactive: Boolean,
) {
    val font get() = Minecraft.getInstance().font
    val contentX = panelX + OverlayPanelStyle.PADDING
    val contentWidth = PANEL_WIDTH - OverlayPanelStyle.PADDING * 2
}

private data class SearchCacheKey(
    val query: String,
    val snapshotVersion: Long,
)

private fun styledText(text: String, color: Int, bold: Boolean = false): MutableComponent =
    Component.literal(text).withStyle { style -> style.withColor(color and RGB_MASK).withBold(bold) }

private val LEGACY_COLOR_PATTERN = Regex("§([0-9a-f])", RegexOption.IGNORE_CASE)

private const val PANEL_WIDTH = 180
private const val PANEL_ROW_HEIGHT = 11
private const val PANEL_TEXT_HEIGHT = 9
private const val PANEL_ICON_TEXT_OFFSET = 16
private const val PANEL_ICON_SCALE = 0.75
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
private const val PANEL_HOVER = 0x28FFFFFF
private const val SEARCH_SCROLLBAR_TRACK = 0xFF303030.toInt()
private const val SEARCH_SCROLLBAR_KNOB = 0xFF909090.toInt()
