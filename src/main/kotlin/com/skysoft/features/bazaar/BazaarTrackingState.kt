package com.skysoft.features.bazaar

import com.skysoft.data.ProfileStorageView
import com.skysoft.data.skyblock.BazaarOrderType
import java.util.ArrayDeque

internal object BazaarTrackingState {
    var sessionVersion = 0L
        private set
    var pendingSetup: PendingOrder? = null
    var pendingOrderOptionId: String? = null
    var pendingCancel: PendingCancel? = null
    private var lastOrdersInventoryKey: String? = null
    private var pendingOrdersInventoryKey: String? = null
    private var pendingOrdersInventoryStableTicks = 0
    var lastOrdersGuiClickMillis = 0L
    var lastOrdersGuiClickSignature = ""
    val missingFromOrdersGuiScans = mutableMapOf<String, MissingOrderObservation>()
    val fillHighlightExpiresAt = mutableMapOf<String, Long>()
    val marketProofMillis = mutableMapOf<String, Long>()
    val recentResolvedOrders = ArrayDeque<RecentResolvedOrder>()
    var depthRefreshTick = 0
    val fillEstimateStates = mutableMapOf<String, BazaarFillEstimateState>()

    fun reset() {
        sessionVersion++
        pendingSetup = null
        pendingOrderOptionId = null
        pendingCancel = null
        resetOrderScan()
        lastOrdersGuiClickMillis = 0L
        lastOrdersGuiClickSignature = ""
        BazaarTrackerAlerts.reset()
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

    fun observeOrderInventory(key: String): Boolean {
        if (key == lastOrdersInventoryKey && missingFromOrdersGuiScans.isEmpty()) return false
        if (key != pendingOrdersInventoryKey) {
            pendingOrdersInventoryKey = key
            pendingOrdersInventoryStableTicks = 1
            return false
        }
        pendingOrdersInventoryStableTicks++
        if (pendingOrdersInventoryStableTicks < GUI_MISSING_PRUNE_INVENTORY_STABLE_TICKS) return false
        lastOrdersInventoryKey = key
        pendingOrdersInventoryKey = null
        pendingOrdersInventoryStableTicks = 0
        return true
    }

    fun forgetOrder(orderId: String) {
        BazaarTrackerAlerts.forgetOrder(orderId)
        missingFromOrdersGuiScans.remove(orderId)
        fillHighlightExpiresAt.remove(orderId)
        marketProofMillis.remove(orderId)
        fillEstimateStates.remove(orderId)
    }
}

internal data class MissingOrderObservation(
    val scans: Int,
    val firstObservedAtMillis: Long,
)

private const val GUI_MISSING_PRUNE_INVENTORY_STABLE_TICKS = 3

internal fun <T : ProfileStorageView.BazaarOrderData> List<T>.pendingOptionOrder(type: BazaarOrderType? = null): T? =
    BazaarTrackingState.pendingOrderOptionId?.let { id ->
        firstOrNull { it.id == id && (type == null || it.type == type) }
    }
