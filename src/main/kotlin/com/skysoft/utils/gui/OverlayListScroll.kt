package com.skysoft.utils.gui

internal object OverlayListScroll {
    fun nextOffset(current: Int, verticalAmount: Double, maximumOffset: Int): Int =
        (current + if (verticalAmount < 0.0) 1 else -1).coerceIn(0, maximumOffset)

    fun indicator(hiddenAbove: Int, hiddenBelow: Int): String = when {
        hiddenBelow > 0 -> "§7$hiddenBelow more..."
        hiddenAbove > 0 -> "§7$hiddenAbove above..."
        else -> ""
    }
}
