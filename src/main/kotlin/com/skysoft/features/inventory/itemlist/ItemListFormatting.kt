package com.skysoft.features.inventory.itemlist

import com.skysoft.utils.NumberUtilities.addSeparators
import java.util.Locale
import kotlin.math.floor

internal object ItemListFormatting {
    fun number(value: Long): String = value.addSeparators()

    fun compactNumber(value: Long): String = when {
        value >= BILLION -> compact(value, BILLION, "b")
        value >= MILLION -> compact(value, MILLION, "m")
        value >= THOUSAND -> compact(value, THOUSAND, "k")
        else -> value.toString()
    }

    fun duration(seconds: Long): String {
        val hours = seconds / SECONDS_PER_HOUR
        val minutes = seconds % SECONDS_PER_HOUR / SECONDS_PER_MINUTE
        val remainingSeconds = seconds % SECONDS_PER_MINUTE
        return buildList {
            if (hours > 0) add("${hours}h")
            if (minutes > 0) add("${minutes}m")
            if (remainingSeconds > 0 || isEmpty()) add("${remainingSeconds}s")
        }.joinToString(" ")
    }

    private fun compact(value: Long, divisor: Long, suffix: String): String {
        val amount = floor(value.toDouble() / divisor * DECIMAL_SCALE) / DECIMAL_SCALE
        val formatted = String.format(Locale.US, "%.1f", amount).removeSuffix(".0")
        return "$formatted$suffix"
    }

    private const val SECONDS_PER_MINUTE = 60L
    private const val SECONDS_PER_HOUR = 3_600L
    private const val THOUSAND = 1_000L
    private const val MILLION = 1_000_000L
    private const val BILLION = 1_000_000_000L
    private const val DECIMAL_SCALE = 10.0
}
