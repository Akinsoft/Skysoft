package com.skysoft.features.inventory.itemlist

import com.skysoft.config.BazaarGraphWindow
import com.skysoft.config.ItemListSourcesConfig
import com.skysoft.data.ProfileStorage
import com.skysoft.data.ProfileStorageView
import com.skysoft.data.skyblock.price.SkysoftBazaarDepthProduct
import com.skysoft.data.skyblock.price.SkysoftBazaarFlowDelta
import com.skysoft.features.bazaar.BazaarInvestmentPosition
import com.skysoft.gui.tooltip.SkysoftNativeTooltip
import com.skysoft.utils.NumberUtilities.addSeparators
import com.skysoft.utils.NumberUtilities.coinAmountFormat
import com.skysoft.utils.IdentityRefreshCache
import com.skysoft.utils.NumberUtilities.coinFormat
import com.skysoft.utils.NumberUtilities.fixedDecimals
import com.skysoft.utils.gui.Rect
import com.skysoft.utils.render.GuiLineRenderer
import com.skysoft.utils.render.LegacyTextRenderer
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.roundToInt
import net.minecraft.client.gui.GuiGraphicsExtractor

internal object BazaarGraphPlotRenderer {
    private val tradeVolumeRowsCache = IdentityRefreshCache<BazaarGraphWindow, BazaarTradeVolumeRows>(
        PlotLayout.TRADE_VOLUME_CACHE_MILLIS,
    )
    private val depthPlotCache = BazaarDepthPlotCache()

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
            renderBazaarGraphMessage(context, bounds, "§8No price filters selected")
            return
        }
        val plot = depthPlotCache.plot(
            bounds,
            product,
            preferences.showBazaarBuyData,
            preferences.showBazaarSellData,
        )
        if (plot == null) {
            renderBazaarGraphMessage(context, bounds, "§8No active Bazaar orders")
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
            "§aBuy order depth",
            mouseX,
            mouseY,
        )
        renderDepthSide(
            context,
            plot.sellPoints,
            plot.layout.baselineY,
            PlotStyle.SELL,
            PlotStyle.SELL_FILL,
            "§cSell order depth",
            mouseX,
            mouseY,
        )
        renderInvestmentLine(context, plot, investment, investmentCost, mouseX, mouseY)
        renderSpread(context, plot, mouseX, mouseY)
        renderDepthLegend(context, plot)
        renderDepthAxis(context, plot)
    }

    fun renderTradeVolume(
        context: GuiGraphicsExtractor,
        bounds: Rect,
        product: SkysoftBazaarDepthProduct?,
        transactions: List<ProfileStorageView.BazaarTransactionData>,
        preferences: ItemListSourcesConfig,
        mouseX: Int,
        mouseY: Int,
    ) {
        val now = System.currentTimeMillis()
        val window = preferences.graphWindow()
        val rowSnapshot = tradeVolumeRows(product, window, now)
        val cutoff = rowSnapshot.cutoff
        val rows = rowSnapshot.rows
        val visibleTransactions = transactions
            .filter { preferences.showBazaarPlayerData && it.atMillis >= cutoff }
        if (rows.isEmpty() && visibleTransactions.isEmpty()) {
            renderBazaarGraphMessage(context, bounds, "§8No Bazaar trade volume in this range")
            return
        }
        if (!preferences.showBazaarBuyData && !preferences.showBazaarSellData && visibleTransactions.isEmpty()) {
            renderBazaarGraphMessage(context, bounds, "§8No volume filters selected")
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
        now: Long,
    ): BazaarTradeVolumeRows =
        tradeVolumeRowsCache.value(product, window, now) {
            val cutoff = now - window.durationMillis
            BazaarTradeVolumeRows(
                cutoff = cutoff,
                rows = product?.flowDeltas.orEmpty()
                    .filter { it.at >= cutoff }
                    .sortedBy(SkysoftBazaarFlowDelta::at),
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
            "§6Tracked ${investment.amount.addSeparators()} §8| §7cost ${investment.investedCoins.coinAmountFormat()} " +
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
        renderBazaarGraphMessage(context, bounds, "§cBazaar graph unavailable")
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
        tooltipTitle: String,
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
        renderDepthTooltip(context, hovered, tooltipTitle, mouseX, mouseY)
    }

    private fun renderDepthTooltip(
        context: GuiGraphicsExtractor,
        point: DepthGraphPoint,
        tooltipTitle: String,
        mouseX: Int,
        mouseY: Int,
    ) {
        val row = requireNotNull(point.row)
        SkysoftNativeTooltip.setForNextFrame(
            context,
            listOf(
                tooltipTitle,
                "§7Price: §6${row.pricePerUnit.coinAmountFormat()} coins",
                "§7Amount: §f${row.amount.addSeparators()}",
                "§7Orders: §f${row.orders.addSeparators()}",
                "§7Cumulative: §f${point.cumulative.addSeparators()}",
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
        val x = plot.priceX(cost)
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
                "§7Amount: §f${investment.amount.addSeparators()}",
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
            drawBazaarGraphDot(context, point.first, point.second, color)
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
                "§f${BAZAAR_GRAPH_TIME_FORMAT.format(Instant.ofEpochMilli(row.at))}",
                "${if (isBuy) "§aBuy" else "§cSell"} volume: §f${value.roundToInt().addSeparators()}",
            ),
            mouseX,
            mouseY,
        )
    }

    private fun renderPlayerTrades(
        context: GuiGraphicsExtractor,
        bounds: Rect,
        transactions: List<ProfileStorageView.BazaarTransactionData>,
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
        transaction: ProfileStorageView.BazaarTransactionData,
        mouseX: Int,
        mouseY: Int,
    ) {
        SkysoftNativeTooltip.setForNextFrame(
            context,
            listOf(
                "§f${transaction.type.displayName}",
                "§7Time: §f${BAZAAR_GRAPH_TIME_FORMAT.format(Instant.ofEpochMilli(transaction.atMillis))}",
                "§7Quantity: §f${transaction.amount.addSeparators()}",
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

    private fun drawTransactionDot(context: GuiGraphicsExtractor, x: Int, y: Int, color: Int) {
        context.fill(
            x - PlotLayout.TRANSACTION_DOT_RADIUS,
            y - PlotLayout.TRANSACTION_DOT_RADIUS,
            x + PlotLayout.TRANSACTION_DOT_RADIUS + 1,
            y + PlotLayout.TRANSACTION_DOT_RADIUS + 1,
            PlotStyle.TRANSACTION_OUTLINE,
        )
        drawBazaarGraphDot(context, x, y, color)
    }

    private fun transactionColor(type: ProfileStorage.BazaarTransactionType): Int = when (type) {
        ProfileStorage.BazaarTransactionType.INSTANT_BUY -> PlotStyle.INSTANT_BUY
        ProfileStorage.BazaarTransactionType.INSTANT_SELL -> PlotStyle.INSTANT_SELL
        ProfileStorage.BazaarTransactionType.BUY_ORDER -> PlotStyle.BUY_ORDER
        ProfileStorage.BazaarTransactionType.SELL_ORDER -> PlotStyle.SELL_ORDER
    }
}

private fun renderDepthGrid(context: GuiGraphicsExtractor, plot: BazaarDepthPlot) {
    val layout = plot.layout
    repeat(PlotLayout.DEPTH_GRID_DIVISIONS) { index ->
        val division = index + 1
        val y = layout.baselineY - layout.plotHeight * division / PlotLayout.DEPTH_GRID_DIVISIONS
        drawDashedHorizontal(context, layout.leftX, layout.rightX, y, BAZAAR_GRAPH_GRID_COLOR)
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
    drawBazaarGraphLegendEntry(
        context,
        "§aBuy orders",
        layout.leftX + PlotLayout.LEGEND_SIDE_INSET,
        layout.legendY,
        PlotStyle.BUY,
    )
    val sellText = "§cSell orders"
    drawBazaarGraphLegendEntry(
        context,
        sellText,
        layout.rightX - LegacyTextRenderer.width(sellText) - BazaarGraphLayout.TEXT_GAP,
        layout.legendY,
        PlotStyle.SELL,
    )
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
    val bidX = plot.priceX(bestBid)
    val askX = plot.priceX(bestAsk)
    val markerY = layout.plotTop + layout.plotHeight / PlotLayout.HALF
    drawDashedVertical(context, bidX, markerY, layout.baselineY, PlotStyle.SPREAD)
    drawDashedVertical(context, askX, markerY, layout.baselineY, PlotStyle.SPREAD)
    context.fill(bidX, markerY, askX + 1, markerY + 1, PlotStyle.SPREAD)
    listOf(bidX, askX).forEach { tickX ->
        context.fill(
            tickX,
            markerY - PlotLayout.SPREAD_TICK_TOP,
            tickX + 1,
            markerY + PlotLayout.SPREAD_TICK_BOTTOM,
            PlotStyle.SPREAD,
        )
    }
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
            "§7Spread: §f${percent.fixedDecimals(2)}%",
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

internal fun renderBazaarGraphMessage(context: GuiGraphicsExtractor, bounds: Rect, message: String) {
    LegacyTextRenderer.draw(context, message, bounds.x + BazaarGraphLayout.MESSAGE_INSET, bounds.y + BazaarGraphLayout.MESSAGE_INSET)
}

internal fun drawBazaarGraphLegendEntry(context: GuiGraphicsExtractor, text: String, x: Int, y: Int, color: Int) {
    context.fill(
        x,
        y + BazaarGraphLayout.SWATCH_Y,
        x + BazaarGraphLayout.SWATCH_SIZE,
        y + BazaarGraphLayout.SWATCH_Y + BazaarGraphLayout.SWATCH_SIZE,
        color,
    )
    LegacyTextRenderer.draw(context, text, x + BazaarGraphLayout.TEXT_GAP, y)
}

internal fun drawBazaarGraphDot(context: GuiGraphicsExtractor, x: Int, y: Int, color: Int) {
    context.fill(x - 1, y - 1, x + 2, y + 2, color)
}

internal val BAZAAR_GRAPH_TIME_FORMAT: DateTimeFormatter =
    DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault())
internal const val BAZAAR_GRAPH_GRID_COLOR = 0x5048515A

internal object BazaarGraphLayout {
    const val MESSAGE_INSET = 5
    const val SWATCH_SIZE = 5
    const val SWATCH_Y = 2
    const val TEXT_GAP = 8
}

private data class TransactionGraphPoint(
    val point: Pair<Int, Int>,
    val transaction: ProfileStorageView.BazaarTransactionData,
)

private data class BazaarTradeVolumeRows(
    val cutoff: Long,
    val rows: List<SkysoftBazaarFlowDelta>,
)

private object PlotLayout {
    const val PANEL_INSET = 6
    const val INVESTMENT_Y = 66
    const val POINT_INSET = 4
    const val HIT_RADIUS = 3
    const val INVESTMENT_HIT_RADIUS = 2
    const val TRANSACTION_DOT_RADIUS = 2
    const val TRANSACTION_HIT_RADIUS = 4
    const val TRADE_VOLUME_CACHE_MILLIS = 1_000L
    const val QUANTITY_AXIS_GAP = 5
    const val QUANTITY_LABEL_LINE_HEIGHT = 9
    const val AXIS_TICK_LENGTH = 4
    const val LEGEND_SIDE_INSET = 8
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
    val AXIS = 0xA0687480.toInt()
    val SPREAD = 0xA0788798.toInt()
    val INVESTMENT = 0xFFFFAA00.toInt()
    val INSTANT_BUY = 0xFF55FFFF.toInt()
    val INSTANT_SELL = 0xFFFF7777.toInt()
    val BUY_ORDER = 0xFF5599FF.toInt()
    val SELL_ORDER = 0xFFFF55FF.toInt()
    val TRANSACTION_OUTLINE = 0xFF101316.toInt()
}
