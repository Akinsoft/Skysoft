package com.skysoft.features.inventory.itemlist

import com.skysoft.config.ItemListSourcesConfig
import com.skysoft.data.ProfileStorage
import com.skysoft.data.skyblock.price.SkysoftBazaarDepthProduct
import com.skysoft.data.skyblock.price.SkysoftBazaarDepthRow
import com.skysoft.data.skyblock.price.SkysoftBazaarFlowDelta
import com.skysoft.features.bazaar.BazaarInvestmentPosition
import com.skysoft.gui.tooltip.SkysoftNativeTooltip
import com.skysoft.utils.NumberUtilities.coinAmountFormat
import com.skysoft.utils.IdentityRefreshCache
import com.skysoft.utils.NumberUtilities.coinFormat
import com.skysoft.utils.gui.Rect
import com.skysoft.utils.render.GuiLineRenderer
import com.skysoft.utils.render.LegacyTextRenderer
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt
import net.minecraft.client.gui.GuiGraphicsExtractor

internal object BazaarGraphPlotRenderer {
    private val tradeVolumeRowsCache = IdentityRefreshCache<TradeVolumeRowsCriteria, BazaarTradeVolumeRows>(
        PlotLayout.TRADE_VOLUME_CACHE_MILLIS,
    )
    private var depthPlotCache = BazaarDepthPlotCache()

    fun renderPrice(
        context: GuiGraphicsExtractor,
        bounds: Rect,
        product: SkysoftBazaarDepthProduct?,
        investment: BazaarInvestmentPosition?,
        preferences: ItemListSourcesConfig,
        mouseX: Int,
        mouseY: Int,
    ) {
        if (!preferences.showBazaarBuyData && !preferences.showBazaarSellData) {
            LegacyTextRenderer.draw(
                context,
                "§8No price filters selected",
                bounds.x + PlotLayout.MESSAGE_INSET,
                bounds.y + PlotLayout.MESSAGE_INSET,
            )
            return
        }
        val plot = depthPlot(
            bounds,
            product,
            preferences.showBazaarBuyData,
            preferences.showBazaarSellData,
        )
        if (plot == null) {
            LegacyTextRenderer.draw(
                context,
                "§8No active Bazaar orders",
                bounds.x + PlotLayout.MESSAGE_INSET,
                bounds.y + PlotLayout.MESSAGE_INSET,
            )
            return
        }
        val investmentCost = investment?.averageCost?.takeIf { preferences.showBazaarPlayerData }
        renderDepthGrid(context, plot)
        renderDepthSide(
            context,
            plot.buyPoints,
            plot.layout.baselineY,
            PlotStyle.BUY,
            PlotStyle.BUY_FILL,
            "Buy",
            mouseX,
            mouseY,
        )
        renderDepthSide(
            context,
            plot.sellPoints,
            plot.layout.baselineY,
            PlotStyle.SELL,
            PlotStyle.SELL_FILL,
            "Sell",
            mouseX,
            mouseY,
        )
        renderInvestmentLine(context, plot, investment, investmentCost, mouseX, mouseY)
        renderSpread(context, plot, mouseX, mouseY)
        renderDepthLegend(context, plot)
        renderDepthAxis(context, plot)
    }

    private fun depthPlot(
        bounds: Rect,
        product: SkysoftBazaarDepthProduct?,
        showBuyOrders: Boolean,
        showSellOrders: Boolean,
    ): BazaarDepthPlot? {
        val current = depthPlotCache
        if (current.matches(product, bounds, showBuyOrders, showSellOrders)) return current.plot
        return buildBazaarDepthPlot(bounds, product, showBuyOrders, showSellOrders).also { plot ->
            depthPlotCache = BazaarDepthPlotCache(product, bounds, showBuyOrders, showSellOrders, plot)
        }
    }

