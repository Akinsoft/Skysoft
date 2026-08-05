package com.skysoft.config

import com.google.gson.annotations.Expose
import io.github.notenoughupdates.moulconfig.ChromaColour
import io.github.notenoughupdates.moulconfig.annotations.Accordion
import io.github.notenoughupdates.moulconfig.annotations.Category
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorColour
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption
import io.github.notenoughupdates.moulconfig.observer.Property

class ForagingFeatureConfig {
    @JvmField
    @field:Expose
    @field:Category(name = "Throwing Axe Helper", desc = "Preview logs cut by Throwing Axe.")
    val throwingAxeHelper = ThrowingAxeHelperConfig()
}

class ThrowingAxeHelperConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Enabled", desc = "Highlight logs your Throwing Axe is expected to cut.")
    @field:ConfigEditorBoolean
    var enabled = false

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Details", desc = "Throwing Axe Helper appearance.")
    @field:Accordion
    val details = ThrowingAxeHelperDetailsConfig()
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
}
