package com.skysoft.features.inventory.itemlist

import com.skysoft.config.SkysoftConfigGui
import com.skysoft.data.skyblock.ItemListEntryKey
import com.skysoft.data.skyblock.ItemListEntryKind
import com.skysoft.data.skyblock.ItemListTierFamily
import com.skysoft.data.skyblock.SkyBlockDataRepository
import com.skysoft.data.skyblock.price.SkyBlockPriceData
import com.skysoft.gui.tooltip.SkysoftNativeTooltip
import com.skysoft.gui.tooltip.TooltipScrollPriorityScreen
import com.skysoft.utils.BrowserUtilities
import com.skysoft.utils.MinecraftClient
import com.skysoft.utils.gui.OverlayPanelStyle
import com.skysoft.utils.gui.PixelButtonRenderer
import com.skysoft.utils.gui.Rect
import com.skysoft.utils.render.LegacyTextRenderer
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import org.lwjgl.glfw.GLFW

internal class ItemListViewerScreen(
    private val parent: Screen?,
    initialKey: ItemListEntryKey,
    initialMode: ItemListViewMode = ItemListViewMode.INFO,
) : Screen(Component.literal("Skysoft Item List")), TooltipScrollPriorityScreen {
    private val selection = ItemListViewerSelection(initialKey, initialMode)
    private val currentKey by selection::currentKey
    private val mode by selection::mode
    private val infoPanel = ItemListInfoPanel()
    private val recipeView = ItemListRecipeView(selection) { font }
    private var layout: ItemListViewerLayout? = null

    override fun init() {
        super.init()
        SkyBlockPriceData.setItemListMarketInterest(true)
        SkyBlockPriceData.refreshItemListMarketNow()
    }

    override fun removed() {
        SkyBlockPriceData.setItemListMarketInterest(false)
        super.removed()
    }

    override fun extractRenderState(context: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        selection.ensureAvailableMode()
        recipeView.prepareFrame()
        context.fill(0, 0, width, height, SCREEN_OVERLAY)
        val currentLayout = ItemListViewerLayout.create(width, height)
        layout = currentLayout
        OverlayPanelStyle.draw(
            context,
            currentLayout.panel.x,
            currentLayout.panel.y,
            currentLayout.panel.width,
            currentLayout.panel.height,
        )
        renderHeader(context, currentLayout, mouseX, mouseY)
        renderTabs(context, currentLayout, mouseX, mouseY)
        when (mode) {
            ItemListViewMode.INFO -> {
                infoPanel.render(context, font, currentLayout.content, currentKey, mouseX, mouseY)
                renderInfoLinks(context, font, currentLayout, currentKey, mouseX, mouseY)
            }
            ItemListViewMode.RECIPES, ItemListViewMode.USAGES -> recipeView.render(context, currentLayout, mouseX, mouseY)
        }
        renderFooter(context, currentLayout, mouseX, mouseY)
    }

    override fun extractBackground(context: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) = Unit

    override fun mouseClicked(click: MouseButtonEvent, doubled: Boolean): Boolean {
        val currentLayout = layout ?: return super.mouseClicked(click, doubled)
        val mouseX = click.x().toInt()
        val mouseY = click.y().toInt()
        val result = when (click.button()) {
            GLFW.GLFW_MOUSE_BUTTON_LEFT -> applyLeftClick(currentLayout, mouseX, mouseY)
            GLFW.GLFW_MOUSE_BUTTON_RIGHT -> {
                val warp = requestNpcWarpAt(recipeView.entityAt(mouseX, mouseY))
                if (warp.shouldCloseScreen) MinecraftClient.setScreen(null)
                warp.inputResult.orElse { recipeView.navigateIngredient(mouseX, mouseY) }
            }
            else -> ViewerInputResult.IGNORED
        }
        if (!result.isHandled) return super.mouseClicked(click, doubled)
        result.playSound()
        return true
    }

    override val mouseScrollPriorityAreas: List<Rect>
        get() = recipeView.petScrollAreas

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        val currentLayout = layout ?: return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)
        if (recipeView.wasPetScrollHandled(mouseX, mouseY, scrollY)) return true
        if (mode != ItemListViewMode.INFO) return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)
        val result = infoPanel.applyScroll(currentLayout.content, mouseX.toInt(), mouseY.toInt(), scrollY)
        return if (result.isHandled) true else super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)
    }

    override fun keyPressed(event: KeyEvent): Boolean {
        if (event.key() in listOf(GLFW.GLFW_KEY_ESCAPE, GLFW.GLFW_KEY_BACKSPACE) &&
            recipeView.closePopup().isHandled
        ) {
            return true
        }
        if (Minecraft.getInstance().options.keyInventory.matches(event)) {
            onClose()
            return true
        }
        itemListShortcutMode(event.key(), SkysoftConfigGui.config().inventory.itemList.settings)?.let {
            selection.changeMode(it)
            return true
        }
        return when (event.key()) {
            GLFW.GLFW_KEY_BACKSPACE -> selection.navigateBack().isHandled
            GLFW.GLFW_KEY_LEFT -> recipeView.changePage(-1, layout?.pageSize(currentKey) ?: 1)
                .also(ViewerInputResult::playSound)
                .isHandled
            GLFW.GLFW_KEY_RIGHT -> recipeView.changePage(1, layout?.pageSize(currentKey) ?: 1)
                .also(ViewerInputResult::playSound)
                .isHandled
            GLFW.GLFW_KEY_A -> {
                ItemListState.toggleFavorite(currentKey)
                true
            }
            else -> super.keyPressed(event)
        }
    }

    override fun onClose() {
        MinecraftClient.setScreen(parent)
    }

    override fun isPauseScreen(): Boolean = false

    private fun applyLeftClick(layout: ItemListViewerLayout, mouseX: Int, mouseY: Int): ViewerInputResult = when {
        layout.close.contains(mouseX, mouseY) -> {
            onClose()
            ViewerInputResult.HANDLED
        }
        layout.back.contains(mouseX, mouseY) -> selection.navigateBack()
        layout.forward.contains(mouseX, mouseY) -> selection.navigateForward()
        layout.favorite.contains(mouseX, mouseY) -> {
            ItemListState.toggleFavorite(currentKey)
            ViewerInputResult.HANDLED
        }
        layout.tierPrevious.contains(mouseX, mouseY) ->
            selection.navigateMinionTier(-1)
        layout.tierNext.contains(mouseX, mouseY) ->
            selection.navigateMinionTier(1)
        layout.infoTab.contains(mouseX, mouseY) -> selection.changeMode(ItemListViewMode.INFO)
        layout.recipeTab.contains(mouseX, mouseY) -> selection.changeMode(ItemListViewMode.RECIPES)
        layout.usageTab.contains(mouseX, mouseY) -> selection.changeMode(ItemListViewMode.USAGES)
        layout.previous.contains(mouseX, mouseY) -> recipeView.changePage(-1, layout.pageSize(currentKey))
        layout.next.contains(mouseX, mouseY) -> recipeView.changePage(1, layout.pageSize(currentKey))
        layout.wiki.contains(mouseX, mouseY) -> openWiki(currentKey, isVisible = mode == ItemListViewMode.INFO)
        else -> recipeView.click(layout, mouseX, mouseY)
    }

    private fun renderHeader(context: GuiGraphicsExtractor, layout: ItemListViewerLayout, mouseX: Int, mouseY: Int) {
        val entry = SkyBlockDataRepository.entry(currentKey)
        val stack = SkyBlockDataRepository.displayStack(currentKey)
        if (stack != null) {
            context.item(stack, layout.item.x + 1, layout.item.y + 1)
            if (layout.item.contains(mouseX, mouseY)) context.setTooltipForNextFrame(font, stack, mouseX, mouseY)
        }
        LegacyTextRenderer.draw(
            context,
            stack?.hoverName?.string ?: entry?.displayName ?: currentKey.id,
            layout.title.x,
            layout.title.y,
            shadow = false,
        )
        LegacyTextRenderer.draw(
            context,
            "§8${entry?.source ?: currentKey.kind.name.lowercase()}",
            layout.title.x,
            layout.title.y + LINE_HEIGHT,
            shadow = false,
        )
        PixelButtonRenderer.draw(
            context,
            font,
            layout.back,
            "<",
            false,
            layout.back.contains(mouseX, mouseY),
            selection.canGoBack,
        )
        PixelButtonRenderer.draw(
            context,
            font,
            layout.forward,
            ">",
            false,
            layout.forward.contains(mouseX, mouseY),
            selection.canGoForward,
        )
        renderFavoriteButton(context, font, layout.favorite, currentKey, mouseX, mouseY)
        PixelButtonRenderer.draw(context, font, layout.close, "X", false, layout.close.contains(mouseX, mouseY), true)
    }

    private fun renderTabs(context: GuiGraphicsExtractor, layout: ItemListViewerLayout, mouseX: Int, mouseY: Int) {
        val isEntity = currentKey.kind == ItemListEntryKind.ENTITY
        drawViewerTab(context, font, layout.infoTab, "Info", mode == ItemListViewMode.INFO, mouseX, mouseY, true, null)
        drawViewerTab(
            context,
            font,
            layout.recipeTab,
            if (isEntity) "Drops" else "Obtain",
            mode == ItemListViewMode.RECIPES,
            mouseX,
            mouseY,
            selection.hasObtainMethods(),
            if (isEntity) "No drops found" else "No obtain methods found",
        )
        if (isEntity) return
        drawViewerTab(
            context,
            font,
            layout.usageTab,
            "Uses",
            mode == ItemListViewMode.USAGES,
            mouseX,
            mouseY,
            selection.hasUsages(),
            "No uses found",
        )
    }

    private fun renderFooter(context: GuiGraphicsExtractor, layout: ItemListViewerLayout, mouseX: Int, mouseY: Int) {
        val minionFamily = minionFamily(currentKey)
        renderTierFooter(context, font, layout, currentKey, minionFamily, mouseX, mouseY)
        val pagination = recipeView.pagination(layout) ?: return
        layout.renderPageFooter(
            context,
            font,
            mouseX,
            mouseY,
            pagination.canGoPrevious,
            pagination.canGoNext,
            pagination.label,
            minionFamily != null,
        )
    }

    private companion object {
        const val LINE_HEIGHT = 12
        val SCREEN_OVERLAY = 0xB0000000.toInt()
    }
}

