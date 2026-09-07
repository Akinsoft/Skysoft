package com.skysoft.features.bazaar

import com.skysoft.data.ProfileStorage
import com.skysoft.data.ProfileStorageView
import com.skysoft.utils.gui.nonPlayerSlotAt
import com.skysoft.utils.trimStartToSize
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.world.inventory.Slot

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

internal fun slotAt(screen: AbstractContainerScreen<*>, mouseX: Int, mouseY: Int): Slot? =
    screen.nonPlayerSlotAt(mouseX, mouseY)

