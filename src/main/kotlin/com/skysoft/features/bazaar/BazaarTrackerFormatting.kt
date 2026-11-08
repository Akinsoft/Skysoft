package com.skysoft.features.bazaar

import com.skysoft.data.ProfileStorageView
import com.skysoft.utils.NumberUtilities.CompactNumber
import com.skysoft.utils.NumberUtilities.addSeparators
import com.skysoft.utils.NumberUtilities.formatLongOrNull
import com.skysoft.utils.NumberUtilities.parseCompactNumberOrNull

internal fun parseExactLong(text: String): Long = requireNotNull(text.trim().formatLongOrNull()) {
    "Expected an exact long value, got '$text'"
}

internal fun parseNumber(text: String): CompactNumber =
    requireNotNull(text.parseCompactNumberOrNull()) { "Expected a compact number, got '$text'" }

internal fun formatOrderAmount(order: ProfileStorageView.BazaarOrderData): String =
    order.amountOrdered.addSeparators() + if (order.amountResolution > 0.0) "+" else ""
