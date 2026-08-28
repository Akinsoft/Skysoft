package com.skysoft.features.event.diana

import com.skysoft.utils.WorldVec

internal enum class DianaArrowDistance(
    val particleOffset: WorldVec,
    val minDistance: Int,
    val maxDistance: Int,
) {
    // Dust offsets are protocol color data; these names are the visual arrow colors.
    YELLOW(WorldVec(0.0, 128.0, 0.0), 0, 117),
    RED(WorldVec(255.0, 255.0, 0.0), 112, 282),
    BLACK(WorldVec(255.0, 0.0, 0.0), 281, 600),
}
