package com.skysoft.features.inventory.sacks

import com.skysoft.config.ProfitTrackerPriceSource
import com.skysoft.config.SkysoftConfigGui
import com.skysoft.data.hypixel.HypixelLocationState
import com.skysoft.data.skyblock.SkyBlockDataRepository
import com.skysoft.data.skyblock.SkyBlockOpenInventoryApi
import com.skysoft.data.skyblock.SkyBlockOpenInventorySnapshot
import com.skysoft.data.skyblock.isSackContentsMenu
import com.skysoft.features.inventory.InventoryOverlayInput
import com.skysoft.features.inventory.InventoryTrackerFrame
import com.skysoft.features.profit.profitTrackerSourcePrice
import com.skysoft.gui.GuiOverlay
import com.skysoft.gui.GuiOverlayContextType
import com.skysoft.gui.GuiOverlayLayer
import com.skysoft.gui.GuiOverlayRegistry
import com.skysoft.gui.OverlayControlArea
import com.skysoft.gui.OverlayControlCycle
import com.skysoft.gui.OverlayControlTooltips
import com.skysoft.gui.RenderableHudEditorElement
import com.skysoft.gui.SkysoftHudEditor
import com.skysoft.gui.TopLeftHudEditorElement
import com.skysoft.gui.tooltip.SkysoftNativeTooltip
import com.skysoft.utils.MinecraftClient
import com.skysoft.utils.NumberUtilities.addSeparators
import com.skysoft.utils.NumberUtilities.coinFormat
import com.skysoft.utils.SkysoftClientEvents
import com.skysoft.utils.SoundUtilities
import com.skysoft.utils.TextUtilities.cleanSkyBlockText
import com.skysoft.utils.TextUtilities.truncateLegacyText
import com.skysoft.utils.gui.OverlayItemRowStyle
import com.skysoft.utils.gui.OverlayListScroll
import com.skysoft.utils.gui.OverlayPanelStyle
import com.skysoft.utils.gui.OverlayTextStyle
import com.skysoft.utils.gui.Rect
import com.skysoft.utils.render.LegacyTextRenderer
import com.skysoft.utils.renderables.GuiRenderable
import com.skysoft.utils.renderables.primitives.ItemIconRenderable
import com.skysoft.utils.renderables.renderAt
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.world.item.ItemStack

object SackDisplay {
    fun register() {
        SkyBlockDataRepository.Demand.register("Sack Display") { config.enabled }
        SkyBlockOpenInventoryApi.onChange(
            "Sack Display inventory",
            isActive = { config.enabled && HypixelLocationState.inSkyBlock },
            listener = ::updateOpenSack,
        )
        SkysoftClientEvents.onDisconnect("Sack Display reset", ::clearSackDisplay)
        InventoryOverlayInput.registerTrackerInput(
            "Sack Display",
            isActive = { true },
            shouldAllowClick = ::shouldAllowSackDisplayClick,
            wasScrollHandled = ::wasSackDisplayScrollHandled,
        )
        GuiOverlayRegistry.registerHud(
            GuiOverlay(
                id = "sack_display",
                layer = GuiOverlayLayer.BELOW_SCREEN,
                contexts = GuiOverlayContextType.INVENTORIES,
                screenForegroundContexts = GuiOverlayContextType.INVENTORIES,
                render = { context, _ -> renderSackDisplay(context) },
            ),
            object : TopLeftHudEditorElement, RenderableHudEditorElement {
                override val id: String = "sack_display"
                override val label: String = "Sack Display"
                override val position get() = config.position
                override val hasEditorBackground: Boolean get() = !config.details.showBackground

                override fun hudRenderable() = editorRenderable()
                override fun isVisible(): Boolean = isSackDisplayVisible()
                override fun openConfig() = SkysoftConfigGui.open("Sack Display")
            },
        )
    }
}

private val config get() = SkysoftConfigGui.config().storageFeatures.sackDisplay
private var openSack: OpenSack? = null
private var displayMode = SackDisplayMode.ITEM_QUANTITIES
private var scrollOffset = 0
private var hoveredControl: OverlayControlArea<SackDisplayControl>? = null
private var isDisplayHovered = false

private fun shouldAllowSackDisplayClick(
    screen: AbstractContainerScreen<*>,
    click: MouseButtonEvent,
): Boolean {
    if (!isSackDisplayVisible()) return true
    if (InventoryOverlayInput.isPointCovered(screen, click.x(), click.y())) return true
    val control = hoveredControl?.action ?: return true
    val handled = when (control) {
        SackDisplayControl.Mode -> OverlayControlCycle.wasClickHandled(click.button()) { backwards ->
            displayMode = OverlayControlCycle.next(SackDisplayMode.entries, displayMode, backwards)
        }
        SackDisplayControl.PriceSource -> OverlayControlCycle.wasClickHandled(click.button()) { backwards ->
            config.settings.priceSource = OverlayControlCycle.next(
                ProfitTrackerPriceSource.entries,
                config.settings.priceSource,
                backwards,
            )
            SkysoftConfigGui.config().saveNow()
        }
        is SackDisplayControl.Item -> wasSackItemClickHandled(screen, control.item.itemId, control.item.name, click.button())
    }
    if (handled) SoundUtilities.playClickSound()
    return !handled
}

