package com.skysoft.features.inventory.itemlist

import com.skysoft.config.SkysoftConfigGui
import com.skysoft.data.hypixel.HypixelLocationState
import com.skysoft.data.hypixel.SkyBlockCookieBuffApi
import com.skysoft.data.skyblock.ItemListEntry
import com.skysoft.data.skyblock.ItemListEntryKey
import com.skysoft.data.skyblock.ItemListTierFamily
import com.skysoft.data.skyblock.ItemListTierFamilyKind
import com.skysoft.data.skyblock.SkyBlockDataLoadState
import com.skysoft.data.skyblock.SkyBlockDataRepository
import com.skysoft.features.inventory.ContainerSearchHighlighter
import com.skysoft.features.inventory.InventoryItemSearchHighlight
import com.skysoft.features.inventory.InventoryOverlayInput
import com.skysoft.features.inventory.StorageOverlayController
import com.skysoft.gui.HudEditorRegistry
import com.skysoft.utils.MinecraftClient
import com.skysoft.utils.SmoothFloatTransition
import com.skysoft.utils.SoundUtilities
import com.skysoft.utils.formatSkyBlockCalculation
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
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner
import net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.core.component.DataComponents
import org.joml.Vector2i
import org.lwjgl.glfw.GLFW
import kotlin.math.roundToInt

object ItemListController {
    private val config get() = SkysoftConfigGui.config().inventory.itemList
    private val searchField = TextFieldState(maxLength = 128)
    private var hoveredKey: ItemListEntryKey? = null
    private var lastLayout: ItemListLayout? = null
    private var lastEntries: List<ItemListEntry> = emptyList()
    private val tierDropdown = ItemListTierDropdownState()
    private val searchResults = ItemListSearchResults()
    private val footerAlpha = SmoothFloatTransition(
        FooterPresentation.IDLE_ALPHA.toFloat(),
        FooterPresentation.FADE_DURATION_NANOS,
    )
    private val navigationAlpha = SmoothFloatTransition(0f, FooterPresentation.FADE_DURATION_NANOS)
    private val calculationBlend = SmoothFloatTransition(0f, FooterPresentation.LABEL_SWAP_DURATION_NANOS)
    private val itemTooltipPositioner = ClientTooltipPositioner { screenWidth, screenHeight, x, y, width, height ->
        val position = DefaultTooltipPositioner.INSTANCE.positionTooltip(screenWidth, screenHeight, x, y, width, height)
        Vector2i(position.x(), position.y().coerceAtLeast(TOOLTIP_EDGE))
    }

