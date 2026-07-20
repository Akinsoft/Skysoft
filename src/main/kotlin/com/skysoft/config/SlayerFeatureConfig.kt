package com.skysoft.config

import com.google.gson.annotations.Expose
import io.github.notenoughupdates.moulconfig.ChromaColour
import io.github.notenoughupdates.moulconfig.annotations.Accordion
import io.github.notenoughupdates.moulconfig.annotations.Category
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorColour
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption
import io.github.notenoughupdates.moulconfig.observer.Property

class SlayerFeatureConfig {
    @JvmField
    @field:Expose
    @field:Category(name = "Target Highlighting", desc = "Highlight Slayer bosses and mini-bosses.")
    val targetHighlighting = SlayerTargetHighlightingConfig()

    @JvmField
    @field:Expose
    @field:Category(name = "Boss Alerts", desc = "Show alerts for Slayer boss events.")
    val bossAlerts = SlayerBossAlertsConfig()

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Miniboss Alert", desc = "Show a title and play a sound when a Slayer miniboss spawns.")
    @field:ConfigEditorBoolean
    var minibossAlert = false
}

class SlayerTargetHighlightingConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Enabled", desc = "Highlight Slayer bosses and mini-bosses.")
    @field:ConfigEditorBoolean
    var enabled = false

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Settings", desc = "Choose which Slayer target indicators appear.")
    @field:Accordion
    val settings = SlayerTargetHighlightingSettingsConfig()

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Details", desc = "Customize Slayer target indicators.")
    @field:Accordion
    val details = SlayerTargetHighlightingDetailsConfig()
}

class SlayerTargetHighlightingSettingsConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Highlight Bosses", desc = "Highlight your Slayer boss.")
    @field:ConfigEditorBoolean
    var highlightBosses = true

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Highlight Mini-Bosses", desc = "Highlight spawned Slayer mini-bosses.")
    @field:ConfigEditorBoolean
    var highlightMinibosses = true

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Target Line", desc = "Draw a line to your boss, or the closest mini-boss.")
    @field:ConfigEditorBoolean
    var targetLine = true
}

class SlayerTargetHighlightingDetailsConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Highlight Color", desc = "Color used for Slayer target highlights.")
    @field:ConfigEditorColour
    val highlightColor: Property<ChromaColour> = Property.of(ChromaColour.fromRGB(255, 85, 85, 0, 255))

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Target Line Color", desc = "Color used for the Slayer target line.")
    @field:ConfigEditorColour
    val targetLineColor: Property<ChromaColour> = Property.of(ChromaColour.fromRGB(255, 255, 255, 0, 255))
}

class SlayerBossAlertsConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Enabled", desc = "Show alerts for Slayer boss events.")
    @field:ConfigEditorBoolean
    var enabled = false

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Settings", desc = "Choose which Slayer boss alerts appear.")
    @field:Accordion
    val settings = SlayerBossAlertSettingsConfig()

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Details", desc = "Customize the Slayer boss titles.")
    @field:Accordion
    val details = SlayerBossAlertDetailsConfig()
}

class SlayerBossAlertSettingsConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Spawn Alert", desc = "Show \"Boss Spawned!\" when your Slayer boss spawns.")
    @field:ConfigEditorBoolean
    var showSpawnAlert = true

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Spawn Sound", desc = "Play a sound when your Slayer boss spawns.")
    @field:ConfigEditorBoolean
    var playSpawnSound = true

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Cocoon Alert", desc = "Show \"Boss Cocooned!\" when your Slayer boss is cocooned.")
    @field:ConfigEditorBoolean
    var showCocoonAlert = true

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Cocoon Sound", desc = "Play a sound when your Slayer boss is cocooned.")
    @field:ConfigEditorBoolean
    var playCocoonSound = true
}

class SlayerBossAlertDetailsConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Title Color", desc = "Color used for Slayer boss alert titles.")
    @field:ConfigEditorColour
    val titleColor: Property<ChromaColour> = Property.of(ChromaColour.fromRGB(255, 255, 255, 0, 255))
}
