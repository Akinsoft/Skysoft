package com.skysoft.config

import com.google.gson.annotations.Expose
import io.github.notenoughupdates.moulconfig.ChromaColour
import io.github.notenoughupdates.moulconfig.annotations.Accordion
import io.github.notenoughupdates.moulconfig.annotations.Category
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorColour
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption
import io.github.notenoughupdates.moulconfig.annotations.ConfigVisibleIf
import io.github.notenoughupdates.moulconfig.observer.Property

class ForagingFeatureConfig {
    @JvmField
    @field:Expose
    @field:Category(name = "Honeyhive Helper", desc = "Track Honeyhive refills and find hives ready to loot.")
    val honeyhiveHelper = HoneyhiveHelperConfig()

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Floor Drop Highlighter", desc = "Highlight visible Floor Drops.")
    @field:MainFeatureToggle
    @field:ConfigEditorBoolean
    var highlightFloorDrops = false

    @JvmField
    @field:Expose
    @field:Category(name = "Queen Ant Warning", desc = "Show a title when you find a Queen Ant.")
    val queenAntWarning = QueenAntWarningConfig()

    @JvmField
    @field:Expose
    @field:Category(name = "Throwing Axe Helper", desc = "Preview logs cut by Throwing Axe.")
    val throwingAxeHelper = ThrowingAxeHelperConfig()

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Hide Axe Particles", desc = "Hide particles emitted by flying Throwing Axes.")
    @field:MainFeatureToggle
    @field:ConfigEditorBoolean
    var hideAxeParticles = false
}

class QueenAntWarningConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Enabled", desc = "Show a title when you find a Queen Ant.")
    @field:MainFeatureToggle
    @field:ConfigEditorBoolean
    var enabled = false

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Details", desc = "Queen Ant Warning appearance.")
    @field:Accordion
    val details = QueenAntWarningDetailsConfig()
}

class QueenAntWarningDetailsConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Crosshair Line", desc = "Draw a line to the Queen Ant.")
    @field:ConfigEditorBoolean
    var crosshairLine = true
}

class ThrowingAxeHelperConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Enabled", desc = "Highlight logs your Throwing Axe is expected to cut.")
    @field:MainFeatureToggle
    @field:ConfigEditorBoolean
    var enabled = false

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Settings", desc = "Throwing Axe Helper settings.")
    @field:Accordion
    val settings = ThrowingAxeHelperSettingsConfig()

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Details", desc = "Throwing Axe Helper appearance.")
    @field:Accordion
    val details = ThrowingAxeHelperDetailsConfig()
}

class ThrowingAxeHelperSettingsConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Highlight Thrown Logs", desc = "Keep logs targeted by recent throws highlighted until they break.")
    @field:ConfigEditorBoolean
    var highlightThrownLogs = false

    @JvmField
    @field:Expose
    @field:ConfigOption(
        name = "Highlight Overlapping Logs",
        desc = "Highlight logs targeted by both your current aim and a recent throw in red.",
    )
    @field:ConfigEditorBoolean
    @field:ConfigVisibleIf("highlightThrownLogs")
    var highlightOverlappingLogs = true
}

class ThrowingAxeHelperDetailsConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Highlight Color", desc = "Color used for expected logs.")
    @field:ConfigEditorColour
    val highlightColor: Property<ChromaColour> = Property.of(ChromaColour.fromRGB(85, 255, 85, 0, 204))

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Possible Color", desc = "Color used for possible extra logs.")
    @field:ConfigEditorColour
    val possibleColor: Property<ChromaColour> = Property.of(ChromaColour.fromRGB(255, 255, 85, 0, 204))

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Thrown Log Color", desc = "Color used for logs targeted by recent throws.")
    @field:ConfigEditorColour
    val thrownLogColor: Property<ChromaColour> = Property.of(ChromaColour.fromRGB(43, 177, 251, 0, 204))
}
