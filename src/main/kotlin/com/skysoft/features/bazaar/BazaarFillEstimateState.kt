package com.skysoft.features.bazaar

import com.skysoft.data.skyblock.BazaarOrderType

internal data class BazaarFillEstimateState(
    val orderId: String,
    val type: BazaarOrderType,
    val productId: String,
    val pricePerUnit: Double,
    val amountOrdered: Long,
    val confirmedAtReference: Long,
    val referenceMillis: Long,
    val filledAtBaseline: Long,
    val baselineAmountAtPrice: Long,
    val queueAheadAtPrice: Long,
    val queueAhead: Long,
    val estimatedFilled: Long,
    val updatedAtMillis: Long,
)
