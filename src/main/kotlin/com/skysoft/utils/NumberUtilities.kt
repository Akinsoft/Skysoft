package com.skysoft.utils

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.round

object NumberUtilities {
    private val integerFormat = ThreadLocal.withInitial { DecimalFormat("#,##0", DecimalFormatSymbols(Locale.US)) }
    private val shortDecimalFormat = ThreadLocal.withInitial { DecimalFormat("#,##0.#", DecimalFormatSymbols(Locale.US)) }

    data class CompactNumber(val value: Double, val approximate: Boolean, val resolution: Double)

    fun Int.addSeparators(): String = integerFormat.get().format(this)
    fun Long.addSeparators(): String = integerFormat.get().format(this)
    fun Double.addSeparators(): String = integerFormat.get().format(this)

    fun String.formatInt(): Int = replace(",", "").toInt()
    fun String.formatIntOrNull(): Int? = replace(",", "").toIntOrNull()
    fun String.formatLongOrNull(): Long? = replace(",", "").toLongOrNull()

    fun String.formatDoubleOrNull(): Double? = parseCompactNumberOrNull()?.value

    fun String.parseCompactNumberOrNull(): CompactNumber? {
        val clean = trim().replace(",", "")
        if (clean.isEmpty()) return null
        val multiplier = when (clean.last().lowercaseChar()) {
            'k' -> THOUSAND
            'm' -> MILLION
            'b' -> BILLION
            else -> 1.0
        }
        if (multiplier == 1.0) return clean.toDoubleOrNull()?.let { CompactNumber(it, approximate = false, resolution = 0.0) }
        val number = clean.dropLast(1)
        val resolution = multiplier / 10.0.pow(number.substringAfter('.', "").length)
        return number.toDoubleOrNull()?.let { CompactNumber(it * multiplier, approximate = true, resolution = resolution) }
    }

    fun String.romanToDecimal(): Int {
        var total = 0
        var previous = 0
        for (char in reversed()) {
            val value = ROMAN_NUMERAL_VALUES[char] ?: return 0
            if (value < previous) total -= value else total += value
            previous = value
        }
        return total
    }

    fun Int.romanNumeral(): String =
        ROMAN_NUMERALS.getOrNull(this - 1) ?: toString()

    fun Long.shortFormat(): String = toDouble().shortFormat()

    fun Double.shortFormat(): String {
        val (suffix, divisor) = compactSuffix() ?: return addSeparators()
        val value = this / divisor
        val pattern = if (abs(value) >= SHORT_WHOLE_NUMBER_THRESHOLD) "0" else "0.#"
        return DecimalFormat(pattern, DecimalFormatSymbols(Locale.US)).format(value) + suffix
    }

    fun Double.coinAmountFormat(): String = shortDecimalFormat.get().format(this)

    fun Double.coinFormat(): String {
        val (suffix, divisor) = compactSuffix() ?: return coinAmountFormat()
        return (this / divisor).coinAmountFormat() + suffix
    }

    private fun Double.compactSuffix(): Pair<String, Double>? = when {
        abs(this) >= BILLION -> "b" to BILLION
        abs(this) >= MILLION -> "m" to MILLION
        abs(this) >= THOUSAND -> "k" to THOUSAND
        else -> null
    }

    fun Double.signedCoinFormat(): String = (if (this >= 0.0) "+" else "-") + abs(this).coinFormat()

    fun Double.roundTo(decimals: Int): Double {
        val factor = 10.0.pow(decimals)
        return round(this * factor) / factor
    }

    private const val THOUSAND = 1_000.0
    private const val MILLION = 1_000_000.0
    private const val BILLION = 1_000_000_000.0
    private const val SHORT_WHOLE_NUMBER_THRESHOLD = 100

    private val ROMAN_NUMERAL_VALUES = mapOf(
        'I' to 1,
        'V' to 5,
        'X' to 10,
        'L' to 50,
    )
    private val ROMAN_NUMERALS = listOf(
        "I",
        "II",
        "III",
        "IV",
        "V",
        "VI",
        "VII",
        "VIII",
        "IX",
        "X",
    )
}
