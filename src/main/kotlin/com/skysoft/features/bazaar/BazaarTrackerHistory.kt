package com.skysoft.features.bazaar

import com.skysoft.data.ProfileStorage
import com.skysoft.data.skyblock.BazaarOrderType
import com.skysoft.data.ProfileStorageView
import com.skysoft.utils.trimStartToSize

internal fun rememberResolvedOrder(order: ProfileStorageView.BazaarOrderData) {
    BazaarTrackingState.forgetOrder(order.id)
    pruneRecentResolvedOrders()
    BazaarTrackingState.recentResolvedOrders.addLast(
        RecentResolvedOrder(
            type = order.type,
            itemName = order.itemName,
            productId = order.productId,
            amount = order.amountOrdered,
            totalCoins = order.totalCoins,
            timestampMillis = System.currentTimeMillis(),
        ),
    )
    BazaarTrackingState.recentResolvedOrders.trimStartToSize(MAX_RECENT_RESOLVED_ORDERS)
}

internal fun ProfileStorage.BazaarTrackerData.resolveTrackedOrder(order: ProfileStorageView.BazaarOrderData) {
    rememberResolvedOrder(order)
    this.activeOrders.remove(order)
}

internal fun ProfileStorage.BazaarTrackerData.discardTrackedOrder(order: ProfileStorageView.BazaarOrderData) {
    BazaarTrackingState.forgetOrder(order.id)
    this.activeOrders.remove(order)
}

internal fun recentlyResolved(parsed: PendingOrder): Boolean {
    pruneRecentResolvedOrders()
    return BazaarTrackingState.recentResolvedOrders.any { resolved ->
        resolved.type == parsed.type &&
            (productMatches(resolved.productId, parsed.productId) || namesMatch(resolved.itemName, parsed.itemName)) &&
            haveOverlappingRanges(
                resolved.amount.toDouble(),
                0.0,
                parsed.amount.toDouble(),
                parsed.amountResolution,
                EXACT_AMOUNT_EPSILON,
            ) &&
            (
                parsed.totalCoins == null || haveOverlappingRanges(
                    resolved.totalCoins,
                    0.0,
                    parsed.totalCoins,
                    parsed.totalCoinsResolution,
                    TOTAL_RECALCULATION_EPSILON,
                )
                )
    }
}

internal fun pruneRecentResolvedOrders() {
    val cutoff = System.currentTimeMillis() - RECENT_RESOLVED_SUPPRESS_MILLIS
    val orders = BazaarTrackingState.recentResolvedOrders
    while (orders.firstOrNull()?.timestampMillis?.let { it < cutoff } == true) orders.removeFirst()
}

internal data class RecentResolvedOrder(
    val type: BazaarOrderType,
    val itemName: String,
    val productId: String?,
    val amount: Long,
    val totalCoins: Double,
    val timestampMillis: Long,
)

private const val MAX_RECENT_RESOLVED_ORDERS = 20
private const val RECENT_RESOLVED_SUPPRESS_MILLIS = 5_000L
