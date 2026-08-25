package com.skysoft.config

import com.google.gson.annotations.Expose
import com.skysoft.config.core.ConfigRepairable
import com.skysoft.data.SkyBlockIsland
import com.skysoft.data.hypixel.SkysoftGame.SKYBLOCK
import io.github.notenoughupdates.moulconfig.annotations.Accordion
import io.github.notenoughupdates.moulconfig.annotations.Category
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorDraggableList
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorText
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption
import io.github.notenoughupdates.moulconfig.observer.Property

class MiscFeatureConfig : ConfigRepairable {
    @JvmField
    @field:Expose
    @field:Category(name = "Block Overlay", desc = "Customize the targeted block highlight.")
    val blockOverlay = BlockOverlayConfig()

    @JvmField
    @field:Expose
    @field:Category(name = "Auto Sprint", desc = "Automatically sprint under configurable conditions.")
    val autoSprint = AutoSprintConfig()

    @JvmField
    @field:Expose
    @field:Category(name = "Mouse Lock", desc = "Lock mouse movement and show its status.")
    val mouseLock = MouseLockConfig()

    @JvmField
    @field:Expose
    @field:Category(name = "Zoom", desc = "Magnify the camera with configurable controls.")
    val zoom = ZoomConfig()

    @JvmField
    @field:Expose
    @field:ConfigGames(SKYBLOCK)
    @field:Category(name = "Rare Drop Titles", desc = "Show valuable rare drops as titles.")
    val rareDropTitles = RareDropTitlesConfig()

    @JvmField
    @field:Expose
    @field:ConfigGames(SKYBLOCK)
    @field:Category(name = "Rare Loot Sharing", desc = "Share valuable drops in selected chat channels.")
    val rareLootSharing = RareLootSharingConfig()

    @JvmField
    @field:Expose
    @field:ConfigGames(SKYBLOCK)
    @field:Category(name = "Dropped Item Scaling", desc = "Customize dropped SkyBlock item sizes by rarity.")
    val droppedItemScaling = DroppedItemScalingConfig()

    @JvmField
    @field:Expose
    @field:ConfigGames(SKYBLOCK)
    @field:Category(name = "Keep Terrain Loaded", desc = "Keep visited terrain beyond the server's view distance loaded.")
    val keepTerrainLoaded = KeepTerrainLoadedConfig()

    @JvmField
    @field:Expose
    @field:ConfigGames(SKYBLOCK)
    @field:ConfigOption(
        name = "Input Math",
        desc = "Calculate equations in SkyBlock number inputs when pressing Enter or Done.",
    )
    @field:MainFeatureToggle
    @field:ConfigEditorBoolean
    var inputMath = false

    @JvmField
    @field:Expose
    @field:ConfigGames(SKYBLOCK)
    @field:ConfigOption(
        name = "Short Warp Commands",
        desc = "Use warp names such as /garden and /crypts without typing /warp.",
    )
    @field:MainFeatureToggle
    @field:ConfigEditorBoolean
    var shortWarpCommands = false

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
    @field:ConfigOption(
        name = "Keep SkyBlock Resource Pack",
        desc = "Keep Hypixel's SkyBlock resource pack loaded between servers.",
    )
    @field:MainFeatureToggle
    @field:ConfigEditorBoolean
    var keepSkyBlockResourcePack = false

    fun isAnyRareLootFeatureEnabled(): Boolean =
        rareDropTitles.enabled ||
            (rareLootSharing.enabled && rareLootSharing.settings.channels.get().isNotEmpty())

    override fun repairLoadedValues() {
        droppedItemScaling.repairLoadedValues()
        zoom.repairLoadedValues()
    }
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

class RareDropTitlesConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Enabled", desc = "Show valuable rare drops as titles.")
    @field:MainFeatureToggle
    @field:ConfigEditorBoolean
    var enabled = false

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Settings", desc = "Rare drop title settings.")
    @field:Accordion
    val settings = RareDropTitlesSettingsConfig()
}

class RareDropTitlesSettingsConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Minimum Value", desc = "Minimum coin value needed to show a title.")
    @field:ConfigEditorText
    var minimumValue = "2,000,000"
}

class RareLootSharingConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Enabled", desc = "Share valuable drops in selected chat channels.")
    @field:MainFeatureToggle
    @field:ConfigEditorBoolean
    var enabled = false

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Settings", desc = "Rare loot sharing settings.")
    @field:Accordion
    val settings = RareLootSharingSettingsConfig()
}

class RareLootSharingSettingsConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Channels", desc = "Chat channels where Skysoft should share valuable drops.")
    @field:ConfigEditorDraggableList
    val channels: Property<MutableList<RareLootShareChannel>> =
        Property.of(mutableListOf(RareLootShareChannel.PARTY))

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Rare Loot Value", desc = "Minimum coin value to share.")
    @field:ConfigEditorText
    var rareLootValue = "1,000,000"
}

enum class RareLootShareChannel(private val displayName: String) {
    PARTY("Party"),
    GUILD("Guild"),
    ;

    override fun toString(): String = displayName
}
