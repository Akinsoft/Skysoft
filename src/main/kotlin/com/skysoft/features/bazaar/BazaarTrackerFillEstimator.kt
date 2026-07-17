package com.skysoft.features.bazaar

import com.skysoft.data.ProfileStorage
import com.skysoft.data.skyblock.price.SkyBlockPriceData
import com.skysoft.data.skyblock.price.SkysoftBazaarDepthProduct
import com.skysoft.utils.SkysoftErrorBoundary
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

internal var depthRefreshTick = 0
internal val fillEstimateStates = mutableMapOf<String, BazaarFillEstimateState>()

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

internal fun tickBazaarFillEstimator() {
    if (!config.settings.estimateFills) {
        fillEstimateStates.clear()
        return
    }
    if (depthRefreshTick++ % BAZAAR_DEPTH_REFRESH_INTERVAL_TICKS != 0) return
    refreshBazaarFillEstimates()
}

internal fun requestBazaarFillEstimateRefresh() {
    if (!config.settings.estimateFills) {
        fillEstimateStates.clear()
        return
    }
    depthRefreshTick = 1
    refreshBazaarFillEstimates()
}

internal fun refreshBazaarFillEstimates() {
    if (!config.settings.estimateFills) {
        fillEstimateStates.clear()
        return
    }
    pruneFillEstimateStates()
    val orders = storage.activeOrders.filter { !it.productId.isNullOrBlank() && it.amountResolution <= 0.0 }
    if (orders.isEmpty()) return
    val productIds = orders.mapNotNull { it.productId }.distinct()
    val sinceMillis = orders.minOfOrNull { orderReferenceMillis(it) } ?: 0L

    SkyBlockPriceData.refreshBazaarDepth(productIds, sinceMillis)?.whenComplete { products, error ->
        if (products == null || error != null) return@whenComplete
        SkysoftErrorBoundary.onClientThread("Bazaar Tracker fill estimate async completion") {
            applyBazaarDepthProducts(products)
        }
    }
}

internal fun applyBazaarDepthProducts(products: Map<String, SkysoftBazaarDepthProduct>) {
    if (!config.settings.estimateFills) {
        fillEstimateStates.clear()
        return
    }
    pruneFillEstimateStates()
    val activeOrders = storage.activeOrders.toList()
    val ambiguousIds = activeOrders
        .filter { order -> activeOrders.any { other -> hasOverlappingFillEstimateIdentity(order, other) } }
        .mapTo(mutableSetOf()) { it.id }
    ambiguousIds.forEach(fillEstimateStates::remove)
    val uncertainAmountIds = activeOrders.filter { it.amountResolution > 0.0 }.mapTo(mutableSetOf()) { it.id }
    uncertainAmountIds.forEach(fillEstimateStates::remove)
    storage.activeOrders.forEach { order ->
        if (order.id in ambiguousIds || order.amountResolution > 0.0) return@forEach
        val productId = order.productId ?: return@forEach
        val product = products[productId] ?: return@forEach
        updateFillEstimate(order, product)
    }
}

internal fun updateFillEstimate(
    order: ProfileStorage.BazaarOrderData,
    product: SkysoftBazaarDepthProduct,
) {
    if (!config.settings.estimateFills) return
    val productId = order.productId ?: return
    val confirmedFilled = max(order.filledAmount, order.claimedAmount).coerceAtMost(order.amountOrdered)
    val remaining = (order.amountOrdered - confirmedFilled).coerceAtLeast(0L)
    if (remaining <= 0L) {
        fillEstimateStates.remove(order.id)
        return
    }

    val queue = product.depthRowsFor(order.type).queueSnapshot(order.type, order.pricePerUnit, remaining) ?: return
    val existingState = fillEstimateStates[order.id]
        ?.takeIf { it.matches(order, productId) && it.confirmedAtReference == confirmedFilled }
    val state = existingState ?: initialFillEstimateState(
        order,
        productId,
        confirmedFilled,
        queue,
        product.eligibleFlowSince(order.type, baseOrderReferenceMillis(order)),
    )
    val previousVisibleFilled = estimatedVisibleFilled(order, confirmedFilled, existingState?.estimatedFilled ?: confirmedFilled)

    val eligibleFlow = product.eligibleFlowSince(order.type, state.referenceMillis)
    val samePriceFilled = (
        state.baselineAmountAtPrice -
            queue.amountAtPrice -
            state.queueAheadAtPrice
        ).coerceAtLeast(0L)
    val filledSinceBaseline = min(samePriceFilled, eligibleFlow)
    val estimatedFilled = max(state.estimatedFilled, state.filledAtBaseline + filledSinceBaseline)
        .coerceIn(confirmedFilled, order.amountOrdered)
    val visibleFilled = estimatedVisibleFilled(order, confirmedFilled, estimatedFilled)
    showEstimatedFillProgress(order, previousVisibleFilled, visibleFilled)

    fillEstimateStates[order.id] = state.copy(
        queueAhead = queue.queueAhead,
        estimatedFilled = estimatedFilled,
        updatedAtMillis = System.currentTimeMillis(),
    )
}

