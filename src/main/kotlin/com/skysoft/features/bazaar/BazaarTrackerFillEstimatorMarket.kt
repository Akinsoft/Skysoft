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

internal fun List<SkysoftBazaarDepthRow>.queueSnapshot(
    type: BazaarOrderType,
    pricePerUnit: Double,
    remaining: Long,
): BazaarQueueSnapshot? {
    var betterAmount = 0L
    var sameAmount = 0L
    var sameOrders = 0L

    for (row in this) {
        when {
            row.isBetterThan(type, pricePerUnit) -> betterAmount += row.amount
            abs(row.pricePerUnit - pricePerUnit) <= BAZAAR_PRICE_EPSILON -> {
                sameAmount += row.amount
                sameOrders += row.orders
            }
        }
    }

    if (sameAmount <= 0L && sameOrders <= 0L) return null
    return BazaarQueueSnapshot(
        amountAtPrice = sameAmount,
        ordersAtPrice = sameOrders,
        queueAhead = betterAmount + (sameAmount - remaining).coerceAtLeast(0L),
    )
}

private fun SkysoftBazaarDepthRow.isBetterThan(type: BazaarOrderType, pricePerUnit: Double): Boolean =
    when (type) {
        BazaarOrderType.BUY -> this.pricePerUnit > pricePerUnit + BAZAAR_PRICE_EPSILON
        BazaarOrderType.SELL -> this.pricePerUnit < pricePerUnit - BAZAAR_PRICE_EPSILON
    }

internal data class BazaarQueueSnapshot(
    val amountAtPrice: Long = 0,
    val ordersAtPrice: Long = 0,
    val queueAhead: Long = 0,
)
