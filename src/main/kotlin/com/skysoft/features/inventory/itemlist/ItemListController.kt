package com.skysoft.features.inventory.itemlist

import com.skysoft.config.SkysoftConfigGui
import com.skysoft.data.hypixel.HypixelLocationState
import com.skysoft.data.skyblock.ItemListEntry
import com.skysoft.data.skyblock.ItemListEntryKey
import com.skysoft.data.skyblock.ItemListEntryKind
import com.skysoft.data.skyblock.ItemListTierFamily
import com.skysoft.data.skyblock.ItemListTierFamilyKind
import com.skysoft.data.skyblock.SkyBlockDataLoadState
import com.skysoft.data.skyblock.SkyBlockDataRepository
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

object ItemListController {
    private val searchField = TextFieldState(maxLength = 128)
    private var hoveredKey: ItemListEntryKey? = null
    private var lastLayout: ItemListLayout? = null
    private var lastEntries: List<ItemListEntry> = emptyList()
    private val tierDropdown = ItemListTierDropdownState()
    private var filterKey: ItemFilterKey? = null
    private var filteredEntryCache: List<ItemListEntry> = emptyList()
    private val footerAlpha = SmoothFloatTransition(
        FooterPresentation.IDLE_ALPHA.toFloat(),
        FooterPresentation.FADE_DURATION_NANOS,
    )
    private val navigationAlpha = SmoothFloatTransition(0f, FooterPresentation.FADE_DURATION_NANOS)
    private val calculationBlend = SmoothFloatTransition(0f, FooterPresentation.LABEL_SWAP_DURATION_NANOS)

