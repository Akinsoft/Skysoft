package com.skysoft.features.inventory.itemlist

import com.skysoft.config.ItemListSourcesConfig
import com.skysoft.data.skyblock.price.SkysoftBazaarDepthProduct
import com.skysoft.data.skyblock.price.SkysoftBazaarPriceSnapshot
import com.skysoft.gui.tooltip.SkysoftNativeTooltip
import com.skysoft.utils.NumberUtilities.coinAmountFormat
import com.skysoft.utils.IdentityRefreshCache
import com.skysoft.utils.gui.Rect
import com.skysoft.utils.render.GuiLineRenderer
import com.skysoft.utils.render.LegacyTextRenderer
import java.time.Instant
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt
import net.minecraft.client.gui.GuiGraphicsExtractor

internal object BazaarPriceHistoryRenderer {
    private val rowsCache = IdentityRefreshCache<PriceRowsCriteria, List<SkysoftBazaarPriceSnapshot>>(
        PriceHistoryLayout.CACHE_MILLIS,
    )

    fun render(
        context: GuiGraphicsExtractor,
        bounds: Rect,
        product: SkysoftBazaarDepthProduct?,
        preferences: ItemListSourcesConfig,
        mouseX: Int,
        mouseY: Int,
    ) {
        val series = visibleSeries(preferences)
        if (series.isEmpty()) {
            renderMessage(context, bounds, "§8No price filters selected")
            return
        }
        val now = System.currentTimeMillis()
        val window = preferences.graphWindow()
        val maximumPoints = (bounds.width / PriceHistoryLayout.POINT_SPACING).coerceIn(
            PriceHistoryLayout.MIN_POINTS,
            PriceHistoryLayout.MAX_POINTS,
        )
        val rows = priceRows(product, window, maximumPoints, now)
        val visiblePrices = rows.flatMap { row ->
            series.mapNotNull { it.value(row).takeIf { price -> price > 0.0 } }
        }
        if (visiblePrices.isEmpty()) {
            renderMessage(context, bounds, "§8No Bazaar price history in this range")
            return
        }
        val plot = pricePlot(bounds, visiblePrices)
        renderLegend(context, bounds, series)
        renderGrid(context, plot)
        series.forEach { renderSeries(context, plot, rows, it, now - window.durationMillis, now) }
        renderHover(context, plot, rows, series, now - window.durationMillis, now, mouseX, mouseY)
    }

    private fun priceRows(
        product: SkysoftBazaarDepthProduct?,
        window: BazaarGraphWindow,
        maximumPoints: Int,
        now: Long,
    ): List<SkysoftBazaarPriceSnapshot> {
        return rowsCache.value(product, PriceRowsCriteria(window, maximumPoints), now) {
            val cutoff = now - window.durationMillis
            compactBazaarPriceHistory(
                product?.priceHistory.orEmpty()
                    .filter { it.at >= cutoff }
                    .sortedBy(SkysoftBazaarPriceSnapshot::at),
                maximumPoints,
            )
        }
    }

    private fun visibleSeries(preferences: ItemListSourcesConfig): List<BazaarPriceSeries> = buildList {
        if (preferences.showBazaarBuyData) {
            add(BazaarPriceSeries.INSTANT_BUY)
            add(BazaarPriceSeries.BUY_ORDER)
        }
        if (preferences.showBazaarSellData) {
            add(BazaarPriceSeries.INSTANT_SELL)
            add(BazaarPriceSeries.SELL_ORDER)
        }
    }

    private fun pricePlot(bounds: Rect, prices: List<Double>): BazaarPricePlot {
        val minimum = prices.min()
        val maximum = prices.max()
        val padding = if (maximum > minimum) {
            (maximum - minimum) * PriceHistoryLayout.RANGE_PADDING
        } else {
            maxOf(abs(minimum) * PriceHistoryLayout.FLAT_PADDING, PriceHistoryLayout.MINIMUM_PADDING)
        }
        return BazaarPricePlot(
            bounds = Rect(
                bounds.x + PriceHistoryLayout.PLOT_INSET,
                bounds.y + PriceHistoryLayout.LEGEND_HEIGHT,
                (bounds.width - PriceHistoryLayout.PLOT_INSET * 2).coerceAtLeast(1),
                (bounds.height - PriceHistoryLayout.LEGEND_HEIGHT - PriceHistoryLayout.PLOT_INSET).coerceAtLeast(1),
            ),
            minimumPrice = minimum - padding,
            maximumPrice = maximum + padding,
        )
    }

