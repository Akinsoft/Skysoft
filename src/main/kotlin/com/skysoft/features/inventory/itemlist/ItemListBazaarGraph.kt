package com.skysoft.features.inventory.itemlist

import com.skysoft.config.ItemListSourcesConfig
import com.skysoft.config.SkysoftConfigGui
import com.skysoft.data.ProfileStorage
import com.skysoft.data.skyblock.price.SkysoftBazaarDepthProduct
import com.skysoft.features.bazaar.BazaarInvestmentPosition
import com.skysoft.utils.gui.PixelButtonRenderer
import com.skysoft.utils.gui.Rect
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
        transactions: List<ProfileStorage.BazaarTransactionData>,
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
        renderGraphLabel(context, graph, bounds, preferences.graphMode(), updatedAtMillis, product, preferences)
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

    fun processClick(bounds: Rect, mouseX: Int, mouseY: Int): BazaarGraphInputResult =
        processFilterInput(bounds, mouseX, mouseY) ?: processRangeInput(bounds, mouseX, mouseY)

    private fun processFilterInput(bounds: Rect, mouseX: Int, mouseY: Int): BazaarGraphInputResult? {
        if (filterButton(bounds).contains(mouseX, mouseY)) {
            filtersOpen = !filtersOpen
            rangeOpen = false
            return BazaarGraphInputResult.HANDLED
        }
        if (!filtersOpen) return null
        val row = FilterOptions.rows(preferences().graphMode()).indices
            .firstOrNull { filterRow(bounds, it).contains(mouseX, mouseY) }
            ?: return null
        return processFilterChange(row)
    }

    private fun processRangeInput(bounds: Rect, mouseX: Int, mouseY: Int): BazaarGraphInputResult {
        val preferences = preferences()
        if (preferences.graphMode() == BazaarGraphMode.ORDER_BOOK) return BazaarGraphInputResult.IGNORED
        if (rangeButton(bounds).contains(mouseX, mouseY)) {
            rangeOpen = !rangeOpen
            filtersOpen = false
            return BazaarGraphInputResult.HANDLED
        }
        if (!rangeOpen) return BazaarGraphInputResult.IGNORED
        val window = BazaarGraphWindow.entries.withIndex()
            .firstOrNull { (index, _) -> rangeRow(bounds, index).contains(mouseX, mouseY) }
            ?.value ?: return BazaarGraphInputResult.IGNORED
        return selectGraphWindow(window, preferences)
    }

    private fun selectGraphWindow(
        window: BazaarGraphWindow,
        preferences: ItemListSourcesConfig,
    ): BazaarGraphInputResult {
        rangeOpen = false
        if (window == preferences.graphWindow()) return BazaarGraphInputResult.HANDLED
        preferences.bazaarGraphWindow = window.name
        savePreferences()
        return BazaarGraphInputResult.HANDLED
    }

    private fun renderPlot(
        context: GuiGraphicsExtractor,
        bounds: Rect,
        product: SkysoftBazaarDepthProduct?,
        investment: BazaarInvestmentPosition?,
        transactions: List<ProfileStorage.BazaarTransactionData>,
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
            state != BazaarDepthState.READY -> LegacyTextRenderer.draw(
                context,
                "§7Loading Bazaar graph...",
                bounds.x + GraphLayout.MESSAGE_INSET,
                bounds.y + GraphLayout.MESSAGE_INSET,
            )
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

    private fun processFilterChange(row: Int): BazaarGraphInputResult {
        val preferences = preferences()
        when (row) {
            FilterOptions.PRICE_HISTORY -> preferences.bazaarGraphMode = BazaarGraphMode.PRICE_HISTORY.name
            FilterOptions.ORDER_BOOK -> preferences.bazaarGraphMode = BazaarGraphMode.ORDER_BOOK.name
            FilterOptions.TRADE_VOLUME -> preferences.bazaarGraphMode = BazaarGraphMode.TRADE_VOLUME.name
            FilterOptions.BUY -> preferences.showBazaarBuyData = !preferences.showBazaarBuyData
            FilterOptions.SELL -> preferences.showBazaarSellData = !preferences.showBazaarSellData
            FilterOptions.PLAYER -> preferences.showBazaarPlayerData = !preferences.showBazaarPlayerData
            else -> return BazaarGraphInputResult.IGNORED
        }
        if (isBazaarGraphModeFilter(row)) {
            filtersOpen = false
            rangeOpen = false
        }
        savePreferences()
        return BazaarGraphInputResult.HANDLED
    }

    private fun renderGraphLabel(
        context: GuiGraphicsExtractor,
        graph: Rect,
        panel: Rect,
        mode: BazaarGraphMode,
        updatedAtMillis: Long,
        product: SkysoftBazaarDepthProduct?,
        preferences: ItemListSourcesConfig,
    ) {
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
        val button = filterButton(bounds)
        PixelButtonRenderer.draw(context, font, button, "Filters", filtersOpen, button.contains(mouseX, mouseY), true)
    }

    private fun renderFilterMenu(context: GuiGraphicsExtractor, font: Font, bounds: Rect, mouseX: Int, mouseY: Int) {
        val preferences = preferences()
        FilterOptions.rows(preferences.graphMode()).forEachIndexed { index, label ->
            val selected = when (index) {
                FilterOptions.PRICE_HISTORY -> preferences.graphMode() == BazaarGraphMode.PRICE_HISTORY
                FilterOptions.ORDER_BOOK -> preferences.graphMode() == BazaarGraphMode.ORDER_BOOK
                FilterOptions.TRADE_VOLUME -> preferences.graphMode() == BazaarGraphMode.TRADE_VOLUME
                FilterOptions.BUY -> preferences.showBazaarBuyData
                FilterOptions.SELL -> preferences.showBazaarSellData
                FilterOptions.PLAYER -> preferences.showBazaarPlayerData
                else -> false
            }
            val row = filterRow(bounds, index)
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
        val button = rangeButton(bounds)
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
            val row = rangeRow(bounds, index)
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
        context.fill(bounds.x, bounds.y, bounds.x + bounds.width, bounds.y + bounds.height, GraphStyle.BACKGROUND)
        context.outline(bounds.x, bounds.y, bounds.width, bounds.height, GraphStyle.OUTLINE)
    }

    private fun graphBounds(bounds: Rect) = Rect(
        bounds.x + GraphLayout.INSET,
        bounds.y + GraphLayout.TOP,
        bounds.width - GraphLayout.INSET * 2,
        (bounds.height - GraphLayout.TOP - GraphLayout.CONTROL_HEIGHT - GraphLayout.CONTROL_GAP)
            .coerceAtLeast(GraphLayout.MIN_HEIGHT),
    )

    private fun filterButton(bounds: Rect) = bazaarFilterButton(bounds)

    private fun filterRow(bounds: Rect, index: Int): Rect = bazaarFilterRow(bounds, index)

    private fun rangeButton(bounds: Rect) = bazaarRangeButton(bounds)

    private fun rangeRow(bounds: Rect, index: Int) = bazaarRangeRow(bounds, index)

    private fun preferences(): ItemListSourcesConfig = SkysoftConfigGui.config().inventory.itemList.sources

    private fun savePreferences() = SkysoftConfigGui.config().saveNow()
}

internal fun bazaarFilterButton(bounds: Rect) = Rect(
    bounds.x + bounds.width - GraphLayout.INSET - GraphLayout.FILTER_WIDTH,
    bazaarGraphLabelY(bounds) - GraphLayout.FILTER_LABEL_ALIGNMENT,
    GraphLayout.FILTER_WIDTH,
    GraphLayout.FILTER_HEIGHT,
)

internal fun bazaarGraphLabelY(bounds: Rect): Int =
    bounds.y + GraphLayout.TOP - GraphLayout.LABEL_Y_OFFSET

internal fun bazaarFilterRow(bounds: Rect, index: Int): Rect {
    require(index >= 0) { "Bazaar filter row index must not be negative" }
    val button = bazaarFilterButton(bounds)
    return Rect(
        bounds.x + bounds.width - GraphLayout.INSET - GraphLayout.FILTER_MENU_WIDTH,
        button.y + button.height + GraphLayout.FILTER_MENU_GAP + index * GraphLayout.FILTER_ROW_HEIGHT,
        GraphLayout.FILTER_MENU_WIDTH,
        GraphLayout.FILTER_ROW_HEIGHT,
    )
}

internal fun bazaarRangeButton(bounds: Rect) = Rect(
    bounds.x + GraphLayout.INSET,
    bounds.y + bounds.height - GraphLayout.CONTROL_HEIGHT,
    GraphLayout.RANGE_MENU_WIDTH,
    GraphLayout.CONTROL_HEIGHT,
)

internal fun bazaarRangeRow(bounds: Rect, index: Int): Rect {
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
    const val MESSAGE_INSET = 5
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

private object FilterOptions {
    const val PRICE_HISTORY = 0
    const val ORDER_BOOK = 1
    const val TRADE_VOLUME = 2
    const val BUY = 3
    const val SELL = 4
    const val PLAYER = 5

    fun rows(mode: BazaarGraphMode) = listOf(
        BazaarGraphMode.PRICE_HISTORY.label,
        BazaarGraphMode.ORDER_BOOK.label,
        BazaarGraphMode.TRADE_VOLUME.label,
        mode.buyFilterLabel,
        mode.sellFilterLabel,
        "My Trades",
    )
}

internal enum class BazaarGraphInputResult(val isHandled: Boolean) {
    IGNORED(false),
    HANDLED(true),
}

internal enum class BazaarGraphMode(
    val label: String,
    val buyFilterLabel: String,
    val sellFilterLabel: String,
) {
    PRICE_HISTORY("Price History", "Buy Prices", "Sell Prices"),
    ORDER_BOOK("Order Book Depth", "Buy Orders", "Sell Orders"),
    TRADE_VOLUME("Trade Volume", "Buy Volume", "Sell Volume"),
}

internal enum class BazaarGraphWindow(val label: String, val durationMillis: Long) {
    FIFTEEN_MINUTES("15m", 15 * 60_000L),
    THIRTY_MINUTES("30m", 30 * 60_000L),
    ONE_HOUR("1h", 60 * 60_000L),
    SIX_HOURS("6h", 6 * 60 * 60_000L),
    TWENTY_FOUR_HOURS("24h", 24 * 60 * 60_000L),
    SEVEN_DAYS("7d", 7 * 24 * 60 * 60_000L),
    THIRTY_DAYS("30d", 30 * 24 * 60 * 60_000L),
}

internal fun isBazaarGraphModeFilter(index: Int): Boolean = index in
    FilterOptions.PRICE_HISTORY..FilterOptions.TRADE_VOLUME

internal fun ItemListSourcesConfig.graphMode(): BazaarGraphMode = when (bazaarGraphMode) {
    "PRICE" -> BazaarGraphMode.ORDER_BOOK
    "ACTIVITY" -> BazaarGraphMode.TRADE_VOLUME
    else -> BazaarGraphMode.entries.firstOrNull { it.name == bazaarGraphMode } ?: BazaarGraphMode.PRICE_HISTORY
}

internal fun ItemListSourcesConfig.graphWindow(): BazaarGraphWindow =
    BazaarGraphWindow.entries.firstOrNull { it.name == bazaarGraphWindow } ?: BazaarGraphWindow.ONE_HOUR

private const val MILLIS_PER_SECOND = 1_000L
