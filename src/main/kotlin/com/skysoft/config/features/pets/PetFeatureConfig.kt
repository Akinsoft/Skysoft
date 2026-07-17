package com.skysoft.config.features.pets

import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName
import com.skysoft.config.core.repairFiniteFloat
import com.skysoft.config.features.pets.display.PetOverlayConfig
import com.skysoft.data.ProfileStorage
import com.skysoft.features.pets.PetOverlayConfigScreen
import io.github.notenoughupdates.moulconfig.annotations.Accordion
import io.github.notenoughupdates.moulconfig.annotations.Category
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorButton
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorSlider
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption
import io.github.notenoughupdates.moulconfig.annotations.ConfigOrder
import io.github.notenoughupdates.moulconfig.observer.Property

class PetFeatureConfig {
    @JvmField
    @field:Expose
    @field:Category(name = "Pet Display", desc = "Pet display settings.")
    @field:ConfigOrder(10)
    val petDisplay: PetDisplay = PetDisplay()

    @JvmField
    @field:Expose
    @field:Category(name = "Visible Pet Position", desc = "Adjust the floating in-world pet head near you.")
    @field:ConfigOrder(15)
    val visiblePetPosition: VisiblePetPosition = VisiblePetPosition()

    @JvmField
    @field:Expose
    @field:ConfigOption(
        name = "Highlight Active Pet",
        desc = "Highlight the currently equipped pet's slot in the Pet Menu."
    )
    @field:ConfigEditorBoolean
    @field:ConfigOrder(20)
    var highlightActivePet: Boolean = false

    @JvmField
    @field:Expose
    @field:ConfigOption(
        name = "Hide Autopet Messages",
        desc = "Hide autopet messages in chat."
    )
    @field:ConfigEditorBoolean
    @field:ConfigOrder(30)
    var hideAutopet: Boolean = false

    val display: PetOverlayConfig get() = petDisplay.display

    fun repairLoadedValues() {
        petDisplay.display.repairLoadedValues()
        petDisplay.enabled = petDisplay.display.general.enabled
        visiblePetPosition.repairLoadedValues()
    }

    class VisiblePetPosition {
        @JvmField
        @field:Expose
        @field:ConfigOption(name = "Enabled", desc = "Adjust the floating in-world pet head near you.")
        @field:ConfigEditorBoolean
        @field:ConfigOrder(10)
        var enabled: Boolean = false

        @JvmField
        @field:Expose
        @field:ConfigOption(name = "Settings", desc = "Visible pet behavior settings.")
        @field:Accordion
        @field:ConfigOrder(20)
        val settings = VisiblePetPositionSettings()

        @JvmField
        @field:Expose
        @field:ConfigOption(name = "Details", desc = "Visible pet position details.")
        @field:Accordion
        @field:ConfigOrder(30)
        val details = VisiblePetPositionDetails()

        fun repairLoadedValues() {
            details.repairLoadedValues()
        }
    }

    class VisiblePetPositionSettings {
        @JvmField
        @field:Expose
        @field:ConfigOption(name = "Stop Bouncing", desc = "Keep the visible pet at a steady height.")
        @field:ConfigEditorBoolean
        var stopBouncing: Boolean = true
    }

    class VisiblePetPositionDetails {
        @JvmField
        @field:Expose
        @field:ConfigOption(name = "Height Offset", desc = "Move the visible pet up or down from its normal height.")
        @field:ConfigEditorSlider(minValue = -2.0f, maxValue = 2.0f, minStep = 0.05f)
        val heightOffset: Property<Float> = Property.of(0.0f)

        fun repairLoadedValues() {
            heightOffset.repairFiniteFloat(MIN_HEIGHT_OFFSET, MAX_HEIGHT_OFFSET, DEFAULT_HEIGHT_OFFSET)
        }

        companion object {
            private const val DEFAULT_HEIGHT_OFFSET = 0.0f
            private const val MIN_HEIGHT_OFFSET = -2.0f
            private const val MAX_HEIGHT_OFFSET = 2.0f
        }
    }

    class PetDisplay {
        @JvmField
        @field:Expose
        val display: PetOverlayConfig = PetOverlayConfig()

        @JvmField
        @field:ConfigOption(name = "Enabled", desc = "Show a GUI element for the currently active pet.")
        @field:ConfigEditorBoolean
        @field:ConfigOrder(10)
        var enabled: Property<Boolean> = display.general.enabled

        @JvmField
        @field:Expose
        @field:ConfigOption(name = "Settings", desc = "Pet Display settings.")
        @field:Accordion
        @field:ConfigOrder(20)
        val settings = PetDisplaySettings()

        @JvmField
        @field:Expose(deserialize = true, serialize = false)
        @field:SerializedName("storage")
        val legacyStorage: ProfileStorage = ProfileStorage()
    }

    class PetDisplaySettings {
        @JvmField
        @field:ConfigOption(name = "Open Editor", desc = "Configure the advanced pet display HUD.")
        @field:ConfigEditorButton(buttonText = "Open")
        val openPetDisplay: Runnable = Runnable { PetOverlayConfigScreen.open() }
    }
}
