package com.skysoft.features.waypoints

import com.skysoft.features.waypoints.codec.WaypointerDecoder
import com.skysoft.features.waypoints.codec.WaypointerRouteData

internal object WaypointerImport {
    fun decode(text: String): WaypointImport {
        val decoded = WaypointerDecoder.decode(text)
        val context = WaypointImportContext("Waypointer V10")
        context.warnings += decoded.warnings
        val groups = decoded.groups.mapIndexed { index, group ->
            val name = group.name.ifBlank { decoded.label.ifBlank { "Imported route ${index + 1}" } }
            val points = group.points.mapIndexed { pointIndex, point ->
                if (point.flags and DISABLED_FLAG.inv() != 0L) {
                    context.warnings += "Waypointer display flags and subwaypoints become ordinary waypoint markers."
                }
                WaypointPoint(
                    name = context.name(point.name), x = point.x, y = point.y, z = point.z,
                    enabled = point.flags and DISABLED_FLAG == 0L, color = color(group, point, pointIndex),
                    radius = point.radius.takeIf { it > 0.0 }?.let(context::radius)
                )
            }
            context.group(name, group.zone, points, group.ordered).copy(
                radius = context.radius(group.radius), color = group.color, skipAhead = group.skipAhead,
            )
        }
        context.warnings += "Route progress and mod-wide settings are not imported."
        return context.finish(groups)
    }

    private fun color(group: WaypointerRouteData.Group, point: WaypointerRouteData.Point, index: Int): Int = when {
        point.flags and LOCKED_COLOR_FLAG != 0L -> point.color
        group.gradient == STATIC_GRADIENT -> group.color
        group.gradient == AUTO_GRADIENT -> {
            val ratio = if (group.points.size <= 1) 0.0 else index.toDouble() / (group.points.size - 1)
            COLOR_SHIFTS.fold(0) { color, shift ->
                val start = (group.startColor ushr shift) and CHANNEL_MASK
                val end = (group.endColor ushr shift) and CHANNEL_MASK
                color or ((start + (end - start) * ratio).toInt() shl shift)
            }
        }
        else -> point.color
    }

    private const val DISABLED_FLAG = 1L shl 20
    private const val LOCKED_COLOR_FLAG = 8L
    private const val STATIC_GRADIENT = 2
    private const val AUTO_GRADIENT = 1
    private const val CHANNEL_MASK = 255
    private val COLOR_SHIFTS = listOf(16, 8, 0)
}
