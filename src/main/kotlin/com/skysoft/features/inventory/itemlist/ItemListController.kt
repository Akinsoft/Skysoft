package com.skysoft.features.inventory.itemlist

import com.skysoft.config.ItemListSettingsConfig
import com.skysoft.config.ItemListSourcesConfig
import com.skysoft.config.SkysoftConfigGui
import com.skysoft.config.core.HudPosition
import com.skysoft.data.hypixel.HypixelLocationState
import com.skysoft.data.hypixel.SkyBlockCookieBuffApi
import com.skysoft.data.skyblock.ItemListEntry
import com.skysoft.data.skyblock.ItemListEntryKey
import com.skysoft.data.skyblock.ItemListEntryKind
import com.skysoft.data.skyblock.ItemListTierFamily
import com.skysoft.data.skyblock.ItemListTierFamilyKind
import com.skysoft.data.skyblock.SkyBlockDataLoadState
import com.skysoft.data.skyblock.SkyBlockDataRepository
import com.skysoft.features.inventory.ContainerSearchHighlighter
import com.skysoft.features.inventory.InventoryItemSearchHighlight
import com.skysoft.features.inventory.StorageOverlayController
import com.skysoft.gui.HudEditorElement
import com.skysoft.gui.HudEditorRegistry
import com.skysoft.utils.MinecraftClient
import com.skysoft.utils.SmoothFloatTransition
import com.skysoft.utils.SoundUtilities
import com.skysoft.utils.gui.PixelButtonRenderer
import com.skysoft.utils.gui.Rect
import com.skysoft.utils.gui.TextFieldState
import com.skysoft.utils.input.InputHandlingResult
import com.skysoft.utils.render.LegacyTextRenderer
import com.skysoft.utils.renderables.primitives.ItemIconRenderable
import com.skysoft.utils.renderables.renderAt
import com.skysoft.utils.ColorUtilities.withScaledAlpha
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import org.lwjgl.glfw.GLFW
import java.math.BigDecimal
import java.math.MathContext
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import kotlin.math.roundToInt

object ItemListController {
    private val searchField = TextFieldState(maxLength = 128)
    private var hoveredKey: ItemListEntryKey? = null
    private var lastLayout: ItemListLayout? = null
    private var lastEntries: List<ItemListEntry> = emptyList()
    private val tierDropdown = ItemListTierDropdownState()
    private var filterKey: ItemFilterKey? = null
    private var filteredEntryCache: List<ItemListEntry> = emptyList()
    private val editorPosition = HudPosition(
        -ItemListLayout.OUTER_MARGIN,
        ItemListLayout.OUTER_MARGIN,
        centerY = false,
    ).rememberDefault()
    private var editorIsResizing = false
    private var editorResizeStartColumns = ItemListSettingsConfig.DEFAULT_COLUMNS
    private var editorResizeMaximumColumns = ItemListSettingsConfig.MAX_COLUMNS
    private val footerAlpha = SmoothFloatTransition(
        FooterPresentation.IDLE_ALPHA.toFloat(),
        FooterPresentation.FADE_DURATION_NANOS,
    )
    private val navigationAlpha = SmoothFloatTransition(0f, FooterPresentation.FADE_DURATION_NANOS)
    private val calculationBlend = SmoothFloatTransition(0f, FooterPresentation.LABEL_SWAP_DURATION_NANOS)

