package com.skysoft.features.bazaar

import com.skysoft.data.ProfileStorageView
import com.skysoft.data.skyblock.BazaarOrderType
import kotlin.math.abs

internal data class BazaarFillEstimateState(
    val type: BazaarOrderType,
    val productId: String,
    val pricePerUnit: Double,
    val amountOrdered: Long,
    val confirmedAtReference: Long,
    val referenceMillis: Long,
    val filledAtBaseline: Long,
    val baselineAmountAtPrice: Long,
    val queueAheadAtPrice: Long,
    val estimatedFilled: Long,
) {
    fun matches(order: ProfileStorageView.BazaarOrderData, productId: String): Boolean =
        type == order.type &&
            this.productId == productId &&
            amountOrdered == order.amountOrdered &&
            abs(pricePerUnit - order.pricePerUnit) <= BAZAAR_PRICE_EPSILON
}

internal fun currentFillEstimateState(
    order: ProfileStorageView.BazaarOrderData,
    productId: String,
    confirmedFilled: Long,
): BazaarFillEstimateState? =
    BazaarTrackingState.fillEstimateStates[order.id]
        ?.takeIf { it.matches(order, productId) && it.confirmedAtReference == confirmedFilled }