private fun wasSackDisplayScrollHandled(verticalAmount: Double): Boolean {
    if (!isSackDisplayVisible() || !isDisplayHovered || verticalAmount == 0.0) return false
    val items = openSack?.items ?: return false
    val maximumOffset = maximumScrollOffset(items.size)
    if (maximumOffset == 0) return false
    scrollOffset = OverlayListScroll.nextOffset(scrollOffset, verticalAmount, maximumOffset)
    return true
}

private fun updateOpenSack(snapshot: SkyBlockOpenInventorySnapshot?) {
    if (snapshot == null && MinecraftClient.screen() is SkysoftHudEditor.EditorScreen) return
    if (snapshot == null || !isSackContentsMenu(snapshot.title)) {
        clearSackDisplay()
        return
    }
    val items = snapshot.items.toSortedMap().values.flatMap(::sackDisplayItems)
    val previous = openSack
    if (previous?.containerId != snapshot.containerId || previous.title != snapshot.title) scrollOffset = 0
    openSack = OpenSack(snapshot.title, snapshot.containerId, items)
    scrollOffset = scrollOffset.coerceIn(0, maximumScrollOffset(items.size))
}

private fun clearSackDisplay() {
    openSack = null
    scrollOffset = 0
    hoveredControl = null
    isDisplayHovered = false
}

private fun renderSackDisplay(context: GuiGraphicsExtractor) {
    if (!isSackDisplayVisible()) {
        hoveredControl = null
        isDisplayHovered = false
        return
    }
    val sack = openSack ?: return
    val renderable = buildRenderable(sack)
    val frame = InventoryTrackerFrame(config.position, renderable.width, renderable.height)
    hoveredControl = frame.render(context) { mouseX, mouseY, _ ->
        renderable.renderInteractive(context, mouseX, mouseY)
    }
    isDisplayHovered = frame.isHovered
    if (frame.interactive) hoveredControl?.let { control ->
        context.nextStratum()
        val item = (control.action as? SackDisplayControl.Item)?.item
        if (item != null) {
            SkysoftNativeTooltip.setItemActionForNextFrame(
                context,
                item.stack ?: ItemStack.EMPTY,
                null,
                item.name,
                frame.screenMouseX,
                frame.screenMouseY,
                actionLines = sackItemActionLines(),
            )
        } else {
            SkysoftNativeTooltip.setForNextFrame(
                context,
                control.tooltipLines,
                frame.screenMouseX,
                frame.screenMouseY,
                scrollable = false,
            )
        }
    }
}

private fun isSackDisplayVisible(): Boolean {
    if (!config.enabled || !HypixelLocationState.inSkyBlock) return false
    val minecraft = Minecraft.getInstance()
    if (MinecraftClient.isGuiHidden(minecraft)) return false
    val screen = MinecraftClient.screen(minecraft) as? AbstractContainerScreen<*> ?: return false
    val sack = openSack ?: return false
    return screen.menu.containerId == sack.containerId && isSackContentsMenu(screen.title.cleanSkyBlockText())
}

private fun buildRenderable(sack: OpenSack): SackDisplayRenderable {
    scrollOffset = scrollOffset.coerceIn(0, maximumScrollOffset(sack.items.size))
    val displayedItems = sack.items.drop(scrollOffset).take(maximumDisplayedItems())
    return SackDisplayRenderable(
        title = sack.title,
        items = displayedItems,
        hiddenAbove = scrollOffset,
        hiddenBelow = (sack.items.size - scrollOffset - displayedItems.size).coerceAtLeast(0),
        mode = displayMode,
        priceSource = config.settings.priceSource,
        totalValue = if (displayMode == SackDisplayMode.TOTAL_VALUE) {
            sack.items.totalValue(config.settings.priceSource)
        } else {
            null
        },
        showIcons = config.details.showItemIcons,
        background = config.details.showBackground,
    )
}

private fun editorRenderable(): SackDisplayRenderable? = openSack?.let(::buildRenderable)

private fun maximumDisplayedItems(): Int = config.settings.maximumItems.coerceIn(1, MAXIMUM_DISPLAY_ITEMS)

private fun maximumScrollOffset(itemCount: Int): Int = (itemCount - maximumDisplayedItems()).coerceAtLeast(0)