private fun renderTierFooter(
    context: GuiGraphicsExtractor,
    font: net.minecraft.client.gui.Font,
    layout: ItemListViewerLayout,
    key: ItemListEntryKey,
    family: ItemListTierFamily?,
    mouseX: Int,
    mouseY: Int,
) {
    if (family == null) return
    val index = family.tiers.indexOf(key)
    PixelButtonRenderer.draw(
        context,
        font,
        layout.tierPrevious,
        "<",
        false,
        layout.tierPrevious.contains(mouseX, mouseY),
        index > 0,
    )
    PixelButtonRenderer.draw(
        context,
        font,
        layout.tierNext,
        ">",
        false,
        layout.tierNext.contains(mouseX, mouseY),
        index + 1 < family.tiers.size,
    )
    drawViewerCentered(context, font, layout.tierPage, "${index + 1} / ${family.tiers.size}")
}

private fun renderFavoriteButton(
    context: GuiGraphicsExtractor,
    font: net.minecraft.client.gui.Font,
    bounds: Rect,
    key: ItemListEntryKey,
    mouseX: Int,
    mouseY: Int,
) {
    val isFavorite = ItemListState.isFavorite(key)
    val isHovered = bounds.contains(mouseX, mouseY)
    PixelButtonRenderer.draw(context, font, bounds, "", isFavorite, isHovered, true)
    context.blitSprite(
        RenderPipelines.GUI_TEXTURED,
        if (isFavorite) HEART_FULL else HEART_CONTAINER,
        bounds.x + (bounds.width - HEART_SIZE) / 2,
        bounds.y + (bounds.height - HEART_SIZE) / 2,
        HEART_SIZE,
        HEART_SIZE,
    )
    if (isHovered) {
        SkysoftNativeTooltip.setForNextFrame(context, listOf(favoriteTooltip(isFavorite)), mouseX, mouseY)
    }
}

