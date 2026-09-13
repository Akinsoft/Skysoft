package com.skysoft.config

import com.google.gson.annotations.Expose
import com.skysoft.config.core.ConfigRepairable
import com.skysoft.features.waypoints.WaypointPanel
import io.github.notenoughupdates.moulconfig.annotations.Accordion
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorButton
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorKeybind
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorSlider
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption
import org.lwjgl.glfw.GLFW

class WaypointsConfig : ConfigRepairable {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Enabled", desc = "Save places and follow your waypoint routes on each SkyBlock island.")
    @field:MainFeatureToggle
    @field:ConfigEditorBoolean
    var enabled = false

    @JvmField
    @field:ConfigOption(name = "Waypoints Display", desc = "Open the Waypoints Display. Also available with /ss waypoints.")
    @field:ConfigEditorButton(buttonText = "Open")
    val openEditor = Runnable { WaypointPanel.open() }

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Settings", desc = "Waypoint editing controls.")
    @field:Accordion
    val settings = WaypointsSettingsConfig()

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Details", desc = "Waypoint and route appearance.")
    @field:Accordion
    val details = WaypointsDetailsConfig()

    @JvmField
    @field:Expose
    var editorX = -1

    @JvmField
    @field:Expose
    var editorY = -1

    override fun repairLoadedValues() {
        details.upcomingPoints = details.upcomingPoints.coerceIn(0, WaypointsConfigBounds.MAX_UPCOMING_POINTS)
        details.renderDistance = details.renderDistance.coerceIn(
            WaypointsConfigBounds.MIN_RENDER_DISTANCE,
            WaypointsConfigBounds.MAX_RENDER_DISTANCE,
        )
    }
}

class WaypointsSettingsConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Open Display", desc = "Open the Waypoints Display.")
    @field:ConfigEditorKeybind(defaultKey = GLFW.GLFW_KEY_UNKNOWN)
    var editorKey = GLFW.GLFW_KEY_UNKNOWN

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Add at Feet", desc = "Add the block under your feet to the selected preset.")
    @field:ConfigEditorKeybind(defaultKey = GLFW.GLFW_KEY_UNKNOWN)
    var feetKey = GLFW.GLFW_KEY_UNKNOWN

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Add at Crosshair", desc = "Add the block you are looking at to the selected preset.")
    @field:ConfigEditorKeybind(defaultKey = GLFW.GLFW_KEY_UNKNOWN)
    var crosshairKey = GLFW.GLFW_KEY_UNKNOWN

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Start / Pause", desc = "Start the selected route, or pause and resume it.")
    @field:ConfigEditorKeybind(defaultKey = GLFW.GLFW_KEY_UNKNOWN)
    var followKey = GLFW.GLFW_KEY_UNKNOWN

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Next Point", desc = "Skip to the next destination in the route you are following.")
    @field:ConfigEditorKeybind(defaultKey = GLFW.GLFW_KEY_UNKNOWN)
    var nextKey = GLFW.GLFW_KEY_UNKNOWN

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Previous Point", desc = "Return to the previous destination in the current route.")
    @field:ConfigEditorKeybind(defaultKey = GLFW.GLFW_KEY_UNKNOWN)
    var previousKey = GLFW.GLFW_KEY_UNKNOWN

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Stop Route", desc = "Stop following the current route.")
    @field:ConfigEditorKeybind(defaultKey = GLFW.GLFW_KEY_UNKNOWN)
    var stopKey = GLFW.GLFW_KEY_UNKNOWN
}

class WaypointsDetailsConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Route Controls", desc = "Show Start, Pause, Previous, Next, and Stop for ordered routes.")
    @field:ConfigEditorBoolean
    var showRouteControls = true

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Placement Controls", desc = "Show At Feet and At Crosshair in the Waypoints Display.")
    @field:ConfigEditorBoolean
    var showPlacementControls = false

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Placement Preview", desc = "Highlight the aimed block while editing, unless a route is active.")
    @field:ConfigEditorBoolean
    var showPlacementPreview = true

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Block Highlight", desc = "Appearance of the block targeted while editing waypoints.")
    @field:Accordion
    val blockHighlight = BlockOverlayDetailsConfig()

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Upcoming Points", desc = "Additional destinations shown ahead of the current route point.")
    @field:ConfigEditorSlider(minValue = 0f, maxValue = WaypointsConfigBounds.MAX_UPCOMING_POINTS.toFloat(), minStep = 1f)
    var upcomingPoints = 2

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Render Distance", desc = "Maximum distance at which waypoints appear.")
    @field:ConfigEditorSlider(
        minValue = WaypointsConfigBounds.MIN_RENDER_DISTANCE.toFloat(),
        maxValue = WaypointsConfigBounds.MAX_RENDER_DISTANCE.toFloat(),
        minStep = WaypointsConfigBounds.RENDER_DISTANCE_STEP.toFloat(),
    )
    var renderDistance = 512

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Show Names", desc = "Show waypoint names beside their markers.")
    @field:ConfigEditorBoolean
    var showNames = true

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Show Distance", desc = "Show the distance to each visible waypoint.")
    @field:ConfigEditorBoolean
    var showDistance = false

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Label Background", desc = "Draw a dark background behind waypoint labels.")
    @field:ConfigEditorBoolean
    var labelBackground = false
}

internal object WaypointsConfigBounds {
    const val MAX_UPCOMING_POINTS = 10
    const val MIN_RENDER_DISTANCE = 32
    const val MAX_RENDER_DISTANCE = 2048
    const val RENDER_DISTANCE_STEP = 16
}
