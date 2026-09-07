package com.skysoft.features.bazaar

import java.util.ArrayDeque

internal object BazaarTrackingState {
    var pendingSetup: PendingOrder? = null
    var pendingOrderOptionId: String? = null
    var pendingCancel: PendingCancel? = null
    private var lastOrdersInventoryKey: String? = null
    private var pendingOrdersInventoryKey: String? = null
    private var pendingOrdersInventoryStableTicks = 0
    var statusAlertTick = 0
    var lastOrdersGuiClickMillis = 0L
    var lastOrdersGuiClickSignature = ""
    val lastAlertStatuses = mutableMapOf<String, OrderStatus>()
    val lastOutbidAlertMillis = mutableMapOf<String, Long>()
    val missingFromOrdersGuiScans = mutableMapOf<String, MissingOrderObservation>()
    val fillHighlightExpiresAt = mutableMapOf<String, Long>()
    val marketProofMillis = mutableMapOf<String, Long>()
    val recentResolvedOrders = ArrayDeque<RecentResolvedOrder>()
    var depthRefreshTick = 0
    val fillEstimateStates = mutableMapOf<String, BazaarFillEstimateState>()

    fun reset() {
        pendingSetup = null
        pendingOrderOptionId = null
        pendingCancel = null
        resetOrderScan()
        statusAlertTick = 0
        lastOrdersGuiClickMillis = 0L
        lastOrdersGuiClickSignature = ""
        lastAlertStatuses.clear()
        lastOutbidAlertMillis.clear()
        missingFromOrdersGuiScans.clear()
        fillHighlightExpiresAt.clear()
        marketProofMillis.clear()
        recentResolvedOrders.clear()
        depthRefreshTick = 0
        fillEstimateStates.clear()
    }

    fun resetOrderScan() {
        lastOrdersInventoryKey = null
        pendingOrdersInventoryKey = null
        pendingOrdersInventoryStableTicks = 0
    }

    fun observeOrderInventory(key: String): BazaarOrderScanDecision {
        if (key == lastOrdersInventoryKey && missingFromOrdersGuiScans.isEmpty()) return BazaarOrderScanDecision.SKIP
        if (key != pendingOrdersInventoryKey) {
            pendingOrdersInventoryKey = key
            pendingOrdersInventoryStableTicks = 1
            return BazaarOrderScanDecision.SKIP
        }
        pendingOrdersInventoryStableTicks++
        if (pendingOrdersInventoryStableTicks < GUI_MISSING_PRUNE_INVENTORY_STABLE_TICKS) return BazaarOrderScanDecision.SKIP
        lastOrdersInventoryKey = key
        pendingOrdersInventoryKey = null
        pendingOrdersInventoryStableTicks = 0
        return BazaarOrderScanDecision.SCAN
    }

    fun forgetOrder(orderId: String) {
        lastAlertStatuses.remove(orderId)
        lastOutbidAlertMillis.remove(orderId)
        missingFromOrdersGuiScans.remove(orderId)
        fillHighlightExpiresAt.remove(orderId)
        marketProofMillis.remove(orderId)
        fillEstimateStates.remove(orderId)
    }
}

internal enum class BazaarOrderScanDecision {
    SKIP,
    SCAN,
}