private fun drawViewerTab(
    context: GuiGraphicsExtractor,
    font: net.minecraft.client.gui.Font,
    bounds: Rect,
    label: String,
    isSelected: Boolean,
    mouseX: Int,
    mouseY: Int,
    isEnabled: Boolean,
    disabledTooltip: String?,
) {
    val isHovered = bounds.contains(mouseX, mouseY)
    PixelButtonRenderer.draw(context, font, bounds, label, isSelected, isHovered, isEnabled)
    if (isHovered && !isEnabled && disabledTooltip != null) {
        SkysoftNativeTooltip.setForNextFrame(context, listOf(disabledTooltip), mouseX, mouseY)
    }
}

private fun requestNpcWarpAt(entityId: String?): NpcWarpClickResult {
    entityId ?: return NpcWarpClickResult.IGNORED
    return when (ItemListNpcWaypoint.requestWarp(entityId)) {
        NpcWarpRequestResult.WARP_SENT -> NpcWarpClickResult.WARP_SENT
        NpcWarpRequestResult.WAYPOINT_ACTIVATED -> NpcWarpClickResult.WAYPOINT_ACTIVATED
        NpcWarpRequestResult.REJECTED -> NpcWarpClickResult.IGNORED
    }
}

private enum class NpcWarpClickResult(
    val inputResult: ViewerInputResult,
    val shouldCloseScreen: Boolean,
) {
    IGNORED(ViewerInputResult.IGNORED, false),
    WARP_SENT(ViewerInputResult.HANDLED, false),
    WAYPOINT_ACTIVATED(ViewerInputResult.HANDLED, true),
}

