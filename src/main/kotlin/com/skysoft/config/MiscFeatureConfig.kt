package com.skysoft.config

import com.google.gson.annotations.Expose
import com.skysoft.config.core.ConfigRepairable
import com.skysoft.data.hypixel.SkysoftGame.SKYBLOCK
import io.github.notenoughupdates.moulconfig.annotations.Accordion
import io.github.notenoughupdates.moulconfig.annotations.Category
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorText
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption

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
    @field:Category(name = "Rare Loot Sharing", desc = "Share valuable drops in party chat.")
    val rareLootSharing = RareLootSharingConfig()

    @JvmField
    @field:Expose
    @field:ConfigGames(SKYBLOCK)
    @field:Category(name = "Dropped Item Scaling", desc = "Customize dropped SkyBlock item sizes by rarity.")
    val droppedItemScaling = DroppedItemScalingConfig()

    @JvmField
    @field:Expose
    @field:ConfigGames(SKYBLOCK)
    @field:ConfigOption(
        name = "Short Warp Commands",
        desc = "Use warp names such as /garden and /crypts without typing /warp.",
    )
    @field:ConfigEditorBoolean
    var shortWarpCommands = false

    @JvmField
    @field:Expose
    @field:ConfigOption(
        name = "Hide Dead Entities",
        desc = "Hide entities during their death animation.",
    )
    @field:ConfigEditorBoolean
    var hideDeadEntities = false

    @JvmField
    @field:Expose
    @field:ConfigGames(SKYBLOCK)
    @field:ConfigOption(
        name = "Keep SkyBlock Resource Pack",
        desc = "Keep Hypixel's SkyBlock resource pack loaded between servers.",
    )
    @field:ConfigEditorBoolean
    var keepSkyBlockResourcePack = false

    fun isAnyRareLootFeatureEnabled(): Boolean = rareDropTitles.enabled || rareLootSharing.enabled

    override fun repairLoadedValues() {
        droppedItemScaling.repairLoadedValues()
        zoom.repairLoadedValues()
    }
}

class RareDropTitlesConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Enabled", desc = "Show valuable rare drops as titles.")
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
    @field:ConfigOption(name = "Enabled", desc = "Share valuable drops in party chat.")
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
    @field:ConfigOption(name = "Rare Loot Value", desc = "Minimum coin value to share.")
    @field:ConfigEditorText
    var rareLootValue = "1,000,000"
}