private fun initialFillEstimateState(
    order: ProfileStorage.BazaarOrderData,
    productId: String,
    confirmedFilled: Long,
    queue: BazaarQueueSnapshot,
    eligibleFlow: Long,
): BazaarFillEstimateState {
    val remaining = (order.amountOrdered - confirmedFilled).coerceAtLeast(0L)
    val inferredAlreadyFilled = if (queue.amountAtPrice < remaining && queue.ordersAtPrice == 1L) {
        min(remaining - queue.amountAtPrice, eligibleFlow)
    } else {
        0L
    }
    val baselineFilled = (confirmedFilled + inferredAlreadyFilled).coerceAtMost(order.amountOrdered)
    return BazaarFillEstimateState(
        orderId = order.id,
        type = order.type,
        productId = productId,
        pricePerUnit = order.pricePerUnit,
        amountOrdered = order.amountOrdered,
        confirmedAtReference = confirmedFilled,
        referenceMillis = baseOrderReferenceMillis(order),
        filledAtBaseline = baselineFilled,
        baselineAmountAtPrice = queue.amountAtPrice,
        queueAheadAtPrice = (queue.amountAtPrice - remaining).coerceAtLeast(0L),
        queueAhead = queue.queueAhead,
        estimatedFilled = baselineFilled,
        updatedAtMillis = System.currentTimeMillis(),
    )
}

private fun estimatedVisibleFilled(
    order: ProfileStorage.BazaarOrderData,
    confirmedFilled: Long,
    estimatedFilled: Long,
): Long {
    if (order.amountOrdered <= 0L || confirmedFilled >= order.amountOrdered) return confirmedFilled
    return max(confirmedFilled, estimatedFilled).coerceAtMost(order.amountOrdered - 1)
}

internal fun estimatedFilledAmount(order: ProfileStorage.BazaarOrderData): Long {
    if (!config.settings.estimateFills) return 0L
    return fillEstimateStates[order.id]
        ?.takeIf { state -> order.productId?.let { state.matches(order, it) } == true }
        ?.estimatedFilled
        ?.coerceIn(0L, order.amountOrdered)
        ?: 0L
}

internal fun confirmedFilledAmount(order: ProfileStorage.BazaarOrderData): Long =
    max(order.filledAmount, order.claimedAmount).coerceAtMost(order.amountOrdered)

internal fun visibleFilledAmount(order: ProfileStorage.BazaarOrderData): Long {
    val confirmedFilled = confirmedFilledAmount(order)
    if (!config.settings.estimateFills || order.amountOrdered <= 0L || confirmedFilled >= order.amountOrdered) {
        return confirmedFilled
    }
    return max(confirmedFilled, estimatedFilledAmount(order)).coerceAtMost(order.amountOrdered - 1)
}

internal fun pruneFillEstimateStates() {
    val activeIds = storage.activeOrders.mapTo(mutableSetOf()) { it.id }
    fillEstimateStates.keys.retainAll(activeIds)
}

internal fun resetFillEstimate(order: ProfileStorage.BazaarOrderData) {
    fillEstimateStates.remove(order.id)
}

private fun orderReferenceMillis(order: ProfileStorage.BazaarOrderData): Long {
    val productId = order.productId ?: return baseOrderReferenceMillis(order)
    val confirmedFilled = confirmedFilledAmount(order)
    return fillEstimateStates[order.id]
        ?.takeIf { it.matches(order, productId) && it.confirmedAtReference == confirmedFilled }
        ?.referenceMillis
        ?: baseOrderReferenceMillis(order)
}

private fun baseOrderReferenceMillis(order: ProfileStorage.BazaarOrderData): Long =
    max(order.createdAtMillis, order.updatedAtMillis)

private fun BazaarFillEstimateState.matches(order: ProfileStorage.BazaarOrderData, productId: String): Boolean =
    type == order.type &&
        this.productId == productId &&
        amountOrdered == order.amountOrdered &&
        abs(pricePerUnit - order.pricePerUnit) <= BAZAAR_PRICE_EPSILON

internal const val BAZAAR_PRICE_EPSILON = 0.0001
private const val BAZAAR_DEPTH_REFRESH_INTERVAL_TICKS = 20 * 5
