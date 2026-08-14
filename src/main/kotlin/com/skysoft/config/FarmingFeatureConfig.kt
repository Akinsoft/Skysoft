package com.skysoft.config

import com.google.gson.annotations.Expose
import io.github.notenoughupdates.moulconfig.annotations.Accordion
import io.github.notenoughupdates.moulconfig.annotations.Category
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorDropdown
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption

class FarmingFeatureConfig {
    @JvmField
    @field:Expose
    @field:Category(name = "No Crop Rotation", desc = "Remove coordinate-based visual variation from crops.")
    val noCropRotation = NoCropRotationConfig()
}

class NoCropRotationConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(
        name = "Enabled",
        desc = "Render crops without coordinate-based offsets or randomized model rotations.",
    )
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
