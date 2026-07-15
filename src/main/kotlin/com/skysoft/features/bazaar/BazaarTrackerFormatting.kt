package com.skysoft.features.bazaar

import com.skysoft.data.ProfileStorage
import com.skysoft.utils.NumberUtilities.addSeparators
import com.skysoft.utils.NumberUtilities.coinFormat
import com.skysoft.utils.NumberUtilities.parseCompactNumberOrNull
import com.skysoft.utils.NumberUtilities.signedCoinFormat
import kotlin.math.pow

internal fun parseExactLong(text: String): Long = requireNotNull(text.replace(",", "").trim().toLongOrNull()) {
    "Expected an exact long value, got '$text'"
}

internal fun parseNumber(text: String): NumberParse {
    val parsed = requireNotNull(text.parseCompactNumberOrNull()) { "Expected a compact number, got '$text'" }
    if (!parsed.approximate) return NumberParse(parsed.value, approximate = false, resolution = 0.0)
    val clean = text.trim().replace(",", "")
    val decimalPlaces = clean.dropLast(1).substringAfter('.', "").length
    val multiplier = when (clean.last().lowercaseChar()) {
        'k' -> COMPACT_THOUSAND
        'm' -> COMPACT_MILLION
        'b' -> COMPACT_BILLION
        else -> error("Compact number is missing its suffix: '$text'")
    }
    return NumberParse(parsed.value, approximate = true, resolution = multiplier / 10.0.pow(decimalPlaces))
}

private const val COMPACT_THOUSAND = 1_000.0
private const val COMPACT_MILLION = 1_000_000.0
private const val COMPACT_BILLION = 1_000_000_000.0

internal fun formatAmount(amount: Long): String = amount.addSeparators()

internal fun formatOrderAmount(order: ProfileStorage.BazaarOrderData): String =
    formatAmount(order.amountOrdered) + if (order.amountResolution > 0.0) "+" else ""

internal fun formatCoins(coins: Double): String = coins.coinFormat()

internal fun formatSigned(coins: Double): String = coins.signedCoinFormat()

