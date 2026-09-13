package com.skysoft.config

import com.google.gson.annotations.Expose
import com.skysoft.config.core.ConfigRepairable
import com.skysoft.data.SkyBlockIsland
import com.skysoft.data.hypixel.SkysoftGame.SKYBLOCK
import io.github.notenoughupdates.moulconfig.ChromaColour
import io.github.notenoughupdates.moulconfig.annotations.Accordion
import io.github.notenoughupdates.moulconfig.annotations.Category
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorColour
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorDraggableList
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption
import io.github.notenoughupdates.moulconfig.observer.Property

class WorldFeatureConfig : ConfigRepairable {
    @JvmField
    @field:Expose
    @field:ConfigGames(SKYBLOCK)
    @field:Category(name = "Waypoints", desc = "Save places and build routes for each SkyBlock island.")
    val waypoints = WaypointsConfig()

    @JvmField
    @field:Expose
    @field:Category(name = "Held Item", desc = "Customize first-person held item visuals and swing duration.")
    val heldItem = HeldItemConfig()

    @JvmField
    @field:Expose
    @field:Category(name = "Zoom", desc = "Magnify the camera with configurable controls.")
    val zoom = ZoomConfig()

    @JvmField
    @field:Expose
    @field:Category(name = "Block Overlay", desc = "Customize the targeted block highlight.")
    val blockOverlay = BlockOverlayConfig()

    @JvmField
    @field:Expose
    @field:Category(name = "Sky Color", desc = "Choose a custom color for the sky.")
    val skyColor = SkyColorConfig()

    @JvmField
    @field:Expose
    @field:ConfigGames(SKYBLOCK)
    @field:Category(name = "Dropped Item Scaling", desc = "Customize dropped SkyBlock item sizes by rarity.")
    val droppedItemScaling = DroppedItemScalingConfig()

    @JvmField
    @field:Expose
    @field:ConfigOption(
        name = "Hide Dead Entities",
        desc = "Hide entities during their death animation.",
    )
    @field:MainFeatureToggle
    @field:ConfigEditorBoolean
    var hideDeadEntities = false

    @JvmField
    @field:Expose
    @field:ConfigGames(SKYBLOCK)
    @field:Category(name = "Keep Terrain Loaded", desc = "Keep visited terrain beyond the server's view distance loaded.")
    val keepTerrainLoaded = KeepTerrainLoadedConfig()

    @JvmField
    @field:Expose
    @field:ConfigGames(SKYBLOCK)
    @field:ConfigOption(
        name = "Keep SkyBlock Resource Pack",
        desc = "Keep Hypixel's SkyBlock resource pack loaded between servers.",
    )
    @field:MainFeatureToggle
    @field:ConfigEditorBoolean
    var keepSkyBlockResourcePack = false

    override fun repairLoadedValues() {
        waypoints.repairLoadedValues()
        heldItem.repairLoadedValues()
        droppedItemScaling.repairLoadedValues()
        zoom.repairLoadedValues()
    }
}

class SkyColorConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Enabled", desc = "Use a custom color for the sky.")
    @field:MainFeatureToggle
    @field:ConfigEditorBoolean
    var enabled = false

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Details", desc = "Sky color details.")
    @field:Accordion
    val details = SkyColorDetailsConfig()
}

class SkyColorDetailsConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Sky Color", desc = "Color used above the horizon.")
    @field:ConfigEditorColour
    val color: Property<ChromaColour> = Property.of(ChromaColour.fromRGB(120, 167, 255, 0, 255))

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Horizon Color", desc = "Color used for the horizon and distance fog.")
    @field:ConfigEditorColour
    val horizonColor: Property<ChromaColour> = Property.of(ChromaColour.fromRGB(120, 167, 255, 0, 255))

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Void Color", desc = "Color used below the horizon.")
    @field:ConfigEditorColour
    val voidColor: Property<ChromaColour> = Property.of(ChromaColour.fromRGB(0, 0, 0, 0, 255))
}

class KeepTerrainLoadedConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Enabled", desc = "Keep visited terrain beyond the server's view distance loaded.")
    @field:MainFeatureToggle
    @field:ConfigEditorBoolean
    var enabled = false

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Settings", desc = "Keep Terrain Loaded settings.")
    @field:Accordion
    val settings = KeepTerrainLoadedSettingsConfig()
}

class KeepTerrainLoadedSettingsConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Islands", desc = "SkyBlock islands where visited terrain should stay loaded.")
    @field:ConfigEditorDraggableList
    val islands: Property<MutableList<TerrainCacheIsland>> = Property.of(TerrainCacheIsland.entries.toMutableList())
}

enum class TerrainCacheIsland(val island: SkyBlockIsland) {
    THE_END(SkyBlockIsland.THE_END),
    DWARVEN_MINES(SkyBlockIsland.DWARVEN_MINES),
    GLACITE_TUNNELS(SkyBlockIsland.GLACITE_TUNNELS),
    DUNGEON_HUB(SkyBlockIsland.DUNGEON_HUB),
    HUB(SkyBlockIsland.HUB),
    THE_FARMING_ISLANDS(SkyBlockIsland.THE_FARMING_ISLANDS),
    CRYSTAL_HOLLOWS(SkyBlockIsland.CRYSTAL_HOLLOWS),
    THE_PARK(SkyBlockIsland.THE_PARK),
    DEEP_CAVERNS(SkyBlockIsland.DEEP_CAVERNS),
    GOLD_MINE(SkyBlockIsland.GOLD_MINE),
    GARDEN(SkyBlockIsland.GARDEN),
    SPIDERS_DEN(SkyBlockIsland.SPIDERS_DEN),
    JERRYS_WORKSHOP(SkyBlockIsland.JERRYS_WORKSHOP),
    THE_RIFT(SkyBlockIsland.THE_RIFT),
    CRIMSON_ISLE(SkyBlockIsland.CRIMSON_ISLE),
    BACKWATER_BAYOU(SkyBlockIsland.BACKWATER_BAYOU),
    GALATEA(SkyBlockIsland.GALATEA),
    TORRHUS_CANYON(SkyBlockIsland.TORRHUS_CANYON),
    SAFARI(SkyBlockIsland.SAFARI),
    LOTUS_ATOLL(SkyBlockIsland.LOTUS_ATOLL),
    ;

    override fun toString(): String = island.toString()
}
