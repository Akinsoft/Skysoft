package com.skysoft.config

import com.google.gson.annotations.Expose
import io.github.notenoughupdates.moulconfig.ChromaColour
import io.github.notenoughupdates.moulconfig.annotations.Accordion
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorColour
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorCombinations
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorInfoText
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption
import io.github.notenoughupdates.moulconfig.observer.Property

class BlockOverlayConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Enabled", desc = "Highlight the block you are targeting.")
    @field:ConfigEditorBoolean
    var enabled = false

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Settings", desc = "Block overlay settings.")
    @field:Accordion
    val settings = BlockOverlaySettingsConfig()

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Details", desc = "Block overlay visual details.")
    @field:Accordion
    val details = BlockOverlayDetailsConfig()
}

class BlockOverlaySettingsConfig {
    @JvmField
    @field:ConfigOption(
        name = "Item Conditions",
        desc = "Use §b/ss blockoverlay additem§7 while holding a SkyBlock item to add it as a condition.",
    )
    @field:ConfigEditorInfoText
    val itemConditionInfo: Unit = Unit

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Reverse Conditions", desc = "Matching combinations disable the overlay instead.")
    @field:ConfigEditorBoolean
    var isConditionActivationReversed = false

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Combinations", desc = "Any matching combination enables the overlay.")
    @field:ConfigEditorCombinations(provider = BlockOverlayCombinationsProvider::class)
    val combinations: MutableList<FeatureConditionCombination> = mutableListOf()
}

class BlockOverlayDetailsConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Color", desc = "Color used for the block tint and outline.")
    @field:ConfigEditorColour
    val color: Property<ChromaColour> = Property.of(ChromaColour.fromRGB(43, 177, 251, 0, 204))
}
