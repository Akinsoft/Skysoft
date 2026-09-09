package com.skysoft.features.inventory.itemlist

import com.skysoft.data.skyblock.price.SkysoftBazaarDepthProduct
import com.skysoft.data.skyblock.price.SkysoftBazaarDepthRow
import com.skysoft.utils.gui.Rect
import kotlin.math.roundToInt

internal class BazaarDepthPlotCache {
    private var cached = CachedDepthPlot()

    fun plot(
        bounds: Rect,
        product: SkysoftBazaarDepthProduct?,
        showBuyOrders: Boolean,
        showSellOrders: Boolean,
    ): BazaarDepthPlot? {
        val current = cached
        if (current.matches(product, bounds, showBuyOrders, showSellOrders)) return current.plot
        return buildBazaarDepthPlot(bounds, product, showBuyOrders, showSellOrders).also { plot ->
            cached = CachedDepthPlot(product, bounds, showBuyOrders, showSellOrders, plot)
        }
    }
}

internal fun buildBazaarDepthPlot(
    bounds: Rect,
    product: SkysoftBazaarDepthProduct?,
    showBuyOrders: Boolean,
    showSellOrders: Boolean,
): BazaarDepthPlot? {
    val buyRows = product?.sellSummary.orEmpty()
        .filter { showBuyOrders && it.amount > 0L && it.pricePerUnit > 0.0 }
        .sortedByDescending(SkysoftBazaarDepthRow::pricePerUnit)
    val sellRows = product?.buySummary.orEmpty()
        .filter { showSellOrders && it.amount > 0L && it.pricePerUnit > 0.0 }
        .sortedBy(SkysoftBazaarDepthRow::pricePerUnit)
    val prices = (buyRows + sellRows).map(SkysoftBazaarDepthRow::pricePerUnit)
    if (prices.isEmpty()) return null
    val rawMinimum = prices.min()
    val rawMaximum = prices.max()
    val flatPadding = rawMaximum.coerceAtLeast(1.0) * DepthLayout.FLAT_PRICE_PADDING
    val minimum = if (rawMinimum == rawMaximum) rawMinimum - flatPadding else rawMinimum
    val maximum = if (rawMinimum == rawMaximum) rawMaximum + flatPadding else rawMaximum
    val range = maximum - minimum
    val layout = depthPlotLayout(bounds)
    val maximumAmount = maxOf(
        buyRows.sumOf(SkysoftBazaarDepthRow::amount),
        sellRows.sumOf(SkysoftBazaarDepthRow::amount),
        1L,
    )
    val buyPoints = depthPoints(layout, buyRows, maximumAmount, minimum, range)
    val sellPoints = depthPoints(layout, sellRows, maximumAmount, minimum, range)
    return BazaarDepthPlot(
        layout = layout,
        buyPoints = buyPoints,
        sellPoints = sellPoints,
        minimumPrice = minimum,
        maximumPrice = maximum,
        priceRange = range,
        maximumAmount = maximumAmount,
        bestBid = buyRows.firstOrNull()?.pricePerUnit,
        bestAsk = sellRows.firstOrNull()?.pricePerUnit,
    )
}

private fun depthPlotLayout(bounds: Rect): BazaarDepthPlotLayout {
    val hasDetails = bounds.width >= DepthLayout.DETAIL_MIN_WIDTH && bounds.height >= DepthLayout.DETAIL_MIN_HEIGHT
    val leftX = bounds.x + if (hasDetails) DepthLayout.QUANTITY_AXIS_WIDTH else DepthLayout.POINT_INSET
    val rightX = bounds.x + bounds.width - DepthLayout.POINT_INSET
    val plotTop = bounds.y + if (hasDetails) DepthLayout.DETAIL_TOP_INSET else DepthLayout.POINT_INSET
    val baselineY = bounds.y + bounds.height - if (hasDetails) DepthLayout.DETAIL_BOTTOM_INSET else DepthLayout.POINT_INSET
    return BazaarDepthPlotLayout(
        leftX = leftX,
        rightX = rightX,
        plotTop = plotTop,
        baselineY = baselineY.coerceAtLeast(plotTop + 1),
        legendY = bounds.y + DepthLayout.LEGEND_Y,
        quantityLabelX = bounds.x + DepthLayout.POINT_INSET,
        hasDetails = hasDetails,
        showEndpointPrices = hasDetails && bounds.width >= DepthLayout.ENDPOINT_PRICE_MIN_WIDTH,
    )
}