    @JvmStatic
    fun register() {
        InventoryOverlayInput.registerCoverageProvider("Item List coverage", ::isItemListActive) {
                screen, mouseX, mouseY ->
            isClickInside(screen, mouseX, mouseY)
        }
        SkyBlockDataRepository.Demand.register("Item List", ::isItemListActive)
        SkyBlockCookieBuffApi.registerConsumer("Item List", ::isItemListViewerOpen)
        HudEditorRegistry.register(ItemListHudEditorElement { lastLayout })
        HudEditorRegistry.register(ItemListSearchHudEditorElement({ lastLayout }, searchField))
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
        val entries = searchResults.entries(ItemListState.search, config.settings.showVanilla)
        val pageCount = pageCount(entries.size, layout.pageSize)
        ItemListState.page = ItemListState.page.coerceIn(0, pageCount - 1)
        hoveredKey = null
        val calculation = itemListCalculation(ItemListState.search)
        lastEntries = if (calculation == null) entries else emptyList()
        val isSearchActive = searchField.focused || ItemListState.search.isNotBlank()
        val footerOpacity = footerAlpha.value(
            if (isSearchActive) 1f else FooterPresentation.IDLE_ALPHA.toFloat(),
        ).toDouble()
        val navigationOpacity = navigationAlpha.value(if (isSearchActive) 1f else 0f).toDouble()
        val labelAlphas = calculationLabelAlphas(calculationBlend.value(if (calculation == null) 0f else 1f))

        drawSlots(context, layout, entries, favorites, calculation != null, mouseX, mouseY)
        tierDropdown.render(context, layout, entries) { bounds, entry ->
            val scale = bounds.width.toFloat() / ItemListLayout.DEFAULT_SLOT_SIZE
            drawEntry(context, bounds, entry, null, scale, mouseX, mouseY, true)
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
        layout.config?.let { drawItemListSettingsButton(context, it, it.contains(mouseX, mouseY), footerOpacity) }
        searchField.text = ItemListState.search
        searchField.render(
            context,
            layout.search.x,
            layout.search.y,
            layout.search.width,
            layout.search.height,
            "Search items and mobs...",
            alpha = footerOpacity,
            outlineColor = InventoryItemSearchHighlight.outlineColor.takeIf {
                ContainerSearchHighlighter.isActive()
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
        val tierKey = tierDropdown.keyAt(mouseX, mouseY)
        if (tierDropdown.isOpen) {
            tierDropdown.clear()
            searchField.focused = false
            if (tierKey != null) {
                when (click.button()) {
                    GLFW.GLFW_MOUSE_BUTTON_LEFT, GLFW.GLFW_MOUSE_BUTTON_RIGHT -> openViewer(tierKey, screen)
                }
            }
            return InputHandlingResult.CONSUMED
        }
        if (
            !layout.containsInteractive(
                mouseX,
                mouseY,
                searchField.focused || ItemListState.search.isNotBlank(),
                entryAt(layout, mouseX, mouseY) != null,
            )
        ) {
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
            layout.search.contains(mouseX, mouseY) -> {
                searchField.focused = true
                if (click.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                    searchField.placeCursorAt(mouseX, layout.search.x, layout.search.width)
                }
                when {
                    click.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT &&
                        config.settings.isRightClickClearEnabled -> {
                        searchField.text = ""
                        updateSearch("")
                        ContainerSearchHighlighter.clear()
                    }
                    click.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT && doubled -> {
                        ContainerSearchHighlighter.toggle(searchField.text)
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
        val isItemListVisible = isVisible(screen)
        if (isItemListVisible && searchField.focused) {
            if (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER) {
                itemListCompiledCalculation(searchField.text)?.let { result ->
                    searchField.text = result
                    searchField.moveCursorToEnd()
                    Minecraft.getInstance().keyboardHandler.setClipboard(result)
                    updateSearch(result)
                    return InputHandlingResult.CONSUMED
                }
            }
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
        if (config.settings.visibilityKey != GLFW.GLFW_KEY_UNKNOWN &&
            event.key() == config.settings.visibilityKey &&
            config.enabled && HypixelLocationState.onHypixel
        ) {
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
        updateSearch(searchField.text)
        return InputHandlingResult.CONSUMED
    }

    @JvmStatic
    fun isClickInside(screen: AbstractContainerScreen<*>, mouseX: Double, mouseY: Double): Boolean =
        isVisible(screen) && lastLayout?.let {
            val pointX = mouseX.toInt()
            val pointY = mouseY.toInt()
            it.containsInteractive(
                pointX,
                pointY,
                searchField.focused || ItemListState.search.isNotBlank(),
                entryAt(it, pointX, pointY) != null,
            )
        } == true

    @JvmStatic
    fun reservedBounds(screen: AbstractContainerScreen<*>): Rect? =
        if (isVisible(screen)) ItemListLayout.create(screen, favoriteEntries().isNotEmpty())?.panel else null

    private fun isVisible(screen: AbstractContainerScreen<*>): Boolean {
        return config.enabled &&
            HypixelLocationState.onHypixel &&
            !ItemListState.isTemporarilyHidden &&
            !StorageOverlayController.isActive(screen)
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
        val canHover = !tierDropdown.isOpen
        favorites.take(layout.columns).forEachIndexed { index, entry ->
            val bounds = requireNotNull(layout.favoriteBounds(index))
            drawEntry(context, bounds, entry, null, layout.itemScale, mouseX, mouseY, canHover)
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
                canHover,
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
        canHover: Boolean,
    ) {
        val hovered = canHover && bounds.contains(mouseX, mouseY)
        if (config.sources.showItemBackgrounds) {
            context.fill(bounds.x, bounds.y, bounds.x + bounds.width, bounds.y + bounds.height, ItemListSlotStyle.BORDER)
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
                val minecraft = Minecraft.getInstance()
                context.setTooltipForNextFrame(
                    minecraft.font,
                    Screen.getTooltipFromItem(minecraft, stack).map { it.visualOrderText },
                    stack.tooltipImage,
                    itemTooltipPositioner,
                    mouseX,
                    mouseY,
                    false,
                    stack.get(DataComponents.TOOLTIP_STYLE),
                )
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
        tierDropdown.clear()
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

    private fun updateSearch(value: String) {
        ItemListState.search = value
        ItemListState.page = 0
        tierDropdown.clear()
        ContainerSearchHighlighter.update(value)
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
    private const val TOOLTIP_EDGE = 4
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

internal fun calculationLabelAlphas(calculationBlend: Float): Pair<Float, Float> =
    (1f - calculationBlend * 2f).coerceIn(0f, 1f) to (calculationBlend * 2f - 1f).coerceIn(0f, 1f)

internal fun itemListCalculation(query: String): String? = formatSkyBlockCalculation(query, grouped = true)

internal fun itemListCompiledCalculation(query: String): String? =
    formatSkyBlockCalculation(query, grouped = false)

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

private fun favoriteEntries(): List<ItemListEntry> =
    ItemListState.favorites().mapNotNull(SkyBlockDataRepository::entry)

private fun pageCount(size: Int, pageSize: Int): Int = Math.ceilDiv(size, pageSize).coerceAtLeast(1)

private fun consume(action: () -> Unit): InputHandlingResult {
    action()
    return InputHandlingResult.CONSUMED
}

private const val CENTERED_TEXT_Y_OFFSET = 4
private val CENTERED_TEXT_COLOR = 0xFFE0E4E8.toInt()
private val CALCULATION_TEXT_COLOR = 0xFF55FF55.toInt()

enum class ItemListViewMode {
    RECIPES,
    USAGES,
    INFO,
}
