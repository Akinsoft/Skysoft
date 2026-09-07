package com.skysoft.features.bazaar

import com.skysoft.data.ProfileStorage
import kotlin.math.max

internal fun ProfileStorage.BazaarTrackerData.addActiveOrder(
    order: ProfileStorage.BazaarOrderData,
    requireFreshMarketProof: Boolean,
) {
    this.activeOrders += order
    recordBazaarTransaction(this, order.toBazaarTransaction())
    if (requireFreshMarketProof) requireMarketProof(order)
    initializeOrderAlertState(order)
}

internal fun refreshBazaarTrackerMarketData(
    refreshFillEstimates: Boolean = false,
    refreshOrderBook: Boolean = false,
) {
    if (refreshFillEstimates) requestBazaarFillEstimateRefresh()
    if (refreshOrderBook) BazaarOrderBookApi.refreshNow()
}

internal fun clearPendingOrderAction() {
    pendingCancel = null
    pendingOrderOptionId = null
}

internal fun ProfileStorage.BazaarTrackerData.applyClaimedAmount(
    order: ProfileStorage.BazaarOrderData,
    amount: Long,
    claimedCoins: Double = 0.0,
    alert: Boolean = true,
): OrderRemovalResult {
    val previousFilled = order.filledAmount
    order.filledAmount = max(order.filledAmount, order.claimedAmount + amount)
    if (alert) playProgressAlert(order, previousFilled)
    order.claimedAmount += amount
    order.claimedCoins += claimedCoins
    order.updatedAtMillis = System.currentTimeMillis()
    val removed = order.claimedAmount >= order.maximumAmount()
    if (removed) {
        rememberResolvedOrder(order)
        this.activeOrders.remove(order)
    }
    return if (removed) OrderRemovalResult.REMOVED else OrderRemovalResult.KEPT
}

internal enum class OrderRemovalResult {
    REMOVED,
    KEPT,
}
