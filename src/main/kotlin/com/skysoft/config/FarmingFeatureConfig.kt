package com.skysoft.config

import com.google.gson.annotations.Expose
import io.github.notenoughupdates.moulconfig.annotations.Accordion
import io.github.notenoughupdates.moulconfig.annotations.Category
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorDropdown
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorSlider
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorText
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption

class FarmingFeatureConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Highlight Pests", desc = "Highlight visible Pests in the Garden.")
    @field:MainFeatureToggle
    @field:ConfigEditorBoolean
    var highlightPests = false

    @JvmField
    @field:Expose
    @field:Category(name = "Pest Cooldown Warning", desc = "Warn before Pests can spawn again.")
    val pestSpawnCooldownWarning = PestSpawnCooldownWarningConfig()

    @JvmField
    @field:Expose
    @field:Category(name = "No Crop Rotation", desc = "Remove coordinate-based visual variation from crops.")
    val noCropRotation = NoCropRotationConfig()
}

class PestSpawnCooldownWarningConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Enabled", desc = "Show a title before Pests can spawn again.")
    @field:MainFeatureToggle
    @field:ConfigEditorBoolean
    var enabled = false

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Settings", desc = "Pest Cooldown Warning settings.")
    @field:Accordion
    val settings = PestSpawnCooldownWarningSettingsConfig()

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Details", desc = "Customize Pest Cooldown Warning titles.")
    @field:Accordion
    val details = PestSpawnCooldownWarningDetailsConfig()
}

class PestSpawnCooldownWarningSettingsConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(
        name = "Warning Time",
        desc = "Seconds before Pests can spawn to show the warning. Set to 0 to warn when they can spawn.",
    )
    @field:ConfigEditorSlider(minValue = 0f, maxValue = 20f, minStep = 1f)
    var warningSeconds = 2
}

class PestSpawnCooldownWarningDetailsConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Ready Text", desc = "Title shown when Pests can spawn.")
    @field:ConfigEditorText
    var readyText = "Pests can spawn again!"

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Early Text", desc = "Title shown before Pests can spawn.")
    @field:ConfigEditorText
    var earlyText = "Pests can spawn soon!"
}

class NoCropRotationConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(
        name = "Enabled",
        desc = "Render crops without coordinate-based offsets or randomized model rotations.",
    )
    @field:MainFeatureToggle
    @field:ConfigEditorBoolean
    var enabled = false

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Settings", desc = "No Crop Rotation settings.")
    @field:Accordion
    val settings = NoCropRotationSettingsConfig()
}

class NoCropRotationSettingsConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Location", desc = "Choose where No Crop Rotation is enabled.")
    @field:ConfigEditorDropdown
    var location = NoCropRotationLocation.ONLY_IN_GARDEN
}

enum class NoCropRotationLocation(private val displayName: String) {
    ONLY_IN_GARDEN("Only in Garden"),
    EVERYWHERE("Everywhere"),
    ;

    override fun toString(): String = displayName
}