    fun renderTradeVolume(
        context: GuiGraphicsExtractor,
        bounds: Rect,
        product: SkysoftBazaarDepthProduct?,
        transactions: List<ProfileStorage.BazaarTransactionData>,
        preferences: ItemListSourcesConfig,
        mouseX: Int,
        mouseY: Int,
    ) {
        val now = System.currentTimeMillis()
        val window = preferences.graphWindow()
        val maximumPoints = (bounds.width / PlotLayout.TRADE_VOLUME_POINT_SPACING).coerceIn(
            PlotLayout.MIN_TRADE_VOLUME_POINTS,
            PlotLayout.MAX_TRADE_VOLUME_POINTS,
        )
        val rowSnapshot = tradeVolumeRows(product, window, maximumPoints, now)
        val cutoff = rowSnapshot.cutoff
        val rows = rowSnapshot.rows
        val visibleTransactions = transactions
            .filter { preferences.showBazaarPlayerData && it.atMillis >= cutoff }
        if (rows.isEmpty() && visibleTransactions.isEmpty()) {
            LegacyTextRenderer.draw(
                context,
                "§8No Bazaar trade volume in this range",
                bounds.x + PlotLayout.MESSAGE_INSET,
                bounds.y + PlotLayout.MESSAGE_INSET,
            )
            return
        }
        if (!preferences.showBazaarBuyData && !preferences.showBazaarSellData && visibleTransactions.isEmpty()) {
            LegacyTextRenderer.draw(
                context,
                "§8No volume filters selected",
                bounds.x + PlotLayout.MESSAGE_INSET,
                bounds.y + PlotLayout.MESSAGE_INSET,
            )
            return
        }
        val maximum = maxOf(
            rows.maxOfOrNull { if (preferences.showBazaarBuyData) it.buy else 0.0 } ?: 0.0,
            rows.maxOfOrNull { if (preferences.showBazaarSellData) it.sell else 0.0 } ?: 0.0,
            visibleTransactions.maxOfOrNull { it.amount.toDouble() } ?: 0.0,
            1.0,
        )
        val end = now.coerceAtLeast(cutoff + 1L)
        if (preferences.showBazaarBuyData) {
            renderTradeVolumeSide(context, bounds, rows, cutoff, end, maximum, true, mouseX, mouseY)
        }
        if (preferences.showBazaarSellData) {
            renderTradeVolumeSide(context, bounds, rows, cutoff, end, maximum, false, mouseX, mouseY)
        }
        renderPlayerTrades(context, bounds, visibleTransactions, cutoff, end, maximum, mouseX, mouseY)
    }

    private fun tradeVolumeRows(
        product: SkysoftBazaarDepthProduct?,
        window: BazaarGraphWindow,
        maximumPoints: Int,
        now: Long,
    ): BazaarTradeVolumeRows =
        tradeVolumeRowsCache.value(product, TradeVolumeRowsCriteria(window, maximumPoints), now) {
            val cutoff = now - window.durationMillis
            BazaarTradeVolumeRows(
                cutoff = cutoff,
                rows = compactBazaarTradeVolumeRows(
                    product?.flowDeltas.orEmpty().filter { it.at >= cutoff }.sortedBy(SkysoftBazaarFlowDelta::at),
                    maximumPoints,
                ),
            )
        }

    fun renderInvestment(
        context: GuiGraphicsExtractor,
        bounds: Rect,
        investment: BazaarInvestmentPosition?,
        exitPrice: Double,
        isVisible: Boolean,
    ) {
        if (!isVisible || investment == null) return
        val currentValue = exitPrice.takeIf { it > 0.0 }?.times(investment.amount)
        val profit = currentValue?.minus(investment.investedCoins)
        val valueText = if (currentValue == null) "no exit price" else "value ${currentValue.coinAmountFormat()}"
        val profitText = if (profit == null) "" else " ${if (profit >= 0.0) "§a+" else "§c"}${profit.coinAmountFormat()}"
        LegacyTextRenderer.draw(
            context,
            "§6Tracked ${ItemListFormatting.number(investment.amount)} §8| §7cost ${investment.investedCoins.coinAmountFormat()} " +
                "§8| §7$valueText$profitText",
            bounds.x + PlotLayout.PANEL_INSET,
            bounds.y + PlotLayout.INVESTMENT_Y,
        )
    }

