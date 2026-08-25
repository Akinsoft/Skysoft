package com.skysoft.utils

import java.math.BigDecimal
import java.math.MathContext
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

internal fun calculateSkyBlockExpression(expression: String): BigDecimal? {
    val normalized = expression.replace(",", "")
    if (normalized.none { it in "+-*/xX" } && !SKYBLOCK_SUFFIX_PATTERN.containsMatchIn(normalized)) return null
    return runCatching { SkyBlockMathParser(normalized).parse() }.getOrNull()
}

internal fun formatSkyBlockCalculation(expression: String, grouped: Boolean): String? =
    calculateSkyBlockExpression(expression)?.let { result ->
        DecimalFormat(
            if (grouped) "#,##0.##########" else "0.##########",
            DecimalFormatSymbols(Locale.US),
        ).format(result)
    }

private class SkyBlockMathParser(private val expression: String) {
    private var index = 0

    fun parse(): BigDecimal {
        val result = parseExpression()
        skipSpaces()
        require(index == expression.length)
        return result
    }

    private fun parseExpression(): BigDecimal {
        var result = parseTerm()
        while (true) {
            result = when (nextOperator('+', '-')) {
                '+' -> result + parseTerm()
                '-' -> result - parseTerm()
                else -> return result
            }
        }
    }

    private fun parseTerm(): BigDecimal {
        var result = parseFactor()
        while (true) {
            result = when (nextOperator('*', 'x', 'X', '/')) {
                '*', 'x', 'X' -> result * parseFactor()
                '/' -> result.divide(parseFactor(), MathContext.DECIMAL64)
                else -> return result
            }
        }
    }

    private fun parseFactor(): BigDecimal {
        skipSpaces()
        return when (expression.getOrNull(index)) {
            '+' -> {
                index++
                parseFactor()
            }
            '-' -> {
                index++
                -parseFactor()
            }
            '(' -> {
                index++
                val result = parseExpression()
                skipSpaces()
                require(expression.getOrNull(index++) == ')')
                result
            }
            else -> parseNumber()
        }
    }

    private fun parseNumber(): BigDecimal {
        skipSpaces()
        val start = index
        while (expression.getOrNull(index)?.let { it.isDigit() || it == '.' } == true) index++
        require(index > start)
        val value = expression.substring(start, index).toBigDecimal()
        val multiplier = when (expression.getOrNull(index)?.lowercaseChar()) {
            's' -> STACK_MULTIPLIER
            'e' -> ENCHANTED_MULTIPLIER
            'k' -> THOUSAND_MULTIPLIER
            'm' -> MILLION_MULTIPLIER
            'b' -> BILLION_MULTIPLIER
            else -> return value
        }
        index++
        return value * BigDecimal.valueOf(multiplier)
    }

    private fun nextOperator(vararg operators: Char): Char? {
        skipSpaces()
        val operator = expression.getOrNull(index)?.takeIf { it in operators } ?: return null
        index++
        return operator
    }

    private fun skipSpaces() {
        while (expression.getOrNull(index)?.isWhitespace() == true) index++
    }
}

private val SKYBLOCK_SUFFIX_PATTERN = Regex("""\d[sekmb]""", RegexOption.IGNORE_CASE)
private const val STACK_MULTIPLIER = 64L
private const val ENCHANTED_MULTIPLIER = 160L
private const val THOUSAND_MULTIPLIER = 1_000L
private const val MILLION_MULTIPLIER = 1_000_000L
private const val BILLION_MULTIPLIER = 1_000_000_000L
