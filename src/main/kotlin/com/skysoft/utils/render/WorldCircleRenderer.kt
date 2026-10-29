package com.skysoft.utils.render

import com.skysoft.utils.WorldVec
import java.awt.Color
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

object WorldCircleRenderer {
    fun drawHorizontalCircle(
        context: SkysoftRenderContext,
        center: WorldVec,
        radius: Double,
        color: Color,
        lineWidth: Int = DEFAULT_LINE_WIDTH,
        depth: Boolean = false,
    ) {
        if (radius <= 0.0) return
        val segments = max(MIN_SEGMENTS, (radius * SEGMENTS_PER_BLOCK).roundToInt())
        LineBoxRenderer.draw3D(context, lineWidth, depth) {
            var previous = point(center, radius, 0, segments)
            for (index in 1..segments) {
                val next = point(center, radius, index, segments)
                draw3DLine(previous, next, color)
                previous = next
            }
        }
    }

    private fun point(center: WorldVec, radius: Double, index: Int, segments: Int): WorldVec {
        val angle = index.toDouble() / segments.toDouble() * PI * FULL_TURN_MULTIPLIER
        return WorldVec(
            center.x + cos(angle) * radius,
            center.y,
            center.z + sin(angle) * radius,
        )
    }

    private const val DEFAULT_LINE_WIDTH = 2
    private const val MIN_SEGMENTS = 48
    private const val SEGMENTS_PER_BLOCK = 2.0
    private const val FULL_TURN_MULTIPLIER = 2.0
}
