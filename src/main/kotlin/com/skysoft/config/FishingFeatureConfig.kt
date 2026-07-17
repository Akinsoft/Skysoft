package com.skysoft.config

import com.google.gson.annotations.Expose
import io.github.notenoughupdates.moulconfig.annotations.Accordion
import io.github.notenoughupdates.moulconfig.annotations.Category
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorDraggableList
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorDropdown
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption
import io.github.notenoughupdates.moulconfig.observer.Property

class FishingFeatureConfig {
    @JvmField
    @field:Expose
    @field:Category(name = "Hotspot Radar", desc = "Find fishing hotspots with the Hotspot Radar.")
    val hotspotRadar = HotspotRadarConfig()

    @JvmField
    @field:Expose
    @field:Category(name = "Hotspot Sharing", desc = "Share and show fishing hotspots.")
    val hotspotSharing = HotspotSharingConfig()
}

class HotspotRadarConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Enabled", desc = "Show guesses from the Hotspot Radar.")
    @field:ConfigEditorBoolean
    var enabled = false

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Details", desc = "Hotspot Radar visual details.")
    @field:Accordion
    val details = HotspotRadarDetailsConfig()
}

class HotspotRadarDetailsConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Crosshair Line", desc = "Draw a line to the radar guess.")
    @field:ConfigEditorBoolean
    var crosshairLine = true
}

class HotspotSharingConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Share Hotspots", desc = "Automatically share fishing hotspots in chat.")
    @field:ConfigEditorBoolean
    var shareHotspots = false

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Settings", desc = "Shared hotspot settings.")
    @field:Accordion
    val settings = HotspotSharingSettingsConfig()

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Details", desc = "Shared hotspot visual settings.")
    @field:Accordion
    val details = HotspotSharingDetailsConfig()
}

class HotspotSharingSettingsConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Shared Hotspots", desc = "Hotspot types Skysoft should share.")
    @field:ConfigEditorDraggableList
    val sharedHotspots: Property<MutableList<FishingHotspotType>> = Property.of(defaultFishingHotspotTypes())

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Received Hotspots", desc = "Hotspot pings Skysoft should show.")
    @field:ConfigEditorDraggableList
    val receivedHotspots: Property<MutableList<FishingHotspotType>> = Property.of(defaultFishingHotspotTypes())

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Show Shared Waypoints", desc = "Show waypoints from hotspot share messages.")
    @field:ConfigEditorBoolean
    var showSharedWaypoints = true
}

class HotspotSharingDetailsConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Bold Text", desc = "Use bold hotspot labels.")
    @field:ConfigEditorBoolean
    var boldText = true

    @JvmField
    @field:Expose
    @field:ConfigOption(
        name = "Hotspot Label Format",
        desc = """Examples:
HOTSPOT
hotspot
Hotspot""",
    )
    @field:ConfigEditorDropdown
    var labelFormat = WaypointLabelFormat.CAPS

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Crosshair Line", desc = "Draw a line to shared hotspot waypoints.")
    @field:ConfigEditorBoolean
    var crosshairLine = true
}

enum class FishingHotspotType(
    private val displayName: String,
    private vararg val statAliases: String,
) {
    FISHING_SPEED("Fishing Speed"),
    SEA_CREATURE_CHANCE("Sea Creature Chance"),
    DOUBLE_HOOK_CHANCE("Double Hook Chance"),
    TROPHY_FISH_CHANCE("Trophy Fish Chance", "Trophy Chance"),
    TREASURE_CHANCE("Treasure Chance"),
    ;

    override fun toString(): String = displayName

    companion object {
        fun fromStat(stat: String): FishingHotspotType? = entries.firstOrNull { type ->
            (listOf(type.displayName) + type.statAliases).any { alias ->
                stat.endsWith(alias, ignoreCase = true)
            }
        }
    }
}

private fun defaultFishingHotspotTypes(): MutableList<FishingHotspotType> =
    FishingHotspotType.entries.toMutableList()