    private fun renderLegend(
        context: GuiGraphicsExtractor,
        bounds: Rect,
        series: List<BazaarPriceSeries>,
    ) {
        series.forEach { entry ->
            val column = entry.legendIndex % PriceHistoryLayout.LEGEND_COLUMNS
            val row = entry.legendIndex / PriceHistoryLayout.LEGEND_COLUMNS
            val x = bounds.x + PriceHistoryLayout.PLOT_INSET + column * bounds.width / PriceHistoryLayout.LEGEND_COLUMNS
            val y = bounds.y + PriceHistoryLayout.LEGEND_Y + row * PriceHistoryLayout.LEGEND_ROW_HEIGHT
            context.fill(
                x,
                y + PriceHistoryLayout.LEGEND_SWATCH_Y,
                x + PriceHistoryLayout.LEGEND_SWATCH,
                y + PriceHistoryLayout.LEGEND_SWATCH_Y + PriceHistoryLayout.LEGEND_SWATCH,
                entry.color,
            )
            LegacyTextRenderer.draw(context, entry.coloredLabel, x + PriceHistoryLayout.LEGEND_TEXT_GAP, y)
        }
    }

    private fun renderGrid(context: GuiGraphicsExtractor, plot: BazaarPricePlot) {
        val middleY = plot.bounds.y + plot.bounds.height / PriceHistoryLayout.HALF
        context.fill(
            plot.bounds.x,
            middleY,
            plot.bounds.x + plot.bounds.width,
            middleY + 1,
            PriceHistoryStyle.GRID,
        )
    }

    private fun renderSeries(
        context: GuiGraphicsExtractor,
        plot: BazaarPricePlot,
        rows: List<SkysoftBazaarPriceSnapshot>,
        series: BazaarPriceSeries,
        start: Long,
        end: Long,
    ) {
        val points = rows.mapNotNull { row ->
            series.value(row).takeIf { it > 0.0 }?.let { price -> pricePoint(plot, row.at, price, start, end) }
        }
        points.zipWithNext().forEach { (first, second) ->
            GuiLineRenderer.drawStep(context, first.first, first.second, second.first, second.second, series.color)
        }
        points.forEach { point ->
            context.fill(
                point.first - PriceHistoryLayout.DOT_RADIUS,
                point.second - PriceHistoryLayout.DOT_RADIUS,
                point.first + PriceHistoryLayout.DOT_RADIUS + 1,
                point.second + PriceHistoryLayout.DOT_RADIUS + 1,
                series.color,
            )
        }
    }

    private fun renderHover(
        context: GuiGraphicsExtractor,
        plot: BazaarPricePlot,
        rows: List<SkysoftBazaarPriceSnapshot>,
        series: List<BazaarPriceSeries>,
        start: Long,
        end: Long,
        mouseX: Int,
        mouseY: Int,
    ) {
        if (!plot.bounds.contains(mouseX, mouseY) || rows.isEmpty()) return
        val progress = ((mouseX - plot.bounds.x).toDouble() / plot.bounds.width).coerceIn(0.0, 1.0)
        val hoveredAt = start + ((end - start) * progress).roundToInt()
        val row = rows.minByOrNull { abs(it.at - hoveredAt) } ?: return
        val x = pricePoint(plot, row.at, plot.minimumPrice, start, end).first
        context.fill(x, plot.bounds.y, x + 1, plot.bounds.y + plot.bounds.height, PriceHistoryStyle.CROSSHAIR)
        val tooltip = buildList {
            add("§f${BazaarPriceSeries.TIME_FORMAT.format(Instant.ofEpochMilli(row.at))}")
            series.forEach { entry ->
                val price = entry.value(row)
                if (price > 0.0) add("${entry.coloredLabel}: §6${price.coinAmountFormat()} coins")
            }
        }
        SkysoftNativeTooltip.setForNextFrame(context, tooltip, mouseX, mouseY)
    }