    fun renderFailure(
        context: GuiGraphicsExtractor,
        bounds: Rect,
        error: String?,
        mouseX: Int,
        mouseY: Int,
    ) {
        LegacyTextRenderer.draw(
            context,
            "§cBazaar graph unavailable",
            bounds.x + PlotLayout.MESSAGE_INSET,
            bounds.y + PlotLayout.MESSAGE_INSET,
        )
        if (bounds.contains(mouseX, mouseY) && error != null) {
            SkysoftNativeTooltip.setForNextFrame(context, listOf("§c$error"), mouseX, mouseY)
        }
    }

    private fun renderDepthSide(
        context: GuiGraphicsExtractor,
        points: List<DepthGraphPoint>,
        baselineY: Int,
        color: Int,
        fillColor: Int,
        label: String,
        mouseX: Int,
        mouseY: Int,
    ) {
        points.zipWithNext().forEach { (first, second) ->
            val left = minOf(first.x, second.x)
            val right = maxOf(first.x, second.x) + 1
            if (right > left && first.y < baselineY) {
                context.fill(left, first.y, right, baselineY, fillColor)
            }
            GuiLineRenderer.drawStep(context, first.x, first.y, second.x, second.y, color)
        }
        val depthPoints = points.filter { it.row != null }
        val hovered = depthPoints.minByOrNull { abs(mouseX - it.x) } ?: return
        val sideRange = depthPoints.minOf(DepthGraphPoint::x)..depthPoints.maxOf(DepthGraphPoint::x)
        if (mouseX !in sideRange || mouseY !in hovered.y..baselineY) return
        renderDepthTooltip(context, hovered, label, mouseX, mouseY)
    }

    private fun renderDepthTooltip(
        context: GuiGraphicsExtractor,
        point: DepthGraphPoint,
        label: String,
        mouseX: Int,
        mouseY: Int,
    ) {
        val row = requireNotNull(point.row)
        SkysoftNativeTooltip.setForNextFrame(
            context,
            listOf(
                "${if (label == "Buy") "§aBuy order depth" else "§cSell order depth"}",
                "§7Price: §6${row.pricePerUnit.coinAmountFormat()} coins",
                "§7Amount: §f${ItemListFormatting.number(row.amount)}",
                "§7Orders: §f${ItemListFormatting.number(row.orders)}",
                "§7Cumulative: §f${ItemListFormatting.number(point.cumulative)}",
            ),
            mouseX,
            mouseY,
        )
    }

    private fun renderInvestmentLine(
        context: GuiGraphicsExtractor,
        plot: BazaarDepthPlot,
        investment: BazaarInvestmentPosition?,
        cost: Double?,
        mouseX: Int,
        mouseY: Int,
    ) {
        if (cost == null || investment == null) return
        if (cost !in plot.minimumPrice..plot.maximumPrice) return
        val x = depthPriceX(plot.layout, cost, plot.minimumPrice, plot.priceRange)
        GuiLineRenderer.drawStep(
            context,
            x,
            plot.layout.plotTop,
            x,
            plot.layout.baselineY,
            PlotStyle.INVESTMENT,
        )
        if (mouseY !in plot.layout.plotTop..plot.layout.baselineY ||
            abs(mouseX - x) > PlotLayout.INVESTMENT_HIT_RADIUS
        ) return
        SkysoftNativeTooltip.setForNextFrame(
            context,
            listOf(
                "§6Tracked purchase cost",
                "§7Average: §f${cost.coinAmountFormat()} coins",
                "§7Amount: §f${ItemListFormatting.number(investment.amount)}",
            ),
            mouseX,
            mouseY,
        )
    }