    @JvmStatic
    fun register() {
        ItemListState.register()
        HudEditorRegistry.register(object : HudEditorElement {
            override val id: String = "item_list_search"
            override val label: String = "Item List Search"
            override val position get() = SkysoftConfigGui.config().inventory.itemList.sources.searchPosition
            override val canScale: Boolean = false
            override val usesInventoryScale: Boolean = true
            override val requiresInventoryScreen: Boolean = true
            override fun width(): Int = FooterPresentation.EDITOR_WIDTH
            override fun height(): Int = FooterPresentation.HEIGHT
            override fun isVisible(): Boolean = SkysoftConfigGui.config().inventory.itemList.enabled
            override fun renderDummy(context: GuiGraphicsExtractor) {
                val isSettingsButtonHidden = SkysoftConfigGui.config().inventory.itemList.sources.isSettingsButtonHidden
                val searchWidth = if (isSettingsButtonHidden) {
                    FooterPresentation.EDITOR_WIDTH
                } else {
                    FooterPresentation.SEARCH_WIDTH
                }
                searchField.render(context, 0, 0, searchWidth, FooterPresentation.HEIGHT, "Search items...")
                if (!isSettingsButtonHidden) {
                    drawSettingsButton(
                        context,
                        Rect(
                            FooterPresentation.SEARCH_WIDTH + FooterPresentation.GAP,
                            0,
                            FooterPresentation.BUTTON_WIDTH,
                            FooterPresentation.HEIGHT,
                        ),
                        hovered = false,
                    )
                }
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
            drawEntry(context, bounds, entry, null, mouseX, mouseY)
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
            val paginationOpacity = navigationOpacity * labelAlphas.first
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
                    layout.pageLabel,
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
            "Search items...",
            alpha = footerOpacity,
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
    fun handleMouseClick(screen: AbstractContainerScreen<*>, click: MouseButtonEvent): InputHandlingResult {
        if (!isVisible(screen)) return InputHandlingResult.IGNORED
        val layout = lastLayout ?: return InputHandlingResult.IGNORED
        val mouseX = click.x().toInt()
        val mouseY = click.y().toInt()
        if (!layout.containsInteractive(mouseX, mouseY)) {
            searchField.focused = false
            return InputHandlingResult.IGNORED
        }
        if (!layout.search.contains(mouseX, mouseY)) searchField.focused = false
        return processPanelClick(screen, click, layout, mouseX, mouseY)
    }

    private fun processPanelClick(
        screen: AbstractContainerScreen<*>,
        click: MouseButtonEvent,
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
            return if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
                searchField.focused = false
                InputHandlingResult.CONSUMED
            } else {
                val before = searchField.text
                searchField.keyPressed(event)
                if (before != searchField.text) updateSearch(searchField.text)
                InputHandlingResult.CONSUMED
            }
        }
        if (event.key() == config.settings.visibilityKey && config.enabled && HypixelLocationState.onHypixel) {
            ItemListState.isTemporarilyHidden = !ItemListState.isTemporarilyHidden
            searchField.focused = false
            return InputHandlingResult.CONSUMED
        }
        if (!isItemListVisible) return InputHandlingResult.IGNORED
        val layout = lastLayout
        return if (layout == null) InputHandlingResult.IGNORED else when (event.key()) {
            GLFW.GLFW_KEY_LEFT -> consume { changePage(-1, layout) }
            GLFW.GLFW_KEY_RIGHT -> consume { changePage(1, layout) }
            GLFW.GLFW_KEY_R -> hoveredKey?.let { consume { openViewer(it, screen) } }
                ?: InputHandlingResult.IGNORED
            GLFW.GLFW_KEY_U -> hoveredKey?.let { consume { openViewer(it, screen) } }
                ?: InputHandlingResult.IGNORED
            GLFW.GLFW_KEY_A -> hoveredKey?.let { consume { ItemListState.toggleFavorite(it) } }
                ?: InputHandlingResult.IGNORED
            else -> InputHandlingResult.IGNORED
        }
    }

    @JvmStatic
    fun handleCharTyped(screen: AbstractContainerScreen<*>, event: CharacterEvent): InputHandlingResult {
        if (!isVisible(screen) || !searchField.focused || !event.isAllowedChatCharacter) return InputHandlingResult.IGNORED
        searchField.charTyped(event)
        updateSearch(searchField.text)
        return InputHandlingResult.CONSUMED
    }

    @JvmStatic
    fun isClickInside(screen: AbstractContainerScreen<*>, mouseX: Double, mouseY: Double): Boolean =
        isVisible(screen) && lastLayout?.containsInteractive(mouseX.toInt(), mouseY.toInt()) == true

    @JvmStatic
    fun reservedBounds(screen: AbstractContainerScreen<*>): Rect? =
        if (isVisible(screen)) ItemListLayout.create(screen, favoriteEntries().isNotEmpty())?.panel else null

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
        if (!hasItemListQuery(query)) return emptyList()
        val sources = SkysoftConfigGui.config().inventory.itemList.sources
        val currentKey = ItemFilterKey(
            query = query,
            showVanilla = sources.showVanilla,
            snapshotVersion = SkyBlockDataRepository.snapshotVersion,
        )
        if (filterKey == currentKey) return filteredEntryCache
        return SkyBlockDataRepository.ItemListData.search(query).filter { entry ->
            entry.key.kind == ItemListEntryKind.SKYBLOCK ||
                (entry.source == "minecraft" && sources.showVanilla)
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
            drawEntry(context, bounds, entry, null, mouseX, mouseY)
        }
        if (hasCalculation) return
        val start = ItemListState.page * layout.pageSize
        entries.drop(start).take(layout.pageSize).forEachIndexed { index, entry ->
            drawEntry(
                context,
                layout.slotBounds(index),
                entry,
                SkyBlockDataRepository.ItemListData.tierFamily(entry.key),
                mouseX,
                mouseY,
            )
        }
        if (
            entries.isEmpty() &&
            (SkyBlockDataRepository.status.state != SkyBlockDataLoadState.READY || hasItemListQuery(ItemListState.search))
        ) {
            val status = SkyBlockDataRepository.status
            val text = when (status.state) {
                SkyBlockDataLoadState.LOADING, SkyBlockDataLoadState.NOT_LOADED -> "§7Loading items..."
                SkyBlockDataLoadState.FAILED -> "§c${status.message ?: "Item data failed"}"
                SkyBlockDataLoadState.READY -> "§7No matching items"
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
            context.item(stack, bounds.x + 1, bounds.y + 1)
            if (ItemListState.isFavorite(entry.key)) {
                context.blitSprite(
                    net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED,
                    FAVORITE_HEART,
                    bounds.x,
                    bounds.y,
                    FAVORITE_HEART_SIZE,
                    FAVORITE_HEART_SIZE,
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
        val column = (mouseX - layout.grid.x) / ItemListLayout.SLOT_SIZE
        val row = (mouseY - layout.grid.y) / ItemListLayout.SLOT_SIZE
        val index = ItemListState.page * layout.pageSize + row * layout.columns + column
        return lastEntries.getOrNull(index)?.key?.let { EntryHit(it, true) }
    }

    private fun changePage(delta: Int, layout: ItemListLayout) {
        val count = pageCount(lastEntries.size, layout.pageSize)
        ItemListState.page = (ItemListState.page + delta).coerceIn(0, count - 1)
        SoundUtilities.playClickSound()
    }

    private fun openViewer(key: ItemListEntryKey, parent: AbstractContainerScreen<*>) {
        ItemListState.recordRecent(key)
        SoundUtilities.playClickSound()
        MinecraftClient.setScreen(ItemListViewerScreen(parent, key))
    }

    private fun updateSearch(value: String) {
        ItemListState.search = value
        ItemListState.page = 0
        tierDropdown.clear()
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
    private val SLOT_BORDER = 0xFF111315.toInt()
    private val SLOT_FILL = 0xB0181B1E.toInt()
    private val SLOT_HOVER = 0xD03B5567.toInt()
    private val FAVORITE_HEART = net.minecraft.resources.Identifier.withDefaultNamespace("hud/heart/full")
    private const val FAVORITE_HEART_SIZE = 9
}

private object FooterPresentation {
    const val EDITOR_WIDTH = 162
    const val HEIGHT = 20
    const val BUTTON_WIDTH = 24
    const val GAP = 3
    const val SEARCH_WIDTH = EDITOR_WIDTH - BUTTON_WIDTH - GAP
    const val IDLE_ALPHA = 0.5
    const val MINIMUM_VISIBLE_ALPHA = 0.01
    const val FADE_DURATION_NANOS = 180_000_000L
    const val LABEL_SWAP_DURATION_NANOS = 600_000_000L
}

private data class EntryHit(
    val key: ItemListEntryKey,
    val isCatalogEntry: Boolean,
)

internal fun hasItemListQuery(query: String): Boolean = query.isNotBlank()

internal fun calculationLabelAlphas(calculationBlend: Float): Pair<Float, Float> =
    (1f - calculationBlend * 2f).coerceIn(0f, 1f) to (calculationBlend * 2f - 1f).coerceIn(0f, 1f)

internal fun itemListCalculation(query: String): String? {
    val expression = query.replace(",", "")
    if (expression.none { it in "+-*/xX" }) return null
    val result = runCatching { ItemListMathParser(expression).parse() }.getOrNull() ?: return null
    return DecimalFormat("#,##0.##########", DecimalFormatSymbols(Locale.US)).format(result)
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
        return expression.substring(start, index).toBigDecimal()
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