    private fun pricePoint(
        plot: BazaarPricePlot,
        at: Long,
        price: Double,
        start: Long,
        end: Long,
    ): Pair<Int, Int> {
        val xProgress = ((at - start).toDouble() / (end - start)).coerceIn(0.0, 1.0)
        val yProgress = ((price - plot.minimumPrice) / plot.priceRange).coerceIn(0.0, 1.0)
        return plot.bounds.x + (plot.bounds.width * xProgress).roundToInt() to
            plot.bounds.y + plot.bounds.height - (plot.bounds.height * yProgress).roundToInt()
    }

    private fun renderMessage(context: GuiGraphicsExtractor, bounds: Rect, message: String) {
        LegacyTextRenderer.draw(
            context,
            message,
            bounds.x + PriceHistoryLayout.MESSAGE_INSET,
            bounds.y + PriceHistoryLayout.MESSAGE_INSET,
        )
    }
}

internal fun compactBazaarPriceHistory(
    rows: List<SkysoftBazaarPriceSnapshot>,
    maximumPoints: Int,
): List<SkysoftBazaarPriceSnapshot> {
    require(maximumPoints > 1) { "Maximum Bazaar price points must be greater than one" }
    if (rows.size <= maximumPoints) return rows
    val bucketSize = ceil((rows.size - 1).toDouble() / (maximumPoints - 1)).toInt()
    return buildList {
        add(rows.first())
        rows.drop(1).chunked(bucketSize).forEach { add(it.last()) }
    }
}

private data class BazaarPricePlot(
    val bounds: Rect,
    val minimumPrice: Double,
    val maximumPrice: Double,
) {
    val priceRange = maximumPrice - minimumPrice
}

private data class PriceRowsCriteria(
    val window: BazaarGraphWindow,
    val maximumPoints: Int,
)

private enum class BazaarPriceSeries(
    val coloredLabel: String,
    val color: Int,
    val legendIndex: Int,
    val value: (SkysoftBazaarPriceSnapshot) -> Double,
) {
    INSTANT_BUY("§bInstant Buy", 0xFF55FFFF.toInt(), 0, SkysoftBazaarPriceSnapshot::instantBuyPrice),
    INSTANT_SELL("§6Instant Sell", 0xFFFFAA00.toInt(), 2, SkysoftBazaarPriceSnapshot::instantSellPrice),
    BUY_ORDER("§aBuy Orders", 0xFF55FF55.toInt(), 1, SkysoftBazaarPriceSnapshot::buyOrderPrice),
    SELL_ORDER("§cSell Orders", 0xFFFF5555.toInt(), 3, SkysoftBazaarPriceSnapshot::sellOrderPrice),
    ;

    companion object {
        val TIME_FORMAT = java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss")
            .withZone(java.time.ZoneId.systemDefault())
    }
}

private object PriceHistoryLayout {
    const val PLOT_INSET = 4
    const val LEGEND_HEIGHT = 22
    const val LEGEND_Y = 2
    const val LEGEND_COLUMNS = 2
    const val LEGEND_ROW_HEIGHT = 10
    const val LEGEND_SWATCH_Y = 2
    const val LEGEND_SWATCH = 5
    const val LEGEND_TEXT_GAP = 8
    const val MESSAGE_INSET = 5
    const val DOT_RADIUS = 1
    const val POINT_SPACING = 8
    const val MIN_POINTS = 24
    const val MAX_POINTS = 64
    const val CACHE_MILLIS = 1_000L
    const val RANGE_PADDING = 0.08
    const val FLAT_PADDING = 0.02
    const val MINIMUM_PADDING = 1.0
    const val HALF = 2
}

private object PriceHistoryStyle {
    const val GRID = 0x5048515A
    val CROSSHAIR = 0xA0FFFFFF.toInt()
}