    private fun renderTradeVolumeSide(
        context: GuiGraphicsExtractor,
        bounds: Rect,
        rows: List<SkysoftBazaarFlowDelta>,
        start: Long,
        end: Long,
        maximum: Double,
        isBuy: Boolean,
        mouseX: Int,
        mouseY: Int,
    ) {
        val color = if (isBuy) PlotStyle.BUY else PlotStyle.SELL
        val points = rows.map { row ->
            tradeVolumePoint(bounds, row.at, if (isBuy) row.buy else row.sell, maximum, start, end)
        }
        points.zipWithNext().forEach { (first, second) ->
            GuiLineRenderer.drawStep(context, first.first, first.second, second.first, second.second, color)
        }
        points.forEachIndexed { index, point ->
            drawGraphDot(context, point.first, point.second, color)
            if (abs(mouseX - point.first) <= PlotLayout.HIT_RADIUS && abs(mouseY - point.second) <= PlotLayout.HIT_RADIUS) {
                renderTradeVolumeTooltip(context, rows[index], isBuy, mouseX, mouseY)
            }
        }
    }

    private fun renderTradeVolumeTooltip(
        context: GuiGraphicsExtractor,
        row: SkysoftBazaarFlowDelta,
        isBuy: Boolean,
        mouseX: Int,
        mouseY: Int,
    ) {
        val value = if (isBuy) row.buy else row.sell
        SkysoftNativeTooltip.setForNextFrame(
            context,
            listOf(
                "§f${PlotStyle.TIME_FORMAT.format(Instant.ofEpochMilli(row.at))}",
                "${if (isBuy) "§aBuy" else "§cSell"} volume: §f${ItemListFormatting.number(value.roundToInt().toLong())}",
            ),
            mouseX,
            mouseY,
        )
    }

    private fun renderPlayerTrades(
        context: GuiGraphicsExtractor,
        bounds: Rect,
        transactions: List<ProfileStorage.BazaarTransactionData>,
        start: Long,
        end: Long,
        maximum: Double,
        mouseX: Int,
        mouseY: Int,
    ) {
        val points = transactions.map { transaction ->
            TransactionGraphPoint(
                tradeVolumePoint(bounds, transaction.atMillis, transaction.amount.toDouble(), maximum, start, end),
                transaction,
            )
        }
        points.forEach { point ->
            val color = transactionColor(point.transaction.type)
            drawTransactionDot(context, point.point.first, point.point.second, color)
        }
        val hovered = points
            .filter { abs(mouseX - it.point.first) <= PlotLayout.TRANSACTION_HIT_RADIUS }
            .filter { abs(mouseY - it.point.second) <= PlotLayout.TRANSACTION_HIT_RADIUS }
            .minByOrNull { abs(mouseX - it.point.first) + abs(mouseY - it.point.second) }
            ?: return
        renderTransactionTooltip(context, hovered.transaction, mouseX, mouseY)
    }

    private fun renderTransactionTooltip(
        context: GuiGraphicsExtractor,
        transaction: ProfileStorage.BazaarTransactionData,
        mouseX: Int,
        mouseY: Int,
    ) {
        SkysoftNativeTooltip.setForNextFrame(
            context,
            listOf(
                "§f${transaction.type.displayName}",
                "§7Time: §f${PlotStyle.TIME_FORMAT.format(Instant.ofEpochMilli(transaction.atMillis))}",
                "§7Quantity: §f${ItemListFormatting.number(transaction.amount)}",
                "§7Price each: §6${transaction.pricePerUnit().coinAmountFormat()} coins",
                "§7Total: §6${transaction.totalCoins.coinAmountFormat()} coins",
            ),
            mouseX,
            mouseY,
        )
    }

