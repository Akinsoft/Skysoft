package com.skysoft.features.waypoints

import com.skysoft.data.SkyBlockIsland
import com.skysoft.utils.ColorUtilities.toChromaColor
import com.skysoft.utils.SkysoftChat
import com.skysoft.utils.WorldVec
import java.awt.Color
import java.util.UUID

internal data class WaypointPoint(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val x: Double,
    val y: Double,
    val z: Double,
    val enabled: Boolean = true,
    val color: Int? = null,
    val chromaMillis: Int = 0,
    val styles: Set<WaypointStyle>? = null,
    val radius: Double? = null,
) {
    val customColor = color?.let { Color(it).toChromaColor().copy(timeForFullRotationInMillis = chromaMillis) }
    val position: WorldVec get() = WorldVec(x, y, z)
    val destination: WorldVec get() = WorldVec(x + BLOCK_CENTER, y + 1.0, z + BLOCK_CENTER)

    fun label(index: Int): String = name.ifBlank { "#${index + 1}" }
}

internal data class WaypointGroup(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val island: SkyBlockIsland,
    val points: List<WaypointPoint> = emptyList(),
    val enabled: Boolean = true,
    val route: Boolean = false,
    val loop: Boolean = false,
    val skipAhead: Boolean = false,
    val radius: Double = DEFAULT_WAYPOINT_RADIUS,
    val color: Int = DEFAULT_WAYPOINT_COLOR,
    val styles: Set<WaypointStyle> = setOf(WaypointStyle.MARKER),
    val filled: Boolean = false,
    val lines: Boolean = true,
    val scope: WaypointScope = WaypointScope.ISLAND,
    val profile: String? = null,
    val visit: String? = null,
) {
    val scopeLabel: String get() = when (scope) {
        WaypointScope.ISLAND -> "All profiles"
        WaypointScope.PROFILE -> profile?.substringAfter('/') ?: "Profile required"
        WaypointScope.VISIT -> "This visit"
    }

    fun pointColor(point: WaypointPoint, index: Int): Int =
        point.customColor?.getEffectiveColourRGB() ?: defaultPointColor(index)

    fun defaultPointColor(index: Int): Int = if (color == DEFAULT_WAYPOINT_COLOR) {
        SkysoftChat.mix(SkysoftChat.PREFIX_LEFT, SkysoftChat.PREFIX_RIGHT, index.toFloat() / points.lastIndex.coerceAtLeast(1))
    } else color

    fun pointStyles(point: WaypointPoint): Set<WaypointStyle> = point.styles ?: styles

    fun pointTarget(point: WaypointPoint): WorldVec =
        if (WaypointStyle.BLOCK in pointStyles(point)) point.position.blockCenter() else point.destination
}

internal enum class WaypointStyle(val label: String) {
    MARKER("Marker"),
    BLOCK("Block"),
    BEACON("Beacon"),
}

internal enum class WaypointScope(val label: String) {
    ISLAND("All profiles"),
    PROFILE("This profile"),
    VISIT("This visit"),
}

internal data class WaypointSelection(val groupId: String? = null, val pointId: String? = null)

internal data class WaypointImport(
    val source: String,
    val groups: List<WaypointGroup>,
    val warnings: List<String> = emptyList(),
    val needsIsland: Set<String> = emptySet(),
)

internal const val DEFAULT_WAYPOINT_COLOR = 0x45A3FF
internal const val DEFAULT_WAYPOINT_RADIUS = 3.0
internal const val MIN_WAYPOINT_RADIUS = 0.25
internal const val MAX_WAYPOINT_RADIUS = 32.0
internal const val MAX_WAYPOINT_GROUPS = 256
internal const val MAX_WAYPOINT_POINTS = 20000
internal const val MAX_WAYPOINT_NAME = 80
internal const val MAX_WAYPOINT_COORDINATE = 30000000.0
internal const val BLOCK_CENTER = 0.5
