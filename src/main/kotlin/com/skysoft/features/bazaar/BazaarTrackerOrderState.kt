package com.skysoft.features.bazaar

import com.skysoft.data.ProfileStorage
import kotlin.math.max

internal fun ProfileStorage.BazaarTrackerData.addActiveOrder(
    order: ProfileStorage.BazaarOrderData,
    requireFreshMarketProof: Boolean,
) {
    this.activeOrders += order
    recordTransaction(order.toBazaarTransaction())
    if (requireFreshMarketProof) requireMarketProof(order)
    BazaarTrackerAlerts.initializeOrderAlertState(order)
}

internal fun clearPendingOrderAction() {
    BazaarTrackingState.pendingCancel = null
    BazaarTrackingState.pendingOrderOptionId = null
}

internal fun ProfileStorage.BazaarTrackerData.applyClaimedAmount(
    order: ProfileStorage.BazaarOrderData,
    amount: Long,
    claimedCoins: Double = 0.0,
    alert: Boolean = true,
) {
    val previousFilled = order.filledAmount
    order.filledAmount = max(order.filledAmount, order.claimedAmount + amount)
    if (alert) BazaarTrackerAlerts.playProgressAlert(order, previousFilled)
    order.claimedAmount += amount
    order.claimedCoins += claimedCoins
    order.updatedAtMillis = System.currentTimeMillis()
    if (order.claimedAmount >= order.maximumAmount()) resolveTrackedOrder(order)
}

internal fun refreshOrderMarketData() {
    requestBazaarFillEstimateRefresh()
    BazaarOrderBookApi.refreshNow()
}
