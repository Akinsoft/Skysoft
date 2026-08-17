package com.skysoft.config

import com.google.gson.annotations.Expose
import com.skysoft.utils.ColorUtilities.toChromaColor
import com.skysoft.utils.SkysoftChat
import io.github.notenoughupdates.moulconfig.ChromaColour
import io.github.notenoughupdates.moulconfig.annotations.Accordion
import io.github.notenoughupdates.moulconfig.annotations.Category
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorColour
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorDraggableList
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorDropdown
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption
import io.github.notenoughupdates.moulconfig.observer.Property
import java.awt.Color

class FishingFeatureConfig {
    @JvmField
    @field:Expose
    @field:Category(name = "Catch Messages", desc = "Shorten and style Sea Creature catch messages.")
    val catchMessages = SeaCreatureCatchMessageConfig()

    @JvmField
    @field:Expose
    @field:Category(name = "Hotspot Radar", desc = "Find fishing hotspots with the Hotspot Radar.")
    val hotspotRadar = HotspotRadarConfig()

    @JvmField
    @field:Expose
    @field:Category(name = "Hotspot Sharing", desc = "Share and show fishing hotspots.")
    val hotspotSharing = HotspotSharingConfig()
}

class SeaCreatureCatchMessageConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Enabled", desc = "Replace Sea Creature catch chat with compact messages.")
    @field:MainFeatureToggle
    @field:ConfigEditorBoolean
    var enabled = false

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Settings", desc = "Catch message behavior.")
    @field:Accordion
    val settings = SeaCreatureCatchMessageSettingsConfig()

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Details", desc = "Catch message appearance.")
    @field:Accordion
    val details = SeaCreatureCatchMessageDetailsConfig()
}

class SeaCreatureCatchMessageSettingsConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Double Hook Position", desc = "Show Double Hook before or after the catch message.")
    @field:ConfigEditorDropdown
    var doubleHookPosition = DoubleHookMessagePosition.BEFORE
}

class SeaCreatureCatchMessageDetailsConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Catch Gradient Start", desc = "Starting color for You caught a.")
    @field:ConfigEditorColour
    val catchGradientStart: Property<ChromaColour> = Property.of(chatColor(SkysoftChat.MESSAGE_GRADIENT_START))

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Catch Gradient End", desc = "Ending color for You caught a.")
    @field:ConfigEditorColour
    val catchGradientEnd: Property<ChromaColour> = Property.of(chatColor(SkysoftChat.MESSAGE_GRADIENT_END))

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Catch Text Bold", desc = "Show You caught a in bold.")
    @field:ConfigEditorBoolean
    var catchTextBold = false

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Sea Creature Color", desc = "Color used for Sea Creature names.")
    @field:ConfigEditorColour
    val seaCreatureColor: Property<ChromaColour> = Property.of(ChromaColour.fromRGB(255, 85, 85, 0, 255))

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Sea Creature Bold", desc = "Show Sea Creature names in bold.")
    @field:ConfigEditorBoolean
    var seaCreatureBold = false

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Hotspot Color", desc = "Color used for Hotspot Sea Creature names.")
    @field:ConfigEditorColour
    val hotspotColor: Property<ChromaColour> = Property.of(ChromaColour.fromRGB(255, 85, 255, 0, 255))

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Hotspot Bold", desc = "Show Hotspot Sea Creature names in bold.")
    @field:ConfigEditorBoolean
    var hotspotBold = false

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Double Hook Color", desc = "Color used for Double Hook.")
    @field:ConfigEditorColour
    val doubleHookColor: Property<ChromaColour> = Property.of(ChromaColour.fromRGB(255, 255, 85, 0, 255))

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Double Hook Bold", desc = "Show Double Hook in bold.")
    @field:ConfigEditorBoolean
    var doubleHookBold = true
}

enum class DoubleHookMessagePosition(private val displayName: String) {
    BEFORE("Before Message"),
    AFTER("After Message"),
    ;

    override fun toString(): String = displayName
}

private fun chatColor(rgb: Int): ChromaColour = Color(rgb).toChromaColor()

class HotspotRadarConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Enabled", desc = "Show guesses from the Hotspot Radar.")
    @field:MainFeatureToggle
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
    @field:MainFeatureToggle
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
