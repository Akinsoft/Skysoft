package com.skysoft.config

import com.google.gson.annotations.Expose
import io.github.notenoughupdates.moulconfig.annotations.Accordion
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorDraggableList
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption
import io.github.notenoughupdates.moulconfig.annotations.ConfigVisibleIf
import io.github.notenoughupdates.moulconfig.observer.Property

class HideSillyButtonsConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Enabled", desc = "Hide selected Minecraft menu buttons.")
    @field:MainFeatureToggle
    @field:ConfigEditorBoolean
    var enabled = false

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Settings", desc = "Choose which buttons to hide.")
    @field:ConfigVisibleIf("enabled")
    @field:Accordion
    val settings = HideSillyButtonsSettingsConfig()

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Details", desc = "Menu button appearance.")
    @field:ConfigVisibleIf("enabled")
    @field:Accordion
    val details = HideSillyButtonsDetailsConfig()
}

class HideSillyButtonsDetailsConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Full-Width Mods Button", desc = "Show Mods as a full-width button above Options in the pause menu.")
    @field:ConfigEditorBoolean
    var fullWidthModsButton = true
}

class HideSillyButtonsSettingsConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Hidden Buttons", desc = "Add buttons to hide, or remove them to show them again.")
    @field:ConfigEditorDraggableList
    val buttons: Property<MutableList<HiddenMenuButton>> =
        Property.of(HiddenMenuButton.entries.filter { it != HiddenMenuButton.SINGLEPLAYER }.toMutableList())
}

enum class HiddenMenuButton(val translationKey: String, private val displayName: String) {
    MINECRAFT_REALMS("menu.online", "Minecraft Realms"),
    ACCESSIBILITY_SETTINGS("options.accessibility", "Accessibility Settings"),
    CHANGE_LANGUAGE("options.language", "Change Language"),
    FRIENDS("gui.friends.open", "Friends"),
    PLAYER_REPORTING("menu.playerReporting", "Player Reporting"),
    GIVE_FEEDBACK("menu.sendFeedback", "Give Feedback"),
    REPORT_BUGS("menu.reportBugs", "Report Bugs"),
    SERVER_LINKS("menu.server_links", "Server Links"),
    ADVANCEMENTS("gui.advancements", "Advancements"),
    STATISTICS("gui.stats", "Statistics"),
    SINGLEPLAYER("menu.singleplayer", "Singleplayer"),
    ;

    override fun toString(): String = displayName
}
