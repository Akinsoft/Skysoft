package com.skysoft.features.waypoints

import com.skysoft.config.WaypointsConfigBounds
import com.skysoft.config.WaypointsDetailsConfig
import com.skysoft.config.WaypointsSettingsConfig
import kotlin.reflect.KMutableProperty1

internal enum class WaypointBinding(val label: String, private val property: KMutableProperty1<WaypointsSettingsConfig, Int>) {
    EDITOR("Open Display", WaypointsSettingsConfig::editorKey),
    FEET("Add at Feet", WaypointsSettingsConfig::feetKey),
    CROSSHAIR("Add at Crosshair", WaypointsSettingsConfig::crosshairKey),
    FOLLOW("Start / Pause", WaypointsSettingsConfig::followKey),
    NEXT("Next Point", WaypointsSettingsConfig::nextKey),
    PREVIOUS("Previous Point", WaypointsSettingsConfig::previousKey),
    STOP("Stop Route", WaypointsSettingsConfig::stopKey),
    ;

    fun value(): Int = property.get(Waypoints.config.settings)

    fun set(value: Int) = property.set(Waypoints.config.settings, value)
}

internal enum class WaypointSlider(
    val label: String,
    val range: IntRange,
    val step: Int,
    private val property: KMutableProperty1<WaypointsDetailsConfig, Int>,
) {
    UPCOMING("Upcoming Points", 0..WaypointsConfigBounds.MAX_UPCOMING_POINTS, 1, WaypointsDetailsConfig::upcomingPoints),
    DISTANCE(
        "Render Distance",
        WaypointsConfigBounds.MIN_RENDER_DISTANCE..WaypointsConfigBounds.MAX_RENDER_DISTANCE,
        WaypointsConfigBounds.RENDER_DISTANCE_STEP,
        WaypointsDetailsConfig::renderDistance,
    ),
    ;

    fun value(): Int = property.get(Waypoints.config.details)

    fun set(value: Int) = property.set(Waypoints.config.details, value.coerceIn(range))
}

internal enum class WaypointDetailToggle(val label: String, private val property: KMutableProperty1<WaypointsDetailsConfig, Boolean>) {
    ROUTE_CONTROLS("Route Controls", WaypointsDetailsConfig::showRouteControls),
    PLACEMENT_CONTROLS("Placement Controls", WaypointsDetailsConfig::showPlacementControls),
    PLACEMENT_PREVIEW("Placement Preview", WaypointsDetailsConfig::showPlacementPreview),
    NAMES("Show Names", WaypointsDetailsConfig::showNames),
    DISTANCE("Show Distance", WaypointsDetailsConfig::showDistance),
    BACKGROUND("Label Background", WaypointsDetailsConfig::labelBackground),
    ;

    fun isEnabled(): Boolean = property.get(Waypoints.config.details)

    fun toggle() = property.set(Waypoints.config.details, !isEnabled())

    companion object {
        val visibleEntries: List<WaypointDetailToggle> get() = entries.filter {
            it != ROUTE_CONTROLS || Waypoints.group?.route == true
        }
    }
}
