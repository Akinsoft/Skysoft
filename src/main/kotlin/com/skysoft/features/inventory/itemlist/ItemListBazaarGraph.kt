package com.skysoft.features.inventory.itemlist

import com.skysoft.config.BazaarGraphMode
import com.skysoft.config.BazaarGraphWindow
import com.skysoft.config.ItemListSourcesConfig
import com.skysoft.config.SkysoftConfigGui
import com.skysoft.data.ProfileStorageView
import com.skysoft.data.skyblock.price.SkysoftBazaarDepthProduct
import com.skysoft.features.bazaar.BazaarInvestmentPosition
import com.skysoft.utils.MILLIS_PER_SECOND
import com.skysoft.utils.gui.PixelButtonRenderer
import com.skysoft.utils.gui.Rect
import com.skysoft.utils.gui.fill
import com.skysoft.utils.gui.outline
import com.skysoft.utils.render.LegacyTextRenderer
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor

internal class ItemListBazaarGraph {
    private var filtersOpen = false
    private var rangeOpen = false

    fun reset() {
        filtersOpen = false
        rangeOpen = false
    }

    fun render(
        context: GuiGraphicsExtractor,
        font: Font,
        bounds: Rect,
        product: SkysoftBazaarDepthProduct?,
        investment: BazaarInvestmentPosition?,
        transactions: List<ProfileStorageView.BazaarTransactionData>,
        exitPrice: Double,
        updatedAtMillis: Long,
        state: BazaarDepthState,
        error: String?,
        mouseX: Int,
        mouseY: Int,
    ) {
        val preferences = preferences()
        BazaarGraphPlotRenderer.renderInvestment(context, bounds, investment, exitPrice, preferences.showBazaarPlayerData)
        val graph = graphBounds(bounds)
        drawGraphFrame(context, graph)
        renderGraphLabel(context, graph, bounds, updatedAtMillis, product, preferences)
        renderPlot(
            context,
            graph,
            product,
            investment,
            transactions,
            preferences,
            state,
            error,
            mouseX,
            mouseY,
        )
        renderRangeSelector(context, font, bounds, mouseX, mouseY, product != null, preferences.graphMode())
        renderFilterButton(context, font, bounds, mouseX, mouseY)
        if (filtersOpen) renderFilterMenu(context, font, bounds, mouseX, mouseY)
    }

    fun processClick(bounds: Rect, mouseX: Int, mouseY: Int): ViewerInputResult =
        processFilterInput(bounds, mouseX, mouseY) ?: processRangeInput(bounds, mouseX, mouseY)

    private fun processFilterInput(bounds: Rect, mouseX: Int, mouseY: Int): ViewerInputResult? {
        if (bazaarFilterButton(bounds).contains(mouseX, mouseY)) {
            filtersOpen = !filtersOpen
            rangeOpen = false
            return ViewerInputResult.HANDLED
        }
        if (!filtersOpen) return null
        val filter = BazaarGraphFilter.entries.withIndex()
            .firstOrNull { (index, _) -> bazaarFilterRow(bounds, index).contains(mouseX, mouseY) }
            ?.value ?: return null
        filter.apply(preferences())
        if (filter.mode != null) {
            filtersOpen = false
            rangeOpen = false
        }
        savePreferences()
        return ViewerInputResult.HANDLED
    }

    private fun processRangeInput(bounds: Rect, mouseX: Int, mouseY: Int): ViewerInputResult {
        val preferences = preferences()
        if (preferences.graphMode() == BazaarGraphMode.ORDER_BOOK) return ViewerInputResult.IGNORED
        if (bazaarRangeButton(bounds).contains(mouseX, mouseY)) {
            rangeOpen = !rangeOpen
            filtersOpen = false
            return ViewerInputResult.HANDLED
        }
        if (!rangeOpen) return ViewerInputResult.IGNORED
        val window = BazaarGraphWindow.entries.withIndex()
            .firstOrNull { (index, _) -> bazaarRangeRow(bounds, index).contains(mouseX, mouseY) }
            ?.value ?: return ViewerInputResult.IGNORED
        rangeOpen = false
        if (window != preferences.graphWindow()) {
            preferences.bazaarGraphWindow = window.name
            savePreferences()
        }
        return ViewerInputResult.HANDLED
    }