private fun renderInfoLinks(
    context: GuiGraphicsExtractor,
    font: net.minecraft.client.gui.Font,
    layout: ItemListViewerLayout,
    key: ItemListEntryKey,
    mouseX: Int,
    mouseY: Int,
) {
    val link = SkyBlockDataRepository.wikiLink(key)
    PixelButtonRenderer.draw(
        context,
        font,
        layout.wiki,
        "SkyBlock Wiki",
        false,
        layout.wiki.contains(mouseX, mouseY),
        link != null,
    )
}

private fun drawViewerCentered(
    context: GuiGraphicsExtractor,
    font: net.minecraft.client.gui.Font,
    bounds: Rect,
    text: String,
) {
    context.text(
        font,
        text,
        bounds.x + (bounds.width - font.width(text)) / 2,
        bounds.y + (bounds.height - font.lineHeight) / 2,
        VIEWER_TEXT_COLOR,
        false,
    )
}

private const val HEART_SIZE = 9
private val HEART_CONTAINER = Identifier.withDefaultNamespace("hud/heart/container")
private val HEART_FULL = Identifier.withDefaultNamespace("hud/heart/full")
private val VIEWER_TEXT_COLOR = 0xFFE0E4E8.toInt()

private fun openWiki(key: ItemListEntryKey, isVisible: Boolean): ViewerInputResult {
    if (!isVisible) return ViewerInputResult.IGNORED
    val url = SkyBlockDataRepository.wikiLink(key) ?: return ViewerInputResult.IGNORED
    BrowserUtilities.tryOpen(url)
    return ViewerInputResult.HANDLED
}

private fun ItemListViewerLayout.renderPageFooter(
    context: GuiGraphicsExtractor,
    font: net.minecraft.client.gui.Font,
    mouseX: Int,
    mouseY: Int,
    canGoPrevious: Boolean,
    canGoNext: Boolean,
    label: String,
    hasTierNavigation: Boolean,
) {
    PixelButtonRenderer.draw(
        context,
        font,
        previous,
        "<",
        false,
        previous.contains(mouseX, mouseY),
        canGoPrevious,
    )
    PixelButtonRenderer.draw(
        context,
        font,
        next,
        ">",
        false,
        next.contains(mouseX, mouseY),
        canGoNext,
    )
    drawViewerCentered(
        context,
        font,
        if (hasTierNavigation) recipePageWithTiers else recipePage,
        label,
    )
}

private fun favoriteTooltip(isFavorite: Boolean): String =
    if (isFavorite) "Remove from favorites" else "Add to favorites"