private class SackDisplayRenderable(
    private val title: String,
    items: List<SackDisplayItem>,
    hiddenAbove: Int,
    hiddenBelow: Int,
    private val mode: SackDisplayMode,
    private val priceSource: ProfitTrackerPriceSource,
    private val totalValue: Double?,
    private val showIcons: Boolean,
    private val background: Boolean,
) : GuiRenderable {
    private val padding = if (background) OverlayPanelStyle.PADDING else 0
    private val rows = items.map { item ->
        SackDisplayRow(
            item = item,
            name = item.name.truncateLegacyText(MAXIMUM_ITEM_NAME_LENGTH),
            value = item.displayValue(mode, priceSource),
            reserveIcon = showIcons,
        )
    }
    private val emptyText = "§7No stored items."
    private val indicatorText = OverlayTextStyle.hiddenRowsIndicator(hiddenAbove, hiddenBelow, " §8• §7")
    private val modeLine = "§7Mode: §a§l[${mode.displayName}]"
    private val priceSourceLine = "§7Price Source: §e§l[$priceSource]"
    private val totalText = coinValueText(totalValue)
    private val totalWidth = LegacyTextRenderer.width("§7Total") + OverlayItemRowStyle.VALUE_COLUMN_GAP +
        LegacyTextRenderer.width(totalText)
    private val contentWidth = maxOf(
        MINIMUM_WIDTH,
        LegacyTextRenderer.width(OverlayTextStyle.title(title)),
        rows.maxOfOrNull(SackDisplayRow::width) ?: LegacyTextRenderer.width(emptyText),
        LegacyTextRenderer.width(indicatorText),
        LegacyTextRenderer.width(modeLine),
        LegacyTextRenderer.width(priceSourceLine).takeIf { mode == SackDisplayMode.TOTAL_VALUE } ?: 0,
        totalWidth.takeIf { mode == SackDisplayMode.TOTAL_VALUE } ?: 0,
    )

    override val width: Int = contentWidth + padding * 2
    override val height: Int = padding * 2 + OverlayTextStyle.TITLE_HEIGHT +
        (if (rows.isEmpty()) OverlayTextStyle.ROW_HEIGHT else rows.size * OverlayItemRowStyle.HEIGHT) +
        (if (indicatorText.isEmpty()) 0 else OverlayTextStyle.ROW_HEIGHT) +
        (if (mode == SackDisplayMode.TOTAL_VALUE) OverlayTextStyle.ROW_HEIGHT + CONTROL_ROW_HEIGHT else 0) +
        CONTROL_ROW_HEIGHT

    override fun render(context: GuiGraphicsExtractor) {
        renderInteractive(context, null, null)
    }

    fun renderInteractive(
        context: GuiGraphicsExtractor,
        mouseX: Int?,
        mouseY: Int?,
    ): OverlayControlArea<SackDisplayControl>? {
        if (background) OverlayPanelStyle.drawHudBackground(context, width, height)
        var y = padding
        LegacyTextRenderer.draw(context, OverlayTextStyle.title(title), padding, y)
        y += OverlayTextStyle.TITLE_HEIGHT
        var hoveredItem: OverlayControlArea<SackDisplayControl>? = null
        if (rows.isEmpty()) {
            LegacyTextRenderer.draw(context, emptyText, padding, y)
            y += OverlayTextStyle.ROW_HEIGHT
        } else {
            rows.forEach { row ->
                hoveredItem = row.renderInteractive(context, padding, width - padding, y, mouseX, mouseY)
                    ?: hoveredItem
                y += OverlayItemRowStyle.HEIGHT
            }
        }
        if (indicatorText.isNotEmpty()) {
            LegacyTextRenderer.draw(context, indicatorText, (width - LegacyTextRenderer.width(indicatorText)) / 2, y)
            y += OverlayTextStyle.ROW_HEIGHT
        }
        var hoveredPriceSource: OverlayControlArea<SackDisplayControl>? = null
        if (mode == SackDisplayMode.TOTAL_VALUE) {
            LegacyTextRenderer.draw(context, "§7Total", padding, y)
            LegacyTextRenderer.draw(context, totalText, width - padding - LegacyTextRenderer.width(totalText), y)
            y += OverlayTextStyle.ROW_HEIGHT
            hoveredPriceSource = renderControl(
                context,
                y,
                priceSourceLine,
                SackDisplayControl.PriceSource,
                OverlayControlTooltips.cycle(
                    "Price Source",
                    ProfitTrackerPriceSource.entries.map(ProfitTrackerPriceSource::toString),
                    priceSource.ordinal,
                ),
                mouseX,
                mouseY,
            )
            y += CONTROL_ROW_HEIGHT
        }
        val hoveredMode = renderControl(
            context,
            y,
            modeLine,
            SackDisplayControl.Mode,
            OverlayControlTooltips.cycle(
                "Mode",
                SackDisplayMode.entries.map(SackDisplayMode::displayName),
                mode.ordinal,
            ),
            mouseX,
            mouseY,
        )
        return hoveredItem ?: hoveredPriceSource ?: hoveredMode
    }

    private fun renderControl(
        context: GuiGraphicsExtractor,
        y: Int,
        line: String,
        action: SackDisplayControl,
        tooltipLines: List<String>,
        mouseX: Int?,
        mouseY: Int?,
    ): OverlayControlArea<SackDisplayControl>? {
        val bounds = Rect(padding, y, LegacyTextRenderer.width(line), CONTROL_ROW_HEIGHT)
        val hovered = bounds.contains(mouseX, mouseY)
        if (hovered) OverlayTextStyle.drawControlHover(context, bounds, 1.0)
        LegacyTextRenderer.draw(context, line, bounds.x, y + CONTROL_TEXT_Y_OFFSET)
        return OverlayControlArea<SackDisplayControl>(action, bounds, tooltipLines).takeIf { hovered }
    }
}