    private fun renderPlot(
        context: GuiGraphicsExtractor,
        bounds: Rect,
        product: SkysoftBazaarDepthProduct?,
        investment: BazaarInvestmentPosition?,
        transactions: List<ProfileStorageView.BazaarTransactionData>,
        preferences: ItemListSourcesConfig,
        state: BazaarDepthState,
        error: String?,
        mouseX: Int,
        mouseY: Int,
    ) {
        when {
            state == BazaarDepthState.FAILED -> BazaarGraphPlotRenderer.renderFailure(
                context,
                bounds,
                error,
                mouseX,
                mouseY,
            )
            state != BazaarDepthState.READY -> renderBazaarGraphMessage(context, bounds, "§7Loading Bazaar graph...")
            preferences.graphMode() == BazaarGraphMode.PRICE_HISTORY -> BazaarPriceHistoryRenderer.render(
                context,
                bounds,
                product,
                preferences,
                mouseX,
                mouseY,
            )
            preferences.graphMode() == BazaarGraphMode.ORDER_BOOK -> BazaarGraphPlotRenderer.renderPrice(
                context,
                bounds,
                product,
                investment,
                preferences,
                mouseX,
                mouseY,
            )
            else -> BazaarGraphPlotRenderer.renderTradeVolume(
                context,
                bounds,
                product,
                transactions,
                preferences,
                mouseX,
                mouseY,
            )
        }
    }

    private fun renderGraphLabel(
        context: GuiGraphicsExtractor,
        graph: Rect,
        panel: Rect,
        updatedAtMillis: Long,
        product: SkysoftBazaarDepthProduct?,
        preferences: ItemListSourcesConfig,
    ) {
        val mode = preferences.graphMode()
        val label = "§f${mode.label}"
        val labelY = graph.y - GraphLayout.LABEL_Y_OFFSET
        LegacyTextRenderer.draw(context, label, graph.x, labelY)
        val updated = updatedAtMillis.takeIf { it > 0L }?.let {
            val ageSeconds = ((System.currentTimeMillis() - it).coerceAtLeast(0L) / MILLIS_PER_SECOND)
            "§8Updated ${ageSeconds}s ago"
        }
        val updatedX = graph.x + LegacyTextRenderer.width(label) + GraphLayout.UPDATED_GAP
        if (updated != null) LegacyTextRenderer.draw(context, updated, updatedX, labelY)
        if (mode != BazaarGraphMode.ORDER_BOOK || product == null) return
        val maximumDepth = maxOf(
            product.sellSummary.takeIf { preferences.showBazaarBuyData }.orEmpty().sumOf { it.amount },
            product.buySummary.takeIf { preferences.showBazaarSellData }.orEmpty().sumOf { it.amount },
        )
        if (maximumDepth <= 0L) return
        val depthText = "§8${ItemListFormatting.compactNumber(maximumDepth)} items"
        val depthX = bazaarFilterButton(panel).x - GraphLayout.HEADER_VALUE_GAP - LegacyTextRenderer.width(depthText)
        val updatedRight = if (updated == null) updatedX else updatedX + LegacyTextRenderer.width(updated)
        if (depthX > updatedRight + GraphLayout.HEADER_VALUE_GAP) {
            LegacyTextRenderer.draw(context, depthText, depthX, labelY)
        }
    }

    private fun renderFilterButton(
        context: GuiGraphicsExtractor,
        font: Font,
        bounds: Rect,
        mouseX: Int,
        mouseY: Int,
    ) {
        val button = bazaarFilterButton(bounds)
        PixelButtonRenderer.draw(context, font, button, "Filters", filtersOpen, button.contains(mouseX, mouseY), true)
    }

    private fun renderFilterMenu(context: GuiGraphicsExtractor, font: Font, bounds: Rect, mouseX: Int, mouseY: Int) {
        val preferences = preferences()
        BazaarGraphFilter.entries.forEachIndexed { index, filter ->
            val label = filter.label(preferences.graphMode())
            val selected = filter.isSelected(preferences)
            val row = bazaarFilterRow(bounds, index)
            PixelButtonRenderer.draw(context, font, row, label, selected, row.contains(mouseX, mouseY), true)
        }
    }

    private fun renderRangeSelector(
        context: GuiGraphicsExtractor,
        font: Font,
        bounds: Rect,
        mouseX: Int,
        mouseY: Int,
        isEnabled: Boolean,
        mode: BazaarGraphMode,
    ) {
        if (mode == BazaarGraphMode.ORDER_BOOK) return
        val selected = preferences().graphWindow()
        val button = bazaarRangeButton(bounds)
        PixelButtonRenderer.draw(
            context,
            font,
            button,
            "Range: ${selected.label}",
            rangeOpen,
            button.contains(mouseX, mouseY),
            isEnabled,
        )
        if (!rangeOpen) return
        BazaarGraphWindow.entries.forEachIndexed { index, window ->
            val row = bazaarRangeRow(bounds, index)
            PixelButtonRenderer.draw(
                context,
                font,
                row,
                window.label,
                selected == window,
                row.contains(mouseX, mouseY),
                isEnabled,
            )
        }
    }

    private fun drawGraphFrame(context: GuiGraphicsExtractor, bounds: Rect) {
        context.fill(bounds, GraphStyle.BACKGROUND)
        context.outline(bounds, GraphStyle.OUTLINE)
    }