private fun depthPoints(
    layout: BazaarDepthPlotLayout,
    rows: List<SkysoftBazaarDepthRow>,
    maximumAmount: Long,
    minimumPrice: Double,
    priceRange: Double,
): List<DepthGraphPoint> {
    val best = rows.firstOrNull() ?: return emptyList()
    var cumulative = 0L
    return buildList {
        add(
            DepthGraphPoint(
                x = depthPriceX(layout, best.pricePerUnit, minimumPrice, priceRange),
                y = layout.baselineY,
                row = null,
                cumulative = 0L,
            ),
        )
        rows.forEach { row ->
            cumulative += row.amount
            add(
                DepthGraphPoint(
                    x = depthPriceX(layout, row.pricePerUnit, minimumPrice, priceRange),
                    y = depthAmountY(layout, cumulative, maximumAmount),
                    row = row,
                    cumulative = cumulative,
                ),
            )
        }
    }
}

private fun depthPriceX(layout: BazaarDepthPlotLayout, price: Double, minimum: Double, range: Double): Int =
    layout.leftX + (layout.plotWidth * ((price - minimum) / range).coerceIn(0.0, 1.0)).roundToInt()

private fun depthAmountY(layout: BazaarDepthPlotLayout, cumulative: Long, maximum: Long): Int =
    layout.baselineY - (layout.plotHeight * (cumulative.toDouble() / maximum)).roundToInt()

internal data class BazaarDepthPlot(
    val layout: BazaarDepthPlotLayout,
    val buyPoints: List<DepthGraphPoint>,
    val sellPoints: List<DepthGraphPoint>,
    val minimumPrice: Double,
    val maximumPrice: Double,
    val priceRange: Double,
    val maximumAmount: Long,
    val bestBid: Double?,
    val bestAsk: Double?,
) {
    fun priceX(price: Double): Int = depthPriceX(layout, price, minimumPrice, priceRange)
}

internal data class BazaarDepthPlotLayout(
    val leftX: Int,
    val rightX: Int,
    val plotTop: Int,
    val baselineY: Int,
    val legendY: Int,
    val quantityLabelX: Int,
    val hasDetails: Boolean,
    val showEndpointPrices: Boolean,
) {
    val plotWidth: Int get() = (rightX - leftX).coerceAtLeast(1)
    val plotHeight: Int get() = (baselineY - plotTop).coerceAtLeast(1)
}

internal data class DepthGraphPoint(
    val x: Int,
    val y: Int,
    val row: SkysoftBazaarDepthRow?,
    val cumulative: Long,
)

private data class CachedDepthPlot(
    val product: SkysoftBazaarDepthProduct? = null,
    val bounds: Rect? = null,
    val showBuyOrders: Boolean = false,
    val showSellOrders: Boolean = false,
    val plot: BazaarDepthPlot? = null,
) {
    fun matches(
        candidateProduct: SkysoftBazaarDepthProduct?,
        candidateBounds: Rect,
        candidateShowBuyOrders: Boolean,
        candidateShowSellOrders: Boolean,
    ): Boolean =
        product === candidateProduct &&
            bounds == candidateBounds &&
            showBuyOrders == candidateShowBuyOrders &&
            showSellOrders == candidateShowSellOrders
}

private object DepthLayout {
    const val POINT_INSET = 4
    const val FLAT_PRICE_PADDING = 0.02
    const val DETAIL_MIN_WIDTH = 220
    const val DETAIL_MIN_HEIGHT = 100
    const val ENDPOINT_PRICE_MIN_WIDTH = 340
    const val DETAIL_TOP_INSET = 29
    const val DETAIL_BOTTOM_INSET = 29
    const val QUANTITY_AXIS_WIDTH = 52
    const val LEGEND_Y = 3
}
