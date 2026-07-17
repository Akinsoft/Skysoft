package com.skysoft.features.bazaar

import com.skysoft.data.ProfileStorage
import com.skysoft.utils.ChangeResult
import kotlin.math.max
import kotlin.math.roundToLong

internal fun applyCancel(cancel: PendingCancel) {
    val order = findCancelOrder(cancel)
        ?: run {
            pendingCancel = null
            return
        }

    when (order.type) {
        BazaarOrderType.BUY -> applyBuyCancel(order, cancel.amount ?: order.remainingAmount())
        BazaarOrderType.SELL -> applySellCancel(order, cancel.amount ?: order.remainingAmount())
    }
    clearPendingOrderAction()
    markBazaarTrackerChanged()
}

internal fun findCancelOrder(cancel: PendingCancel): ProfileStorage.BazaarOrderData? {
    cancel.orderId?.let { id ->
        val order = storage.activeOrders.firstOrNull { it.id == id }
        if (order != null && isPlausibleCancelOrder(order, cancel)) return order
    }
    return storage.activeOrders
        .asSequence()
        .filter { it.type == cancel.type }
        .filter { cancel.itemName.isBlank() || namesMatch(it.itemName, cancel.itemName) }
        .filter { productMatches(it.productId, cancel.productId) || cancel.productId == null || it.productId == null }
        .filter { isPlausibleCancelOrder(it, cancel) }
        .minWithOrNull(
            compareBy<ProfileStorage.BazaarOrderData> { cancelDistance(it, cancel) }
                .thenBy { if (pendingOrderOptionId == it.id) 0 else 1 }
                .thenByDescending { it.updatedAtMillis }
        )
}

internal fun isPlausibleCancelOrder(order: ProfileStorage.BazaarOrderData, cancel: PendingCancel): Boolean {
    val amount = cancel.amount ?: return true
    if (amount < 0 || order.amountOrdered <= 0) return false
    val expectedUnfilled = (order.maximumAmount() - order.filledAmount).coerceAtLeast(0L)
    val tolerance = max(
        MIN_CANCEL_AMOUNT_TOLERANCE,
        (max(amount, expectedUnfilled) * BAZAAR_MATCH_TOLERANCE_RATE).roundToLong(),
    )
    if (amount > order.maximumAmount() + tolerance) return false
    if (order.filledAmount > 0L && amount > expectedUnfilled + tolerance) return false
    return true
}

internal fun cancelDistance(order: ProfileStorage.BazaarOrderData, cancel: PendingCancel): Long {
    val amount = cancel.amount ?: return 0L
    val expectedUnfilled = (order.maximumAmount() - order.filledAmount).coerceAtLeast(0L)
    return amountDistance(expectedUnfilled, amount)
}

internal fun applyBuyCancel(order: ProfileStorage.BazaarOrderData, missingAmount: Long) {
    val filledTotal = (order.maximumAmount() - missingAmount).coerceAtLeast(order.filledAmount).coerceAtLeast(0L)
    if (filledTotal > order.claimedAmount) {
        order.amountOrdered = filledTotal
        order.filledAmount = filledTotal
        order.totalCoins = order.pricePerUnit * filledTotal
        order.updatedAtMillis = System.currentTimeMillis()
    } else {
        rememberResolvedOrder(order)
        storage.activeOrders.remove(order)
    }
}

internal fun applySellCancel(order: ProfileStorage.BazaarOrderData, unsoldAmount: Long) {
    val soldTotal = (order.maximumAmount() - unsoldAmount).coerceAtLeast(order.filledAmount).coerceAtLeast(0L)
    if (soldTotal > order.claimedAmount) {
        order.amountOrdered = soldTotal
        order.filledAmount = soldTotal
        val taxMultiplier = (1.0 - storage.taxPercent / BAZAAR_PERCENT_SCALE)
            .coerceAtLeast(MIN_BAZAAR_TAX_MULTIPLIER)
        order.totalCoins = order.pricePerUnit * soldTotal * taxMultiplier
        order.updatedAtMillis = System.currentTimeMillis()
    } else {
        rememberResolvedOrder(order)
        storage.activeOrders.remove(order)
    }
}

internal fun removeOrReduceOrderAfterClaim(order: ProfileStorage.BazaarOrderData, amount: Long) {
    applyClaimedAmount(order, amount, alert = false)
}

internal fun updateOrderFromGui(order: ProfileStorage.BazaarOrderData, parsed: PendingOrder): ChangeResult {
    val previousFilled = order.filledAmount
    var progressAlertBaseline = previousFilled
    val identityUpdate = updateOrderIdentityFromGui(order, parsed)
    var changed = identityUpdate.changed
    applyGuiFilledAmount(order, parsed)?.let { previousVisibleFilled ->
        progressAlertBaseline = max(progressAlertBaseline, previousVisibleFilled)
        changed = changed || order.filledAmount != previousFilled
    }
    if (changed) {
        if (identityUpdate.meaningful) order.updatedAtMillis = System.currentTimeMillis()
        playProgressAlert(order, progressAlertBaseline)
    }
    return ChangeResult.from(changed)
}

