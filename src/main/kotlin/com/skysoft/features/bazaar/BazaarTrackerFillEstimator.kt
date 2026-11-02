package com.skysoft.features.bazaar

import com.skysoft.data.ProfileStorageView
import com.skysoft.data.hypixel.HypixelLocationState
import com.skysoft.data.skyblock.price.SkyBlockPriceData
import com.skysoft.data.skyblock.price.SkysoftBazaarDepthProduct
import com.skysoft.utils.SkysoftErrorBoundary
import kotlin.math.max
import kotlin.math.min

internal fun tickBazaarFillEstimator() {
    if (didClearDisabledFillEstimates()) return
    if (BazaarTrackingState.depthRefreshTick++ % BAZAAR_DEPTH_REFRESH_INTERVAL_TICKS != 0) return
    refreshBazaarFillEstimates()
}

internal fun requestBazaarFillEstimateRefresh() {
    if (didClearDisabledFillEstimates()) return
    BazaarTrackingState.depthRefreshTick = 1
    refreshBazaarFillEstimates()
}

private fun didClearDisabledFillEstimates(): Boolean {
    if (config.settings.estimateFills) return false
    BazaarTrackingState.fillEstimateStates.clear()
    return true
}

private fun refreshBazaarFillEstimates() {
    pruneFillEstimateStates()
    val orders = storage.activeOrders.filter { !it.productId.isNullOrBlank() && it.amountResolution <= 0.0 }
    if (orders.isEmpty()) return
    val productIds = orders.mapNotNull { it.productId }.distinct()
    val sinceMillis = orders.minOfOrNull { orderReferenceMillis(it) } ?: 0L
    val sessionVersion = BazaarTrackingState.sessionVersion

    SkyBlockPriceData.refreshBazaarDepth(productIds, sinceMillis)?.whenComplete { products, error ->
        if (products == null || error != null) return@whenComplete
        SkysoftErrorBoundary.onClientThread("Bazaar Tracker fill estimate async completion") {
            if (sessionVersion != BazaarTrackingState.sessionVersion) return@onClientThread
            if (!config.enabled || !HypixelLocationState.inSkyBlock) return@onClientThread
            applyBazaarDepthProducts(products)
        }
    }
}

private fun applyBazaarDepthProducts(products: Map<String, SkysoftBazaarDepthProduct>) {
    if (didClearDisabledFillEstimates()) return
    pruneFillEstimateStates()
    val activeOrders = storage.activeOrders.toList()
    for (order in activeOrders) {
        val ambiguous = activeOrders.any { other -> hasOverlappingFillEstimateIdentity(order, other) }
        if (ambiguous || order.amountResolution > 0.0) {
            BazaarTrackingState.fillEstimateStates.remove(order.id)
            continue
        }
        val productId = order.productId ?: continue
        val product = products[productId] ?: continue
        updateFillEstimate(order, productId, product)
    }
}

private fun updateFillEstimate(
    order: ProfileStorageView.BazaarOrderData,
    productId: String,
    product: SkysoftBazaarDepthProduct,
) {
    val confirmedFilled = confirmedFilledAmount(order)
    val remaining = (order.amountOrdered - confirmedFilled).coerceAtLeast(0L)
    if (remaining <= 0L) {
        BazaarTrackingState.fillEstimateStates.remove(order.id)
        return
    }

    val queue = product.depthRowsFor(order.type).queueSnapshot(order.pricePerUnit) ?: return
    val existingState = currentFillEstimateState(order, productId, confirmedFilled)
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
    BazaarTrackerAlerts.showEstimatedFillProgress(order, previousVisibleFilled, visibleFilled)

    BazaarTrackingState.fillEstimateStates[order.id] = state.copy(estimatedFilled = estimatedFilled)
}

private fun initialFillEstimateState(
    order: ProfileStorageView.BazaarOrderData,
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
        type = order.type,
        productId = productId,
        pricePerUnit = order.pricePerUnit,
        amountOrdered = order.amountOrdered,
        confirmedAtReference = confirmedFilled,
        referenceMillis = baseOrderReferenceMillis(order),
        filledAtBaseline = baselineFilled,
        baselineAmountAtPrice = queue.amountAtPrice,
        queueAheadAtPrice = (queue.amountAtPrice - remaining).coerceAtLeast(0L),
        estimatedFilled = baselineFilled,
    )
}

private fun estimatedVisibleFilled(
    order: ProfileStorageView.BazaarOrderData,
    confirmedFilled: Long,
    estimatedFilled: Long,
): Long {
    if (order.amountOrdered <= 0L || confirmedFilled >= order.amountOrdered) return confirmedFilled
    return max(confirmedFilled, estimatedFilled).coerceAtMost(order.amountOrdered - 1)
}

internal fun estimatedFilledAmount(order: ProfileStorageView.BazaarOrderData): Long {
    if (!config.settings.estimateFills) return 0L
    return BazaarTrackingState.fillEstimateStates[order.id]
        ?.takeIf { state -> order.productId?.let { state.matches(order, it) } == true }
        ?.estimatedFilled
        ?.coerceIn(0L, order.amountOrdered)
        ?: 0L
}

internal fun confirmedFilledAmount(order: ProfileStorageView.BazaarOrderData): Long =
    max(order.filledAmount, order.claimedAmount).coerceAtMost(order.amountOrdered)

internal fun visibleFilledAmount(order: ProfileStorageView.BazaarOrderData): Long =
    estimatedVisibleFilled(order, confirmedFilledAmount(order), estimatedFilledAmount(order))

private fun pruneFillEstimateStates() {
    val activeIds = storage.activeOrders.mapTo(mutableSetOf()) { it.id }
    BazaarTrackingState.fillEstimateStates.keys.retainAll(activeIds)
}

internal fun resetFillEstimate(order: ProfileStorageView.BazaarOrderData) {
    BazaarTrackingState.fillEstimateStates.remove(order.id)
}

private fun orderReferenceMillis(order: ProfileStorageView.BazaarOrderData): Long {
    val productId = order.productId ?: return baseOrderReferenceMillis(order)
    return currentFillEstimateState(order, productId, confirmedFilledAmount(order))?.referenceMillis
        ?: baseOrderReferenceMillis(order)
}

private fun baseOrderReferenceMillis(order: ProfileStorageView.BazaarOrderData): Long =
    max(order.createdAtMillis, order.updatedAtMillis)

internal const val BAZAAR_PRICE_EPSILON = 0.0001
private const val BAZAAR_DEPTH_REFRESH_INTERVAL_TICKS = 20 * 5
