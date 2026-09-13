package com.skysoft.config

import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName
import com.skysoft.data.hypixel.SkysoftGame.SKYBLOCK
import io.github.notenoughupdates.moulconfig.annotations.Accordion
import io.github.notenoughupdates.moulconfig.annotations.Category
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorDraggableList
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorText
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption
import io.github.notenoughupdates.moulconfig.observer.Property

class LootFeatureConfig {
    @JvmField
    @field:Expose
    @field:SerializedName(value = "profitTrackers", alternate = ["profitTracker"])
    @field:ConfigGames(SKYBLOCK)
    @field:Category(name = "Profit Trackers", desc = "Configure activity profit trackers.")
    val profitTrackers = ProfitTrackersConfig()

    @JvmField
    @field:Expose
    @field:ConfigGames(SKYBLOCK)
    @field:Category(name = "Item Change Log", desc = "Show recent item gains and losses.")
    val itemChangeLog = ItemChangeLogConfig()

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

    fun isAnyRareLootFeatureEnabled(): Boolean =
        rareDropTitles.enabled ||
            (rareLootSharing.enabled && rareLootSharing.settings.channels.get().isNotEmpty())
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
