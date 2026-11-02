package com.skysoft.features.inventory.itemlist

import com.skysoft.utils.DurationParts
import java.util.Locale
import kotlin.math.floor

internal object ItemListFormatting {
    fun compactNumber(value: Long): String = when {
        value >= BILLION -> compact(value, BILLION, "b")
        value >= MILLION -> compact(value, MILLION, "m")
        value >= THOUSAND -> compact(value, THOUSAND, "k")
        else -> value.toString()
    }

    fun duration(seconds: Long): String {
        val duration = DurationParts.fromSeconds(seconds)
        return buildList {
            if (duration.totalHours > 0) add("${duration.totalHours}h")
            if (duration.minutes > 0) add("${duration.minutes}m")
            if (duration.seconds > 0 || isEmpty()) add("${duration.seconds}s")
        }.joinToString(" ")
    }

    private fun compact(value: Long, divisor: Long, suffix: String): String {
        val amount = floor(value.toDouble() / divisor * DECIMAL_SCALE) / DECIMAL_SCALE
        val formatted = String.format(Locale.US, "%.1f", amount).removeSuffix(".0")
        return "$formatted$suffix"
    }

    private const val THOUSAND = 1_000L
    private const val MILLION = 1_000_000L
    private const val BILLION = 1_000_000_000L
    private const val DECIMAL_SCALE = 10.0
}