    private fun tradeVolumePoint(
        bounds: Rect,
        at: Long,
        value: Double,
        maximum: Double,
        start: Long,
        end: Long,
    ): Pair<Int, Int> {
        val xProgress = ((at - start).toDouble() / (end - start)).coerceIn(0.0, 1.0)
        val yProgress = (value / maximum).coerceIn(0.0, 1.0)
        return bounds.x + PlotLayout.POINT_INSET +
            ((bounds.width - PlotLayout.POINT_INSET * 2) * xProgress).roundToInt() to
            bounds.y + bounds.height - PlotLayout.POINT_INSET -
            ((bounds.height - PlotLayout.POINT_INSET * 2) * yProgress).roundToInt()
    }

    private fun drawGraphDot(context: GuiGraphicsExtractor, x: Int, y: Int, color: Int) {
        context.fill(
            x - PlotLayout.DOT_START,
            y - PlotLayout.DOT_START,
            x + PlotLayout.DOT_END,
            y + PlotLayout.DOT_END,
            color,
        )
    }

    private fun drawTransactionDot(context: GuiGraphicsExtractor, x: Int, y: Int, color: Int) {
        context.fill(
            x - PlotLayout.TRANSACTION_DOT_RADIUS,
            y - PlotLayout.TRANSACTION_DOT_RADIUS,
            x + PlotLayout.TRANSACTION_DOT_RADIUS + 1,
            y + PlotLayout.TRANSACTION_DOT_RADIUS + 1,
            PlotStyle.TRANSACTION_OUTLINE,
        )
        context.fill(x - 1, y - 1, x + 2, y + 2, color)
    }

    private fun transactionColor(type: ProfileStorage.BazaarTransactionType): Int = when (type) {
        ProfileStorage.BazaarTransactionType.INSTANT_BUY -> PlotStyle.INSTANT_BUY
        ProfileStorage.BazaarTransactionType.INSTANT_SELL -> PlotStyle.INSTANT_SELL
        ProfileStorage.BazaarTransactionType.BUY_ORDER -> PlotStyle.BUY_ORDER
        ProfileStorage.BazaarTransactionType.SELL_ORDER -> PlotStyle.SELL_ORDER
    }
}

internal fun compactBazaarTradeVolumeRows(
    rows: List<SkysoftBazaarFlowDelta>,
    maximumPoints: Int,
): List<SkysoftBazaarFlowDelta> {
    require(maximumPoints > 0) { "Maximum Bazaar trade volume points must be positive" }
    if (rows.size <= maximumPoints) return rows
    val bucketSize = ceil(rows.size.toDouble() / maximumPoints).toInt()
    return rows.chunked(bucketSize).map { bucket ->
        SkysoftBazaarFlowDelta(
            at = bucket.last().at,
            buy = bucket.sumOf(SkysoftBazaarFlowDelta::buy),
            sell = bucket.sumOf(SkysoftBazaarFlowDelta::sell),
        )
    }
}

private fun renderDepthGrid(context: GuiGraphicsExtractor, plot: BazaarDepthPlot) {
    val layout = plot.layout
    repeat(PlotLayout.DEPTH_GRID_DIVISIONS) { index ->
        val division = index + 1
        val y = layout.baselineY - layout.plotHeight * division / PlotLayout.DEPTH_GRID_DIVISIONS
        drawDashedHorizontal(context, layout.leftX, layout.rightX, y, PlotStyle.GRID)
    }
    context.fill(layout.leftX - 1, layout.plotTop, layout.leftX, layout.baselineY + 1, PlotStyle.AXIS)
    context.fill(layout.leftX, layout.baselineY, layout.rightX + 1, layout.baselineY + 1, PlotStyle.AXIS)
}