    private fun graphBounds(bounds: Rect) = Rect(
        bounds.x + GraphLayout.INSET,
        bounds.y + GraphLayout.TOP,
        bounds.width - GraphLayout.INSET * 2,
        (bounds.height - GraphLayout.TOP - GraphLayout.CONTROL_HEIGHT - GraphLayout.CONTROL_GAP)
            .coerceAtLeast(GraphLayout.MIN_HEIGHT),
    )

    private fun preferences(): ItemListSourcesConfig = SkysoftConfigGui.config().items.itemList.sources

    private fun savePreferences() = SkysoftConfigGui.config().saveNow()
}

private fun bazaarFilterButton(bounds: Rect) = Rect(
    bounds.x + bounds.width - GraphLayout.INSET - GraphLayout.FILTER_WIDTH,
    bazaarGraphLabelY(bounds) - GraphLayout.FILTER_LABEL_ALIGNMENT,
    GraphLayout.FILTER_WIDTH,
    GraphLayout.FILTER_HEIGHT,
)

private fun bazaarGraphLabelY(bounds: Rect): Int =
    bounds.y + GraphLayout.TOP - GraphLayout.LABEL_Y_OFFSET

private fun bazaarFilterRow(bounds: Rect, index: Int): Rect {
    require(index >= 0) { "Bazaar filter row index must not be negative" }
    val button = bazaarFilterButton(bounds)
    return Rect(
        bounds.x + bounds.width - GraphLayout.INSET - GraphLayout.FILTER_MENU_WIDTH,
        button.y + button.height + GraphLayout.FILTER_MENU_GAP + index * GraphLayout.FILTER_ROW_HEIGHT,
        GraphLayout.FILTER_MENU_WIDTH,
        GraphLayout.FILTER_ROW_HEIGHT,
    )
}

private fun bazaarRangeButton(bounds: Rect) = Rect(
    bounds.x + GraphLayout.INSET,
    bounds.y + bounds.height - GraphLayout.CONTROL_HEIGHT,
    GraphLayout.RANGE_MENU_WIDTH,
    GraphLayout.CONTROL_HEIGHT,
)

private fun bazaarRangeRow(bounds: Rect, index: Int): Rect {
    require(index in BazaarGraphWindow.entries.indices) { "Bazaar range row index is out of bounds" }
    val button = bazaarRangeButton(bounds)
    return Rect(
        button.x,
        button.y - GraphLayout.RANGE_MENU_GAP -
            (BazaarGraphWindow.entries.size - index) * GraphLayout.RANGE_ROW_HEIGHT,
        GraphLayout.RANGE_MENU_WIDTH,
        GraphLayout.RANGE_ROW_HEIGHT,
    )
}

private object GraphLayout {
    const val INSET = 6
    const val TOP = 88
    const val LABEL_Y_OFFSET = 12
    const val UPDATED_GAP = 6
    const val HEADER_VALUE_GAP = 5
    const val CONTROL_HEIGHT = 18
    const val CONTROL_GAP = 5
    const val MIN_HEIGHT = 38
    const val RANGE_MENU_WIDTH = 70
    const val RANGE_MENU_GAP = 1
    const val RANGE_ROW_HEIGHT = 15
    const val FILTER_WIDTH = 52
    const val FILTER_HEIGHT = 14
    const val FILTER_LABEL_ALIGNMENT = 2
    const val FILTER_MENU_WIDTH = 102
    const val FILTER_MENU_GAP = 1
    const val FILTER_ROW_HEIGHT = 15
}

private object GraphStyle {
    const val BACKGROUND = 0x70101316
    val OUTLINE = 0xFF48515A.toInt()
}

private enum class BazaarGraphFilter(val mode: BazaarGraphMode? = null) {
    PRICE_HISTORY(BazaarGraphMode.PRICE_HISTORY),
    ORDER_BOOK(BazaarGraphMode.ORDER_BOOK),
    TRADE_VOLUME(BazaarGraphMode.TRADE_VOLUME),
    BUY,
    SELL,
    PLAYER,
    ;

    fun label(currentMode: BazaarGraphMode): String = when (this) {
        BUY -> currentMode.buyFilterLabel
        SELL -> currentMode.sellFilterLabel
        PLAYER -> "My Trades"
        else -> requireNotNull(mode).label
    }

    fun isSelected(preferences: ItemListSourcesConfig): Boolean = when (this) {
        BUY -> preferences.showBazaarBuyData
        SELL -> preferences.showBazaarSellData
        PLAYER -> preferences.showBazaarPlayerData
        else -> preferences.graphMode() == mode
    }

    fun apply(preferences: ItemListSourcesConfig) {
        when (this) {
            BUY -> preferences.showBazaarBuyData = !preferences.showBazaarBuyData
            SELL -> preferences.showBazaarSellData = !preferences.showBazaarSellData
            PLAYER -> preferences.showBazaarPlayerData = !preferences.showBazaarPlayerData
            else -> preferences.bazaarGraphMode = requireNotNull(mode).name
        }
    }
}

