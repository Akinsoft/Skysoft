package com.skysoft.config

import com.google.gson.annotations.Expose
import io.github.notenoughupdates.moulconfig.ChromaColour
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorButton
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorColour
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorInfoText
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorSlider
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption
import io.github.notenoughupdates.moulconfig.annotations.ConfigVisibleIf
import io.github.notenoughupdates.moulconfig.observer.Property
import io.github.notenoughupdates.moulconfig.processor.ProcessedOption

class SettingsConfig {
    @JvmField
    val turnOffPending: Property<Boolean> = Property.of(false)

    @JvmField
    val turnOnPending: Property<Boolean> = Property.of(false)

    @JvmField
    @field:ConfigOption(name = "Turn Every Feature Off", desc = "Disable every Skysoft feature.")
    @field:ConfigEditorButton(buttonText = "Turn Off")
    val turnEveryFeatureOff = Runnable { confirmOrToggle(false, turnOffPending) }

    @JvmField
    @field:ConfigOption(name = "Confirmation Required", desc = "Click Turn Off again to confirm.")
    @field:ConfigEditorInfoText
    @field:ConfigVisibleIf("turnOffPending")
    val turnOffConfirmation: Unit = Unit

    @JvmField
    @field:ConfigOption(name = "Turn Every Feature On", desc = "Enable every Skysoft feature.")
    @field:ConfigEditorButton(buttonText = "Turn On")
    val turnEveryFeatureOn = Runnable { confirmOrToggle(true, turnOnPending) }

    @JvmField
    @field:ConfigOption(name = "Confirmation Required", desc = "Click Turn On again to confirm.")
    @field:ConfigEditorInfoText
    @field:ConfigVisibleIf("turnOnPending")
    val turnOnConfirmation: Unit = Unit

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Selected Category Color", desc = "Color used for the selected category.")
    @field:ConfigEditorColour
    val selectedCategoryColor: Property<ChromaColour> =
        Property.of(ChromaColour.fromRGB(85, 255, 255, 0, 255))

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Category Color", desc = "Color used for top-level categories.")
    @field:ConfigEditorColour
    val categoryColor: Property<ChromaColour> =
        Property.of(ChromaColour.fromRGB(170, 170, 170, 0, 255))

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Subcategory Color", desc = "Color used for subcategories.")
    @field:ConfigEditorColour
    val subcategoryColor: Property<ChromaColour> =
        Property.of(ChromaColour.fromRGB(85, 85, 85, 0, 255))

    @JvmField
    @field:Expose
    @field:ConfigOption(
        name = "For Intrests",
        desc = "A long long time ago, a wise Intrests said \"what if a mod did...\" and then this happened.",
    )
    @field:ConfigEditorBoolean
    var forIntrests = true

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Volume", desc = "Volume of the death sound.")
    @field:ConfigVisibleIf("forIntrests")
    @field:ConfigEditorSlider(minValue = 0f, maxValue = 100f, minStep = 1f)
    var forIntrestsVolume = 50

    internal fun clearConfirmations() {
        turnOffPending.set(false)
        turnOnPending.set(false)
    }

    private fun confirmOrToggle(enabled: Boolean, confirmation: Property<Boolean>) {
        if (!confirmation.get()) {
            clearConfirmations()
            confirmation.set(true)
            return
        }

        val config = SkysoftConfigGui.config()
        SkysoftMoulConfigGuis.processConfig(config).allCategories.values
            .asSequence()
            .flatMap { it.options.asSequence() }
            .filter(::isMainFeatureToggle)
            .forEach { it.set(enabled) }
        clearConfirmations()
        config.saveNow()
    }

    private fun isMainFeatureToggle(option: ProcessedOption): Boolean =
        (option as? ProcessedOption.HasField)
            ?.field
            ?.isAnnotationPresent(MainFeatureToggle::class.java) == true
}
