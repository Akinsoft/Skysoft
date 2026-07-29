package com.skysoft.gui

import kotlin.math.roundToInt

internal const val HUD_EDITOR_GRID_SPACING = 5

internal fun hudGridCoordinate(value: Int, spacing: Int, enabled: Boolean): Int =
    if (enabled) (value.toFloat() / spacing).roundToInt() * spacing else value

internal fun hudGridTarget(
    target: Int,
    currentAbsolute: Int,
    currentCoordinate: Int,
    spacing: Int,
    enabled: Boolean,
): Int {
    val targetCoordinate = currentCoordinate + target - currentAbsolute
    return target + hudGridCoordinate(targetCoordinate, spacing, enabled) - targetCoordinate
}
