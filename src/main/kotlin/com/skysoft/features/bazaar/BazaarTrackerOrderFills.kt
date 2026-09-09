package com.skysoft.features.bazaar

import com.skysoft.data.ProfileStorage

internal fun applyGuiFilledAmount(order: ProfileStorage.BazaarOrderData, parsed: PendingOrder): Long? {
    val filled = parsed.filledAmount ?: return clearGuiContradictedFillEstimate(order)
    val previousVisibleFilled = visibleFilledAmount(order)
    val confirmedFilled = confirmedFilledAmount(order)
    val newFilled = if (order.amountOrdered > 0) filled.coerceAtMost(order.maximumAmount()) else filled
    if (parsed.filledAmountApproximate && previousVisibleFilled > confirmedFilled) return previousVisibleFilled
    resetFillEstimate(order)
    if (newFilled <= order.filledAmount) return previousVisibleFilled
    order.filledAmount = newFilled
    if (newFilled > previousVisibleFilled) markFillHighlight(order, newFilled)
    return previousVisibleFilled
}

private fun clearGuiContradictedFillEstimate(order: ProfileStorage.BazaarOrderData): Long? {
    val estimate = BazaarTrackingState.fillEstimateStates.remove(order.id) ?: return null
    BazaarTrackingState.fillHighlightExpiresAt.remove(order.id)
    val confirmedFilled = confirmedFilledAmount(order)
    return maxOf(confirmedFilled, estimate.estimatedFilled)
}