internal fun findClaimOrder(
    type: BazaarOrderType,
    itemName: String,
    amount: Long,
    unitPrice: Double,
): ProfileStorage.BazaarOrderData? {
    pendingOrderOptionId?.let { id ->
        storage.activeOrders.firstOrNull { isPlausibleClaimOrder(it, id, type, itemName, amount, unitPrice) }?.let {
            return it
        }
    }
    val candidates = storage.activeOrders
        .filter { isPlausibleClaimOrder(it, it.id, type, itemName, amount, unitPrice) }
    return candidates.minWithOrNull(
        compareBy<ProfileStorage.BazaarOrderData> {
            if (it.filledAmount - it.claimedAmount >= amount) 0 else 1
        }.thenBy {
            amountDistance(it.remainingAmount().takeIf { remaining -> remaining > 0 } ?: it.amountOrdered, amount)
        }.thenBy { it.createdAtMillis },
    )
}

internal fun isPlausibleClaimOrder(
    order: ProfileStorage.BazaarOrderData,
    id: String,
    type: BazaarOrderType,
    itemName: String,
    amount: Long,
    unitPrice: Double,
): Boolean {
    if (order.id != id || order.type != type || !namesMatch(order.itemName, itemName)) return false
    if (order.amountOrdered <= 0 && order.pricePerUnit <= 0.0) return false
    val remaining = order.remainingAmount().takeIf { it > 0 } ?: order.amountOrdered
    if (
        remaining > 0 &&
        amount > remaining + max(
            MIN_CLAIM_AMOUNT_TOLERANCE,
            (remaining * BAZAAR_MATCH_TOLERANCE_RATE).roundToLong(),
        )
    ) {
        return false
    }
    if (unitPrice > 0.0 && order.pricePerUnit > 0.0 && !haveOverlappingRanges(
            order.pricePerUnit,
            order.pricePerUnitResolution,
            unitPrice,
            0.0,
            MIN_UNIT_PRICE_TOLERANCE,
        )
    ) {
        return false
    }
    return true
}

internal fun pruneOrdersMissingFromGui(
    matchedOrderIds: Set<String>,
    parsedOrders: List<PendingOrder>,
    visibleOrderCount: Int,
): ChangeResult {
    val now = System.currentTimeMillis()
    val recentlyClickedOrder = now - lastOrdersGuiClickMillis < GUI_MISSING_PRUNE_CLICK_GRACE_MILLIS
    val visibleScanMayBeWindowed = visibleOrderCount >= BAZAAR_ORDERS_GUI_VISIBLE_ORDER_LIMIT &&
        storage.activeOrders.any { it.id !in matchedOrderIds }
    var changed = false
    val iterator = storage.activeOrders.iterator()
    while (iterator.hasNext()) {
        val order = iterator.next()
        if (order.id in matchedOrderIds) {
            missingFromOrdersGuiScans.remove(order.id)
        } else if (visibleScanMayBeWindowed) {
            missingFromOrdersGuiScans.remove(order.id)
        } else if (!shouldPruneMissingFromGui(order, parsedOrders, recentlyClickedOrder, now)) {
            missingFromOrdersGuiScans.remove(order.id)
        } else {
            val previous = missingFromOrdersGuiScans[order.id]
            val observation = MissingOrderObservation(
                scans = (previous?.scans ?: 0) + 1,
                firstObservedAtMillis = previous?.firstObservedAtMillis ?: now,
            )
            missingFromOrdersGuiScans[order.id] = observation
            if (
                parsedOrders.isEmpty() ||
                (
                    observation.scans >= GUI_MISSING_PRUNE_CONFIRM_SCANS &&
                        now - observation.firstObservedAtMillis >= GUI_MISSING_PRUNE_MIN_CONFIRMATION_MILLIS
                    )
            ) {
                rememberResolvedOrder(order)
                iterator.remove()
                changed = true
            }
        }
    }
    return ChangeResult.from(changed)
}

internal fun shouldPruneMissingFromGui(
    order: ProfileStorage.BazaarOrderData,
    parsedOrders: List<PendingOrder>,
    recentlyClickedOrder: Boolean,
    now: Long,
): Boolean {
    if (recentlyClickedOrder) return false
    if (pendingCancel?.orderId == order.id) return false
    if (now - order.createdAtMillis < GUI_MISSING_PRUNE_NEW_ORDER_GRACE_MILLIS) return false

    // If we have seen this order in a real Bazaar Orders slot before, then a stable scan
    // where it did not match any current order means the tracked entry is stale. This catches
    // ghosts like an old SELL in slot 10 while the actual menu now only contains BUY rows.
    if (order.lastGuiSlot >= 0) return true

    // New chat-confirmed orders start without a slot. Keep them only if the current GUI still
    // contains a plausible matching row that may be claimed by another duplicate this scan.
    return parsedOrders.none { parsed -> parsedRepresentsOrder(order, parsed) }
}

internal fun parsedRepresentsOrder(order: ProfileStorage.BazaarOrderData, parsed: PendingOrder): Boolean {
    if (order.type != parsed.type) return false
    if (!productMatches(order.productId, parsed.productId) && !namesMatch(order.itemName, parsed.itemName)) return false
    if (!orderMatchesParsedIdentity(order, parsed)) return false
    return guiMatchIsPlausible(order, parsed)
}