private data class SackDisplayRow(
    val item: SackDisplayItem,
    val name: String,
    val value: String,
    val reserveIcon: Boolean,
) {
    private val contentOffset = if (reserveIcon) OverlayItemRowStyle.ICON_TEXT_OFFSET else 0
    val width: Int = contentOffset + LegacyTextRenderer.width(name) + OverlayItemRowStyle.VALUE_COLUMN_GAP +
        LegacyTextRenderer.width(value)

    fun renderInteractive(
        context: GuiGraphicsExtractor,
        left: Int,
        right: Int,
        y: Int,
        mouseX: Int?,
        mouseY: Int?,
    ): OverlayControlArea<SackDisplayControl>? {
        val bounds = Rect(left, y, right - left, OverlayItemRowStyle.HEIGHT)
        val hovered = bounds.contains(mouseX, mouseY)
        if (hovered) OverlayTextStyle.drawControlHover(context, bounds, 1.0)
        if (reserveIcon) {
            item.stack?.let { ItemIconRenderable(it, OverlayItemRowStyle.ICON_SCALE).renderAt(context, left, y) }
        }
        LegacyTextRenderer.draw(context, name, left + contentOffset, y + OverlayItemRowStyle.TEXT_Y_OFFSET)
        LegacyTextRenderer.draw(
            context,
            value,
            right - LegacyTextRenderer.width(value),
            y + OverlayItemRowStyle.TEXT_Y_OFFSET,
        )
        return OverlayControlArea<SackDisplayControl>(SackDisplayControl.Item(item), bounds, emptyList()).takeIf { hovered }
    }
}

private fun SackDisplayItem.displayValue(
    mode: SackDisplayMode,
    priceSource: ProfitTrackerPriceSource,
): String = when (mode) {
    SackDisplayMode.ITEM_QUANTITIES -> if (amount == 0L) {
        "§8x0"
    } else {
        "§7x§e${amount.addSeparators()}" + (capacity?.let { " §8/ §7$it" } ?: "")
    }
    SackDisplayMode.TOTAL_VALUE -> coinValueText(totalValue(priceSource))
}

private fun coinValueText(value: Double?): String = value?.let { "§6${it.coinFormat()} coins" } ?: "§8Unknown"

private fun SackDisplayItem.totalValue(priceSource: ProfitTrackerPriceSource): Double? = if (amount == 0L) {
    0.0
} else {
    profitTrackerSourcePrice(itemId, priceSource)
        ?.takeIf { it.isFinite() && it > 0.0 }
        ?.times(amount)
        ?.takeIf(Double::isFinite)
}

private fun List<SackDisplayItem>.totalValue(priceSource: ProfitTrackerPriceSource): Double? {
    var total = 0.0
    for (item in this) {
        total += item.totalValue(priceSource) ?: return null
        if (!total.isFinite()) return null
    }
    return total
}

private data class OpenSack(
    val title: String,
    val containerId: Int,
    val items: List<SackDisplayItem>,
)

private enum class SackDisplayMode(val displayName: String) {
    ITEM_QUANTITIES("Quantities"),
    TOTAL_VALUE("Total Value"),
}

private sealed interface SackDisplayControl {
    data object Mode : SackDisplayControl
    data object PriceSource : SackDisplayControl
    data class Item(val item: SackDisplayItem) : SackDisplayControl
}

private const val MAXIMUM_DISPLAY_ITEMS = 30
private const val MAXIMUM_ITEM_NAME_LENGTH = 30
private const val MINIMUM_WIDTH = 190
private const val CONTROL_ROW_HEIGHT = 13
private const val CONTROL_TEXT_Y_OFFSET = 1
