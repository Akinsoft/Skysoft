package com.skysoft.features.bazaar

import com.skysoft.data.skyblock.BazaarOrderType
import com.skysoft.data.skyblock.price.SkysoftBazaarDepthProduct
import com.skysoft.data.skyblock.price.SkysoftBazaarDepthRow
import kotlin.math.abs

internal fun SkysoftBazaarDepthProduct.depthRowsFor(type: BazaarOrderType): List<SkysoftBazaarDepthRow> =
    when (type) {
        BazaarOrderType.BUY -> sellSummary
        BazaarOrderType.SELL -> buySummary
    }

internal fun SkysoftBazaarDepthProduct.eligibleFlowSince(type: BazaarOrderType, referenceMillis: Long): Long {
    val flow = flowDeltas.asSequence()
        .filter { it.at > referenceMillis }
        .sumOf {
            when (type) {
                BazaarOrderType.BUY -> it.sell
                BazaarOrderType.SELL -> it.buy
            }
        }
    return flow.toLong().coerceAtLeast(0L)
}

internal fun List<SkysoftBazaarDepthRow>.queueSnapshot(pricePerUnit: Double): BazaarQueueSnapshot? {
    val samePriceRows = filter { abs(it.pricePerUnit - pricePerUnit) <= BAZAAR_PRICE_EPSILON }
    val amountAtPrice = samePriceRows.sumOf { it.amount }
    val ordersAtPrice = samePriceRows.sumOf { it.orders }
    if (amountAtPrice <= 0L && ordersAtPrice <= 0L) return null
    return BazaarQueueSnapshot(amountAtPrice, ordersAtPrice)
}

internal data class BazaarQueueSnapshot(
    val amountAtPrice: Long,
    val ordersAtPrice: Long,
)