private fun renderDepthLegend(context: GuiGraphicsExtractor, plot: BazaarDepthPlot) {
    val layout = plot.layout
    if (!layout.hasDetails) return
    LegacyTextRenderer.draw(context, "§7Cumulative", layout.quantityLabelX, layout.legendY)
    LegacyTextRenderer.draw(
        context,
        "§7quantity",
        layout.quantityLabelX,
        layout.legendY + PlotLayout.QUANTITY_LABEL_LINE_HEIGHT,
    )
    val buyX = layout.leftX + PlotLayout.LEGEND_SIDE_INSET
    context.fill(
        buyX,
        layout.legendY + PlotLayout.LEGEND_SWATCH_Y,
        buyX + PlotLayout.LEGEND_SWATCH,
        layout.legendY + PlotLayout.LEGEND_SWATCH_Y + PlotLayout.LEGEND_SWATCH,
        PlotStyle.BUY,
    )
    LegacyTextRenderer.draw(context, "§aBuy orders", buyX + PlotLayout.LEGEND_TEXT_GAP, layout.legendY)
    val sellText = "§cSell orders"
    val sellTextX = layout.rightX - LegacyTextRenderer.width(sellText)
    context.fill(
        sellTextX - PlotLayout.LEGEND_TEXT_GAP,
        layout.legendY + PlotLayout.LEGEND_SWATCH_Y,
        sellTextX - PlotLayout.LEGEND_TEXT_GAP + PlotLayout.LEGEND_SWATCH,
        layout.legendY + PlotLayout.LEGEND_SWATCH_Y + PlotLayout.LEGEND_SWATCH,
        PlotStyle.SELL,
    )
    LegacyTextRenderer.draw(context, sellText, sellTextX, layout.legendY)
}

private fun renderSpread(
    context: GuiGraphicsExtractor,
    plot: BazaarDepthPlot,
    mouseX: Int,
    mouseY: Int,
) {
    val layout = plot.layout
    val bestBid = plot.bestBid ?: return
    val bestAsk = plot.bestAsk ?: return
    if (!layout.hasDetails || bestAsk <= bestBid) return
    val bidX = depthPriceX(layout, bestBid, plot.minimumPrice, plot.priceRange)
    val askX = depthPriceX(layout, bestAsk, plot.minimumPrice, plot.priceRange)
    val markerY = layout.plotTop + layout.plotHeight / PlotLayout.HALF
    drawDashedVertical(context, bidX, markerY, layout.baselineY, PlotStyle.SPREAD)
    drawDashedVertical(context, askX, markerY, layout.baselineY, PlotStyle.SPREAD)
    context.fill(bidX, markerY, askX + 1, markerY + 1, PlotStyle.SPREAD)
    context.fill(
        bidX,
        markerY - PlotLayout.SPREAD_TICK_TOP,
        bidX + 1,
        markerY + PlotLayout.SPREAD_TICK_BOTTOM,
        PlotStyle.SPREAD,
    )
    context.fill(
        askX,
        markerY - PlotLayout.SPREAD_TICK_TOP,
        askX + 1,
        markerY + PlotLayout.SPREAD_TICK_BOTTOM,
        PlotStyle.SPREAD,
    )
    drawCenteredWithin(context, "§7Spread", bidX, askX, markerY - PlotLayout.SPREAD_LABEL_OFFSET)
    if (mouseX !in bidX..askX || abs(mouseY - markerY) > PlotLayout.SPREAD_HIT_RADIUS) return
    val spread = bestAsk - bestBid
    val midpoint = (bestAsk + bestBid) / PlotLayout.HALF
    val percent = spread / midpoint * PlotLayout.PERCENT_SCALE
    SkysoftNativeTooltip.setForNextFrame(
        context,
        listOf(
            "§fBid-ask spread",
            "§7Difference: §6${spread.coinAmountFormat()} coins",
            "§7Spread: §f${String.format(Locale.US, "%.2f", percent)}%",
        ),
        mouseX,
        mouseY,
    )
}

