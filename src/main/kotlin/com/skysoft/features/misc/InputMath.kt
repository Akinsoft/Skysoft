package com.skysoft.features.misc

import com.skysoft.config.SkysoftConfigGui
import com.skysoft.data.hypixel.HypixelLocationState
import com.skysoft.utils.calculateSkyBlockExpression
import java.math.RoundingMode

object InputMath {
    @JvmStatic
    fun compile(lines: Array<String>): String? {
        if (!SkysoftConfigGui.config().misc.inputMath ||
            !HypixelLocationState.inSkyBlock ||
            !isNumberInput(lines)
        ) {
            return null
        }
        val result = calculateSkyBlockExpression(lines[0]) ?: return null
        val decimalPlaces = if (lines[2].contains("price", ignoreCase = true)) 2 else 0
        return result.setScale(decimalPlaces, RoundingMode.HALF_UP)
            .stripTrailingZeros()
            .toPlainString()
            .takeIf { it.length <= MAX_INPUT_LENGTH }
    }

    private fun isNumberInput(lines: Array<String>): Boolean {
        if (lines.size < SIGN_LINE_COUNT) return false
        val prompt = lines[2]
        return when (lines[1]) {
            FULL_INPUT_MARKER -> !prompt.endsWith("your") && !prompt.endsWith("query")
            SHORT_INPUT_MARKER -> !prompt.endsWith("your") && prompt != SET_NAME_PROMPT
            FLIPPING_INPUT_MARKER -> true
            else -> false
        }
    }

    private const val FULL_INPUT_MARKER = "^^^^^^^^^^^^^^^"
    private const val SHORT_INPUT_MARKER = "^^^^^^"
    private const val FLIPPING_INPUT_MARKER = "^^Flipping^^"
    private const val SET_NAME_PROMPT = "Enter name"
    private const val SIGN_LINE_COUNT = 4
    private const val MAX_INPUT_LENGTH = 15
}
