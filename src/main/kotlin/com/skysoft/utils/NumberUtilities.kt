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

    data class CompactNumber(val value: Double, val approximate: Boolean)

    fun Int.addSeparators(): String = integerFormat.get().format(this)
    fun Long.addSeparators(): String = integerFormat.get().format(this)
    fun Double.addSeparators(): String = integerFormat.get().format(this)

    fun String.formatInt(): Int = replace(",", "").toInt()

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
        val number = if (multiplier == 1.0) clean else clean.dropLast(1)
        return number.toDoubleOrNull()?.let { CompactNumber(it * multiplier, approximate = multiplier != 1.0) }
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
        val absoluteValue = abs(this)
        val suffix = when {
            absoluteValue >= BILLION -> "b" to BILLION
            absoluteValue >= MILLION -> "m" to MILLION
            absoluteValue >= THOUSAND -> "k" to THOUSAND
            else -> return addSeparators()
        }
        val value = this / suffix.second
        val pattern = if (abs(value) >= SHORT_WHOLE_NUMBER_THRESHOLD) "0" else "0.#"
        return DecimalFormat(pattern, DecimalFormatSymbols(Locale.US)).format(value) + suffix.first
    }

    fun Double.coinAmountFormat(): String = shortDecimalFormat.get().format(this)

    fun Double.coinFormat(): String = when {
        abs(this) >= BILLION -> shortDecimalFormat.get().format(this / BILLION) + "b"
        abs(this) >= MILLION -> shortDecimalFormat.get().format(this / MILLION) + "m"
        abs(this) >= THOUSAND -> shortDecimalFormat.get().format(this / THOUSAND) + "k"
        else -> shortDecimalFormat.get().format(this)
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