private fun renderDepthAxis(context: GuiGraphicsExtractor, plot: BazaarDepthPlot) {
    val layout = plot.layout
    if (!layout.hasDetails) return
    repeat(PlotLayout.DEPTH_GRID_DIVISIONS + 1) { index ->
        val amount = plot.maximumAmount * index / PlotLayout.DEPTH_GRID_DIVISIONS
        val y = layout.baselineY - layout.plotHeight * index / PlotLayout.DEPTH_GRID_DIVISIONS
        val text = ItemListFormatting.compactNumber(amount)
        LegacyTextRenderer.draw(
            context,
            "§7$text",
            layout.leftX - PlotLayout.QUANTITY_AXIS_GAP - LegacyTextRenderer.width(text),
            y - PlotLayout.AXIS_TEXT_CENTER_OFFSET,
        )
        context.fill(
            layout.leftX - PlotLayout.AXIS_TICK_LENGTH,
            y,
            layout.leftX,
            y + 1,
            PlotStyle.AXIS,
        )
    }
    val priceY = layout.baselineY + PlotLayout.AXIS_PRICE_Y
    if (layout.showEndpointPrices) {
        LegacyTextRenderer.draw(context, "§7${plot.minimumPrice.coinFormat()}", layout.leftX, priceY)
        val maximumText = "§7${plot.maximumPrice.coinFormat()}"
        LegacyTextRenderer.draw(
            context,
            maximumText,
            layout.rightX - LegacyTextRenderer.width(maximumText),
            priceY,
        )
    }
    val centerX = (layout.leftX + layout.rightX) / PlotLayout.HALF
    plot.bestBid?.let { bestBid ->
        drawCenteredWithin(context, "§a${bestBid.coinFormat()}", layout.leftX, centerX, priceY)
        drawCenteredWithin(
            context,
            "§aBest bid",
            layout.leftX,
            centerX,
            layout.baselineY + PlotLayout.AXIS_LABEL_Y,
        )
    }
    plot.bestAsk?.let { bestAsk ->
        drawCenteredWithin(context, "§c${bestAsk.coinFormat()}", centerX, layout.rightX, priceY)
        drawCenteredWithin(
            context,
            "§cBest ask",
            centerX,
            layout.rightX,
            layout.baselineY + PlotLayout.AXIS_LABEL_Y,
        )
    }
    drawCenteredWithin(
        context,
        "§7Price",
        layout.leftX,
        layout.rightX,
        layout.baselineY + PlotLayout.AXIS_TITLE_Y,
    )
}

private fun drawCenteredWithin(context: GuiGraphicsExtractor, text: String, left: Int, right: Int, y: Int) {
    val x = left + ((right - left) - LegacyTextRenderer.width(text)) / PlotLayout.HALF
    LegacyTextRenderer.draw(context, text, x, y)
}

private fun drawDashedHorizontal(context: GuiGraphicsExtractor, left: Int, right: Int, y: Int, color: Int) {
    var x = left
    while (x <= right) {
        context.fill(x, y, minOf(x + PlotLayout.DASH_LENGTH, right + 1), y + 1, color)
        x += PlotLayout.DASH_LENGTH + PlotLayout.DASH_GAP
    }
}