    @JvmStatic
    fun register() {
        SkyBlockDataRepository.Demand.register("Item List", ::isItemListActive)
        SkyBlockCookieBuffApi.registerConsumer("Item List", ::isItemListViewerOpen)
        ItemListState.register()
        HudEditorRegistry.register(object : HudEditorElement {
            override val id: String = "item_list"
            override val label: String = "Item List"
            override val position: HudPosition = editorPosition
            override val canMove: Boolean = false
            override val canScale: Boolean = false
            override val hasEditorBackground: Boolean = false
            override val editorLeftPadding: Int
                get() = LegacyTextRenderer.width(EDITOR_RESIZE_ARROW) + EDITOR_RESIZE_ARROW_GAP
            override val usesInventoryScale: Boolean = true
            override val requiresInventoryScreen: Boolean = true
            override fun width(): Int = lastLayout?.panel?.width ?: 0
            override fun height(): Int = lastLayout?.panel?.height ?: 0
            override fun isVisible(): Boolean = lastLayout != null
            override fun renderDummy(context: GuiGraphicsExtractor) = Unit

            override fun renderEditorDummy(context: GuiGraphicsExtractor) {
                LegacyTextRenderer.draw(
                    context,
                    EDITOR_RESIZE_ARROW,
                    -editorLeftPadding,
                    (height() - EDITOR_RESIZE_ARROW_HEIGHT) / 2,
                )
            }

            override fun beginEditorDrag(localX: Int, localY: Int, width: Int, height: Int) {
                val layout = lastLayout ?: return
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

            override fun editorTooltipLines(): List<String> {
                val settings = SkysoftConfigGui.config().inventory.itemList.settings
                val visibleRows = lastLayout?.rows ?: settings.rows
                val rows = if (settings.rows == ItemListSettingsConfig.DEFAULT_ROWS) {
                    "$visibleRows (auto)"
                } else {
                    visibleRows.toString()
                }
                val width = lastLayout?.panel?.width?.div(ItemListLayout.DEFAULT_SLOT_SIZE) ?: settings.columns
                val itemScale = lastLayout?.itemScale ?: settings.itemScale
                return listOf(
                    "§7Width: §e$width§7, visible grid: §e${lastLayout?.columns ?: 0} x $rows",
                    "§7Item size: §e${"%.1f".format(Locale.US, itemScale)}x",
                    "§eDrag the centered ↔ arrow §7to resize width",
                    "§eScroll-Wheel §7to resize items",
                    "§eRight-click §7to open settings",
                    "§eR §7to reset",
                )
            }

            override fun openConfig() = SkysoftConfigGui.open("Item List")
        })
        HudEditorRegistry.register(object : HudEditorElement {
            override val id: String = "item_list_search"
            override val label: String = "Item List Search"
            override val position get() = SkysoftConfigGui.config().inventory.itemList.sources.searchPosition
            override val canScale: Boolean = false
            override val keepsInsideScreen: Boolean = true
            override val usesInventoryScale: Boolean = true
            override val requiresInventoryScreen: Boolean = true
            override fun width(): Int = lastLayout?.footer?.width ?: ItemListLayout.DEFAULT_FOOTER_WIDTH
            override fun height(): Int = ItemListLayout.FOOTER_HEIGHT
            override fun isVisible(): Boolean = lastLayout != null
            override fun renderDummy(context: GuiGraphicsExtractor) {
                val isSettingsButtonHidden = SkysoftConfigGui.config().inventory.itemList.sources.isSettingsButtonHidden
                val footerWidth = width()
                val searchWidth = if (isSettingsButtonHidden) {
                    footerWidth
                } else {
                    footerWidth - ItemListLayout.CONFIG_BUTTON_WIDTH - ItemListLayout.CONTROL_GAP
                }
                searchField.render(context, 0, 0, searchWidth, ItemListLayout.FOOTER_HEIGHT, "Search items and mobs...")
                if (!isSettingsButtonHidden) {
                    drawSettingsButton(
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

            override fun editorTooltipLines(): List<String> {
                val sources = SkysoftConfigGui.config().inventory.itemList.sources
                val isAttached = sources.searchPosition.isAtDefault()
                return listOf(
                    if (isAttached) "§7Attached to Item List sizing" else "§7Independent width: §e${width()}px",
                    if (isAttached) "§eDrag §7to detach and move" else "§eDrag §7to move",
                    if (isAttached) "§eScroll-Wheel §7to resize item slots" else "§eScroll-Wheel §7to resize width",
                    "§eRight-click §7to open settings",
                    if (isAttached) "§eR §7to reset" else "§eR §7to reconnect",
                )
            }

            override fun openConfig() = SkysoftConfigGui.open("Item List")
        })
    }

    @JvmStatic
    fun render(screen: AbstractContainerScreen<*>, context: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {
        if (!isVisible(screen)) {
            clearFrameState()
            return
        }
        val favorites = favoriteEntries()
        val layout = ItemListLayout.create(screen, favorites.isNotEmpty())
        if (layout == null) {
            clearFrameState()
            LegacyTextRenderer.draw(
                context,
                "§cItem List needs more room",
                screen.width - NO_ROOM_TEXT_WIDTH,
                screen.height - NO_ROOM_TEXT_BOTTOM,
            )
            return
        }
        lastLayout = layout
        val entries = filteredEntries()
        lastEntries = entries
        val pageCount = pageCount(entries.size, layout.pageSize)
        ItemListState.page = ItemListState.page.coerceIn(0, pageCount - 1)
        hoveredKey = null
        val calculation = itemListCalculation(ItemListState.search)
        val isSearchActive = searchField.focused || ItemListState.search.isNotBlank()
        val footerOpacity = footerAlpha.value(
            if (isSearchActive) 1f else FooterPresentation.IDLE_ALPHA.toFloat(),
        ).toDouble()
        val navigationOpacity = navigationAlpha.value(if (isSearchActive) 1f else 0f).toDouble()
        val labelAlphas = calculationLabelAlphas(calculationBlend.value(if (calculation == null) 0f else 1f))

        drawSlots(context, layout, entries, favorites, calculation != null, mouseX, mouseY)
        tierDropdown.render(context, layout, entries) { bounds, entry ->
            val scale = bounds.width.toFloat() / ItemListLayout.DEFAULT_SLOT_SIZE
            drawEntry(context, bounds, entry, null, scale, mouseX, mouseY)
        }
        if (navigationOpacity > FooterPresentation.MINIMUM_VISIBLE_ALPHA) {
            PixelButtonRenderer.draw(
                context,
                Minecraft.getInstance().font,
                layout.previous,
                "<",
                selected = false,
                hovered = layout.previous.contains(mouseX, mouseY),
                enabled = ItemListState.page > 0,
                alpha = navigationOpacity,
            )
            PixelButtonRenderer.draw(
                context,
                Minecraft.getInstance().font,
                layout.next,
                ">",
                selected = false,
                hovered = layout.next.contains(mouseX, mouseY),
                enabled = ItemListState.page + 1 < pageCount,
                alpha = navigationOpacity,
            )
            val paginationOpacity = navigationOpacity * if (layout.isSearchDetached) 1f else labelAlphas.first
            if (paginationOpacity > FooterPresentation.MINIMUM_VISIBLE_ALPHA) {
                drawCenteredText(
                    context,
                    layout.pageLabel,
                    "${ItemListState.page + 1} / $pageCount",
                    paginationOpacity,
                )
            }
            val calculationOpacity = navigationOpacity * labelAlphas.second
            if (calculation != null && calculationOpacity > FooterPresentation.MINIMUM_VISIBLE_ALPHA) {
                drawCenteredText(
                    context,
                    layout.calculationLabel,
                    "= $calculation",
                    calculationOpacity,
                    CALCULATION_TEXT_COLOR,
                )
            }
        }
        layout.config?.let { drawSettingsButton(context, it, it.contains(mouseX, mouseY), footerOpacity) }
        searchField.text = ItemListState.search
        searchField.render(
            context,
            layout.search.x,
            layout.search.y,
            layout.search.width,
            layout.search.height,
            "Search items and mobs...",
            alpha = footerOpacity,
            outlineColor = InventoryItemSearchHighlight.OUTLINE_COLOR.takeIf {
                ContainerSearchHighlighter.isActive(screen)
            },
        )
        if (layout.config?.contains(mouseX, mouseY) == true) {
            context.setTooltipForNextFrame(
                Minecraft.getInstance().font,
                net.minecraft.network.chat.Component.literal("Item List settings"),
                mouseX,
                mouseY,
            )
        }
    }

    @JvmStatic
    fun handleMouseClick(
        screen: AbstractContainerScreen<*>,
        click: MouseButtonEvent,
        doubled: Boolean,
    ): InputHandlingResult {
        if (!isVisible(screen)) return InputHandlingResult.IGNORED
        val layout = lastLayout ?: return InputHandlingResult.IGNORED
        val mouseX = click.x().toInt()
        val mouseY = click.y().toInt()
        if (!layout.containsInteractive(mouseX, mouseY)) {
            searchField.focused = false
            return InputHandlingResult.IGNORED
        }
        if (!layout.search.contains(mouseX, mouseY)) searchField.focused = false
        return processPanelClick(screen, click, doubled, layout, mouseX, mouseY)
    }

    private fun processPanelClick(
        screen: AbstractContainerScreen<*>,
        click: MouseButtonEvent,
        doubled: Boolean,
        layout: ItemListLayout,
        mouseX: Int,
        mouseY: Int,
    ): InputHandlingResult {
        when {
            tierDropdown.keyAt(mouseX, mouseY) != null -> {
                val key = requireNotNull(tierDropdown.keyAt(mouseX, mouseY))
                when (click.button()) {
                    GLFW.GLFW_MOUSE_BUTTON_LEFT, GLFW.GLFW_MOUSE_BUTTON_RIGHT -> openViewer(key, screen)
                }
            }
            layout.search.contains(mouseX, mouseY) -> {
                searchField.focused = true
                if (click.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                    searchField.placeCursorAt(mouseX, layout.search.x, layout.search.width)
                }
                when {
                    click.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT &&
                        SkysoftConfigGui.config().inventory.itemList.settings.isRightClickClearEnabled -> {
                        searchField.text = ""
                        updateSearch(screen, "")
                        ContainerSearchHighlighter.clear(screen)
                    }
                    click.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT && doubled &&
                        (ContainerSearchHighlighter.isActive(screen) || searchField.text.isNotBlank()) -> {
                        ContainerSearchHighlighter.toggle(screen, searchField.text)
                        SoundUtilities.playClickSound()
                    }
                }
            }
            layout.config?.contains(mouseX, mouseY) == true && click.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT -> {
                SoundUtilities.playClickSound()
                SkysoftConfigGui.open("Item List")
            }
            layout.previous.contains(mouseX, mouseY) && click.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT &&
                (searchField.focused || ItemListState.search.isNotBlank()) -> {
                changePage(-1, layout)
            }
            layout.next.contains(mouseX, mouseY) && click.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT &&
                (searchField.focused || ItemListState.search.isNotBlank()) -> {
                changePage(1, layout)
            }
            else -> openClickedEntry(screen, click, layout, mouseX, mouseY)
        }
        return InputHandlingResult.CONSUMED
    }

    private fun openClickedEntry(
        screen: AbstractContainerScreen<*>,
        click: MouseButtonEvent,
        layout: ItemListLayout,
        mouseX: Int,
        mouseY: Int,
    ) {
        val hit = entryAt(layout, mouseX, mouseY) ?: return
        val family = if (hit.isCatalogEntry) SkyBlockDataRepository.ItemListData.tierFamily(hit.key) else null
        if (family != null) {
            tierDropdown.toggle(hit.key)
            SoundUtilities.playClickSound()
            return
        }
        when (click.button()) {
            GLFW.GLFW_MOUSE_BUTTON_LEFT, GLFW.GLFW_MOUSE_BUTTON_RIGHT -> openViewer(hit.key, screen)
        }
    }

    @JvmStatic
    fun handleMouseScroll(
        screen: AbstractContainerScreen<*>,
        mouseX: Double,
        mouseY: Double,
        verticalAmount: Double,
    ): InputHandlingResult {
        if (!isVisible(screen)) return InputHandlingResult.IGNORED
        val layout = lastLayout ?: return InputHandlingResult.IGNORED
        if (!layout.panel.contains(mouseX.toInt(), mouseY.toInt()) || verticalAmount == 0.0) return InputHandlingResult.IGNORED
        changePage(if (verticalAmount > 0) -1 else 1, layout)
        return InputHandlingResult.CONSUMED
    }

    @JvmStatic
    fun handleKeyPress(screen: AbstractContainerScreen<*>, event: KeyEvent): InputHandlingResult {
        val config = SkysoftConfigGui.config().inventory.itemList
        val isItemListVisible = isVisible(screen)
        if (isItemListVisible && searchField.focused) {
            if (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER) {
                itemListCompiledCalculation(searchField.text)?.let { result ->
                    searchField.text = result
                    searchField.moveCursorToEnd()
                    Minecraft.getInstance().keyboardHandler.setClipboard(result)
                    updateSearch(screen, result)
                    return InputHandlingResult.CONSUMED
                }
            }
            return if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
                searchField.focused = false
                InputHandlingResult.CONSUMED
            } else {
                val before = searchField.text
                searchField.keyPressed(event)
                if (before != searchField.text) updateSearch(screen, searchField.text)
                InputHandlingResult.CONSUMED
            }
        }
        if (event.key() == config.settings.visibilityKey && config.enabled && HypixelLocationState.onHypixel) {
            ItemListState.isTemporarilyHidden = !ItemListState.isTemporarilyHidden
            searchField.focused = false
            return InputHandlingResult.CONSUMED
        }
        if (isItemListVisible && event.key() == GLFW.GLFW_KEY_TAB && config.settings.isTabSearchEnabled) {
            searchField.focused = true
            return InputHandlingResult.CONSUMED
        }
        val shortcut = resolveItemListShortcut(event.key(), config, screen, hoveredKey.takeIf { isItemListVisible })
        val layout = lastLayout
        return when {
            shortcut != null -> consume { openViewer(shortcut.key, screen, shortcut.mode) }
            !isItemListVisible || layout == null -> InputHandlingResult.IGNORED
            else -> when (event.key()) {
                GLFW.GLFW_KEY_LEFT -> consume { changePage(-1, layout) }
                GLFW.GLFW_KEY_RIGHT -> consume { changePage(1, layout) }
                GLFW.GLFW_KEY_A -> hoveredKey?.let { consume { ItemListState.toggleFavorite(it) } }
                    ?: InputHandlingResult.IGNORED
                else -> InputHandlingResult.IGNORED
            }
        }
    }

    @JvmStatic
    fun handleCharTyped(screen: AbstractContainerScreen<*>, event: CharacterEvent): InputHandlingResult {
        if (!isVisible(screen) || !searchField.focused || !event.isAllowedChatCharacter) return InputHandlingResult.IGNORED
        searchField.charTyped(event)
        updateSearch(screen, searchField.text)
        return InputHandlingResult.CONSUMED
    }

    @JvmStatic
    fun isClickInside(screen: AbstractContainerScreen<*>, mouseX: Double, mouseY: Double): Boolean =
        isVisible(screen) && lastLayout?.containsInteractive(mouseX.toInt(), mouseY.toInt()) == true

    @JvmStatic
    fun reservedBounds(screen: AbstractContainerScreen<*>): Rect? =
        if (isVisible(screen)) ItemListLayout.create(screen, favoriteEntries().isNotEmpty())?.panel else null

    internal fun searchFieldDebugSummary(): String =
        "focused=${searchField.focused} length=${searchField.text.length} cursor=${searchField.cursorIndex} " +
            "selected=${searchField.selectedCharacterCount}"

    private fun isVisible(screen: AbstractContainerScreen<*>): Boolean {
        val config = SkysoftConfigGui.config().inventory.itemList
        return config.enabled &&
            HypixelLocationState.onHypixel &&
            !ItemListState.isTemporarilyHidden &&
            !StorageOverlayController.isActive(screen)
    }

    private fun filteredEntries(): List<ItemListEntry> {
        val status = SkyBlockDataRepository.status
        if (status.state != SkyBlockDataLoadState.READY) return emptyList()
        val query = ItemListState.search.trim()
        if (query.isBlank()) return emptyList()
        val settings = SkysoftConfigGui.config().inventory.itemList.settings
        val currentKey = ItemFilterKey(
            query = query,
            showVanilla = settings.showVanilla,
            snapshotVersion = SkyBlockDataRepository.snapshotVersion,
        )
        if (filterKey == currentKey) return filteredEntryCache
        return SkyBlockDataRepository.ItemListData.search(query).filter { entry ->
            entry.key.kind == ItemListEntryKind.SKYBLOCK ||
                entry.key.kind == ItemListEntryKind.ENTITY ||
                (entry.source == "minecraft" && settings.showVanilla)
        }.also {
            filterKey = currentKey
            filteredEntryCache = it
        }
    }

    private fun drawSlots(
        context: GuiGraphicsExtractor,
        layout: ItemListLayout,
        entries: List<ItemListEntry>,
        favorites: List<ItemListEntry>,
        hasCalculation: Boolean,
        mouseX: Int,
        mouseY: Int,
    ) {
        favorites.take(layout.columns).forEachIndexed { index, entry ->
            val bounds = requireNotNull(layout.favoriteBounds(index))
            drawEntry(context, bounds, entry, null, layout.itemScale, mouseX, mouseY)
        }
        if (hasCalculation) return
        val start = ItemListState.page * layout.pageSize
        entries.drop(start).take(layout.pageSize).forEachIndexed { index, entry ->
            drawEntry(
                context,
                layout.slotBounds(index),
                entry,
                SkyBlockDataRepository.ItemListData.tierFamily(entry.key),
                layout.itemScale,
                mouseX,
                mouseY,
            )
        }
        if (
            entries.isEmpty() &&
            (SkyBlockDataRepository.status.state != SkyBlockDataLoadState.READY || ItemListState.search.isNotBlank())
        ) {
            val status = SkyBlockDataRepository.status
            val text = when (status.state) {
                SkyBlockDataLoadState.LOADING, SkyBlockDataLoadState.NOT_LOADED -> "§7Loading catalog..."
                SkyBlockDataLoadState.FAILED -> "§c${status.message ?: "Item List data failed"}"
                SkyBlockDataLoadState.READY -> "§7No matches"
            }
            LegacyTextRenderer.draw(
                context,
                text,
                layout.grid.x + EMPTY_TEXT_X_OFFSET,
                layout.grid.y + EMPTY_TEXT_Y_OFFSET,
            )
        }
    }

    private fun drawEntry(
        context: GuiGraphicsExtractor,
        bounds: Rect,
        entry: ItemListEntry,
        family: ItemListTierFamily?,
        itemScale: Float,
        mouseX: Int,
        mouseY: Int,
    ) {
        val hovered = bounds.contains(mouseX, mouseY)
        if (SkysoftConfigGui.config().inventory.itemList.sources.showItemBackgrounds) {
            context.fill(bounds.x, bounds.y, bounds.x + bounds.width, bounds.y + bounds.height, SLOT_BORDER)
            context.fill(
                bounds.x + 1,
                bounds.y + 1,
                bounds.x + bounds.width - 1,
                bounds.y + bounds.height - 1,
                if (hovered) SLOT_HOVER else SLOT_FILL,
            )
        }
        SkyBlockDataRepository.displayStack(entry.key)?.let { sourceStack ->
            val stack = if (family == null) {
                sourceStack
            } else {
                sourceStack.withActionHint(
                    "CLICK",
                    family.displayName,
                    ChatFormatting.BLUE.takeIf { family.kind == ItemListTierFamilyKind.ENCHANTMENT },
                )
            }
            val icon = ItemIconRenderable(stack, itemScale.toDouble())
            icon.renderAt(
                context,
                bounds.x + (bounds.width - icon.width) / 2f,
                bounds.y + (bounds.height - icon.height) / 2f,
            )
            if (ItemListState.isFavorite(entry.key)) {
                val heartSize = (FAVORITE_HEART_SIZE * itemScale).roundToInt()
                context.blitSprite(
                    net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED,
                    FAVORITE_HEART,
                    bounds.x,
                    bounds.y,
                    heartSize,
                    heartSize,
                )
            }
            if (hovered) {
                hoveredKey = entry.key
                context.setTooltipForNextFrame(Minecraft.getInstance().font, stack, mouseX, mouseY)
            }
        }
    }

    private fun entryAt(layout: ItemListLayout, mouseX: Int, mouseY: Int): EntryHit? {
        val favorites = favoriteEntries().take(layout.columns)
        favorites.forEachIndexed { index, entry ->
            if (layout.favoriteBounds(index)?.contains(mouseX, mouseY) == true) return EntryHit(entry.key, false)
        }
        if (!layout.grid.contains(mouseX, mouseY)) return null
        val column = (mouseX - layout.grid.x) / layout.slotSize
        val row = (mouseY - layout.grid.y) / layout.slotSize
        val index = ItemListState.page * layout.pageSize + row * layout.columns + column
        return lastEntries.getOrNull(index)?.key?.let { EntryHit(it, true) }
    }

    private fun changePage(delta: Int, layout: ItemListLayout) {
        val count = pageCount(lastEntries.size, layout.pageSize)
        val nextPage = (ItemListState.page + delta).coerceIn(0, count - 1)
        if (nextPage == ItemListState.page) return
        ItemListState.page = nextPage
        SoundUtilities.playNavigationSound(delta)
    }

    private fun openViewer(
        key: ItemListEntryKey,
        parent: AbstractContainerScreen<*>,
        mode: ItemListViewMode = ItemListViewMode.INFO,
    ) {
        SoundUtilities.playClickSound()
        MinecraftClient.setScreen(ItemListViewerScreen(parent, key, mode))
    }

    private fun updateSearch(screen: AbstractContainerScreen<*>, value: String) {
        ItemListState.search = value
        ItemListState.page = 0
        tierDropdown.clear()
        ContainerSearchHighlighter.update(screen, value)
    }

    private fun clearFrameState() {
        hoveredKey = null
        lastLayout = null
        lastEntries = emptyList()
    }

    private const val NO_ROOM_TEXT_WIDTH = 115
    private const val NO_ROOM_TEXT_BOTTOM = 14
    private const val EMPTY_TEXT_X_OFFSET = 4
    private const val EMPTY_TEXT_Y_OFFSET = 5
    private const val EDITOR_RESIZE_ARROW = "↔"
    private const val EDITOR_RESIZE_ARROW_GAP = 4
    private const val EDITOR_RESIZE_ARROW_HEIGHT = 9
    private const val EDITOR_RESIZE_ARROW_HIT_RADIUS = 8
    private val SLOT_BORDER = 0xFF111315.toInt()
    private val SLOT_FILL = 0xB0181B1E.toInt()
    private val SLOT_HOVER = 0xD03B5567.toInt()
    private val FAVORITE_HEART = net.minecraft.resources.Identifier.withDefaultNamespace("hud/heart/full")
    private const val FAVORITE_HEART_SIZE = 9
}

private fun isItemListActive(): Boolean =
    SkysoftConfigGui.config().inventory.itemList.enabled || isItemListViewerOpen()

private fun isItemListViewerOpen(): Boolean = MinecraftClient.screen() is ItemListViewerScreen

private object FooterPresentation {
    const val IDLE_ALPHA = 0.5
    const val MINIMUM_VISIBLE_ALPHA = 0.01
    const val FADE_DURATION_NANOS = 180_000_000L
    const val LABEL_SWAP_DURATION_NANOS = 600_000_000L
}

private data class EntryHit(
    val key: ItemListEntryKey,
    val isCatalogEntry: Boolean,
)

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

internal fun calculationLabelAlphas(calculationBlend: Float): Pair<Float, Float> =
    (1f - calculationBlend * 2f).coerceIn(0f, 1f) to (calculationBlend * 2f - 1f).coerceIn(0f, 1f)

internal fun itemListCalculation(query: String): String? =
    itemListCalculationResult(query)?.let { result ->
        DecimalFormat("#,##0.##########", DecimalFormatSymbols(Locale.US)).format(result)
    }

internal fun itemListCompiledCalculation(query: String): String? =
    itemListCalculationResult(query)?.let { result ->
        DecimalFormat("0.##########", DecimalFormatSymbols(Locale.US)).format(result)
    }

private fun itemListCalculationResult(query: String): BigDecimal? {
    val expression = query.replace(",", "")
    if (expression.none { it in "+-*/xX" } && !ITEM_LIST_SUFFIX_PATTERN.containsMatchIn(expression)) return null
    return runCatching { ItemListMathParser(expression).parse() }.getOrNull()
}

private class ItemListMathParser(private val expression: String) {
    private var index = 0

    fun parse(): BigDecimal {
        val result = parseExpression()
        skipSpaces()
        require(index == expression.length)
        return result
    }

    private fun parseExpression(): BigDecimal {
        var result = parseTerm()
        while (true) {
            result = when (nextOperator('+', '-')) {
                '+' -> result + parseTerm()
                '-' -> result - parseTerm()
                else -> return result
            }
        }
    }

    private fun parseTerm(): BigDecimal {
        var result = parseFactor()
        while (true) {
            result = when (nextOperator('*', 'x', 'X', '/')) {
                '*', 'x', 'X' -> result * parseFactor()
                '/' -> result.divide(parseFactor(), MathContext.DECIMAL64)
                else -> return result
            }
        }
    }

    private fun parseFactor(): BigDecimal {
        skipSpaces()
        return when (expression.getOrNull(index)) {
            '+' -> {
                index++
                parseFactor()
            }
            '-' -> {
                index++
                -parseFactor()
            }
            '(' -> {
                index++
                val result = parseExpression()
                skipSpaces()
                require(expression.getOrNull(index++) == ')')
                result
            }
            else -> parseNumber()
        }
    }

    private fun parseNumber(): BigDecimal {
        skipSpaces()
        val start = index
        while (expression.getOrNull(index)?.let { it.isDigit() || it == '.' } == true) index++
        require(index > start)
        val value = expression.substring(start, index).toBigDecimal()
        val multiplier = when (expression.getOrNull(index)?.lowercaseChar()) {
            's' -> STACK_MULTIPLIER
            'e' -> ENCHANTED_MULTIPLIER
            'k' -> THOUSAND_MULTIPLIER
            'm' -> MILLION_MULTIPLIER
            'b' -> BILLION_MULTIPLIER
            else -> return value
        }
        index++
        return value * BigDecimal.valueOf(multiplier)
    }

    private fun nextOperator(vararg operators: Char): Char? {
        skipSpaces()
        val operator = expression.getOrNull(index)?.takeIf { it in operators } ?: return null
        index++
        return operator
    }

    private fun skipSpaces() {
        while (expression.getOrNull(index)?.isWhitespace() == true) index++
    }
}

private val ITEM_LIST_SUFFIX_PATTERN = Regex("""\d[sekmb]""", RegexOption.IGNORE_CASE)
private const val STACK_MULTIPLIER = 64L
private const val ENCHANTED_MULTIPLIER = 160L
private const val THOUSAND_MULTIPLIER = 1_000L
private const val MILLION_MULTIPLIER = 1_000_000L
private const val BILLION_MULTIPLIER = 1_000_000_000L

private fun drawCenteredText(
    context: GuiGraphicsExtractor,
    bounds: Rect,
    text: String,
    alpha: Double = 1.0,
    color: Int = CENTERED_TEXT_COLOR,
) {
    val font = Minecraft.getInstance().font
    context.text(
        font,
        text,
        bounds.x + (bounds.width - font.width(text)) / 2,
        bounds.y + CENTERED_TEXT_Y_OFFSET,
        color.withScaledAlpha(alpha),
        false,
    )
}

private fun drawSettingsButton(
    context: GuiGraphicsExtractor,
    bounds: Rect,
    hovered: Boolean,
    alpha: Double = 1.0,
) {
    PixelButtonRenderer.draw(
        context,
        Minecraft.getInstance().font,
        bounds,
        "",
        selected = false,
        hovered = hovered,
        enabled = true,
        alpha = alpha,
    )
    context.item(
        ItemStack(Items.REDSTONE_TORCH),
        bounds.x + (bounds.width - SETTINGS_ICON_SIZE) / 2,
        bounds.y + (bounds.height - SETTINGS_ICON_SIZE) / 2,
    )
    if (alpha < 1.0) {
        context.fill(
            bounds.x,
            bounds.y,
            bounds.x + bounds.width,
            bounds.y + bounds.height,
            SETTINGS_ICON_DIM.withScaledAlpha(1.0 - alpha),
        )
    }
}

private fun favoriteEntries(): List<ItemListEntry> =
    ItemListState.favorites().mapNotNull(SkyBlockDataRepository::entry)

private fun pageCount(size: Int, pageSize: Int): Int = ((size + pageSize - 1) / pageSize).coerceAtLeast(1)

private fun consume(action: () -> Unit): InputHandlingResult {
    action()
    return InputHandlingResult.CONSUMED
}

private const val CENTERED_TEXT_Y_OFFSET = 4
private const val SETTINGS_ICON_SIZE = 16
private val SETTINGS_ICON_DIM = 0xB0000000.toInt()
private val CENTERED_TEXT_COLOR = 0xFFE0E4E8.toInt()
private val CALCULATION_TEXT_COLOR = 0xFF55FF55.toInt()

enum class ItemListViewMode {
    RECIPES,
    USAGES,
    INFO,
}

private data class ItemFilterKey(
    val query: String,
    val showVanilla: Boolean,
    val snapshotVersion: Long,
)