private fun drawDashedVertical(context: GuiGraphicsExtractor, x: Int, top: Int, bottom: Int, color: Int) {
    var y = top
    while (y <= bottom) {
        context.fill(x, y, x + 1, minOf(y + PlotLayout.DASH_LENGTH, bottom + 1), color)
        y += PlotLayout.DASH_LENGTH + PlotLayout.DASH_GAP
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
    val flatPadding = rawMaximum.coerceAtLeast(1.0) * PlotLayout.FLAT_PRICE_PADDING
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
    val hasDetails = bounds.width >= PlotLayout.DETAIL_MIN_WIDTH && bounds.height >= PlotLayout.DETAIL_MIN_HEIGHT
    val leftX = bounds.x + if (hasDetails) PlotLayout.QUANTITY_AXIS_WIDTH else PlotLayout.POINT_INSET
    val rightX = bounds.x + bounds.width - PlotLayout.POINT_INSET
    val plotTop = bounds.y + if (hasDetails) PlotLayout.DETAIL_TOP_INSET else PlotLayout.POINT_INSET
    val baselineY = bounds.y + bounds.height - if (hasDetails) PlotLayout.DETAIL_BOTTOM_INSET else PlotLayout.POINT_INSET
    return BazaarDepthPlotLayout(
        leftX = leftX,
        rightX = rightX,
        plotTop = plotTop,
        baselineY = baselineY.coerceAtLeast(plotTop + 1),
        legendY = bounds.y + PlotLayout.LEGEND_Y,
        quantityLabelX = bounds.x + PlotLayout.POINT_INSET,
        hasDetails = hasDetails,
        showEndpointPrices = hasDetails && bounds.width >= PlotLayout.ENDPOINT_PRICE_MIN_WIDTH,
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
)

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

private data class TransactionGraphPoint(
    val point: Pair<Int, Int>,
    val transaction: ProfileStorage.BazaarTransactionData,
)

private data class TradeVolumeRowsCriteria(
    val window: BazaarGraphWindow,
    val maximumPoints: Int,
)

private data class BazaarTradeVolumeRows(
    val cutoff: Long,
    val rows: List<SkysoftBazaarFlowDelta>,
)

private data class BazaarDepthPlotCache(
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

private object PlotLayout {
    const val PANEL_INSET = 6
    const val INVESTMENT_Y = 66
    const val MESSAGE_INSET = 5
    const val POINT_INSET = 4
    const val HIT_RADIUS = 3
    const val INVESTMENT_HIT_RADIUS = 2
    const val DOT_START = 1
    const val DOT_END = 2
    const val TRANSACTION_DOT_RADIUS = 2
    const val TRANSACTION_HIT_RADIUS = 4
    const val TRADE_VOLUME_POINT_SPACING = 8
    const val MIN_TRADE_VOLUME_POINTS = 24
    const val MAX_TRADE_VOLUME_POINTS = 64
    const val TRADE_VOLUME_CACHE_MILLIS = 1_000L
    const val FLAT_PRICE_PADDING = 0.02
    const val DETAIL_MIN_WIDTH = 220
    const val DETAIL_MIN_HEIGHT = 100
    const val ENDPOINT_PRICE_MIN_WIDTH = 340
    const val DETAIL_TOP_INSET = 29
    const val DETAIL_BOTTOM_INSET = 29
    const val QUANTITY_AXIS_WIDTH = 52
    const val QUANTITY_AXIS_GAP = 5
    const val QUANTITY_LABEL_LINE_HEIGHT = 9
    const val AXIS_TICK_LENGTH = 4
    const val LEGEND_Y = 3
    const val LEGEND_SIDE_INSET = 8
    const val LEGEND_SWATCH = 5
    const val LEGEND_SWATCH_Y = 2
    const val LEGEND_TEXT_GAP = 8
    const val DEPTH_GRID_DIVISIONS = 4
    const val DASH_LENGTH = 3
    const val DASH_GAP = 3
    const val SPREAD_HIT_RADIUS = 4
    const val SPREAD_TICK_TOP = 2
    const val SPREAD_TICK_BOTTOM = 3
    const val SPREAD_LABEL_OFFSET = 11
    const val PERCENT_SCALE = 100.0
    const val AXIS_TEXT_CENTER_OFFSET = 4
    const val AXIS_PRICE_Y = 2
    const val AXIS_LABEL_Y = 11
    const val AXIS_TITLE_Y = 20
    const val HALF = 2
}

private object PlotStyle {
    val BUY = 0xFF55FF55.toInt()
    val SELL = 0xFFFF5555.toInt()
    const val BUY_FILL = 0x4055FF55
    const val SELL_FILL = 0x40FF5555
    const val GRID = 0x5048515A
    val AXIS = 0xA0687480.toInt()
    val SPREAD = 0xA0788798.toInt()
    val INVESTMENT = 0xFFFFAA00.toInt()
    val INSTANT_BUY = 0xFF55FFFF.toInt()
    val INSTANT_SELL = 0xFFFF7777.toInt()
    val BUY_ORDER = 0xFF5599FF.toInt()
    val SELL_ORDER = 0xFFFF55FF.toInt()
    val TRANSACTION_OUTLINE = 0xFF101316.toInt()
    val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault())
}
