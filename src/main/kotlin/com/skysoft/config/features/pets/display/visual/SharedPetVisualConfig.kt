package com.skysoft.config.features.pets.display.visual

import com.google.gson.annotations.Expose
import com.skysoft.config.core.ConfigRepairable
import com.skysoft.config.core.repairFiniteFloat
import com.skysoft.config.core.repairLoadedConfigs
import io.github.notenoughupdates.moulconfig.annotations.Accordion
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorSlider
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption
import io.github.notenoughupdates.moulconfig.annotations.ConfigOrder
import io.github.notenoughupdates.moulconfig.observer.Property

class SharedPetVisualConfig(
    private val scalar: Float = 0.6f,
) : ConfigRepairable {
    @Expose
    @ConfigOption(
        name = "Exp-Share Pets",
        desc = "Adds additional pet icons for your pets active in Exp Share.\n" +
            "§cOpen the exp share menu if information is out of date."
    )
    @ConfigEditorBoolean
    @ConfigOrder(10)
    val enabled: Property<Boolean> = Property.of(true)

    @Expose
    @ConfigOption(name = "Organization", desc = "")
    @Accordion
    @ConfigOrder(20)
    val organization: SharedPetLayoutConfig = SharedPetLayoutConfig()

    @Expose
    @ConfigOption(name = "Pet Icon", desc = "")
    @Accordion
    @ConfigOrder(30)
    val icon: SharedPetSpacingIconConfig = SharedPetSpacingIconConfig(scalar)

    class SharedPetSpacingIconConfig(scalar: Float = 1.0f) : PetIconConfig(scalar) {
        @Expose
        @ConfigOption(
            name = "Icon Spacing",
            desc = "Gap between grouped icons; orbit layouts use orbit distance instead."
        )
        @ConfigEditorSlider(minValue = 1f, maxValue = 5f, minStep = 1f)
        @ConfigOrder(45)
        val iconSpacing: Property<Float> = Property.of(1f)

        override fun repairLoadedValues() {
            super.repairLoadedValues()
            iconSpacing.repairFiniteFloat(MIN_SPACING, MAX_SPACING, DEFAULT_SPACING)
        }

        companion object {
            private const val DEFAULT_SPACING = 1f
            private const val MIN_SPACING = 1f
            private const val MAX_SPACING = 5f
        }
    }

    @Expose
    @ConfigOption(name = "Background Color", desc = "")
    @Accordion
    @ConfigOrder(40)
    val rarityBackground: PetVisualConfig.BackgroundColorConfig =
        PetVisualConfig.BackgroundColorConfig(scalar)

    @Expose
    @ConfigOption(name = "Pet Item", desc = "")
    @Accordion
    @ConfigOrder(50)
    val petItem: PetItemLayerConfig = PetItemLayerConfig(scalar)

    @Expose
    @ConfigOption(
        name = "Hide Disabled Slots",
        desc = "Only render Exp Share slots that are currently enabled."
    )
    @ConfigEditorBoolean
    @ConfigOrder(60)
    val activeSlotsOnly: Property<Boolean> = Property.of(false)

    @Expose
    @ConfigOption(
        name = "Disabled Opacity",
        desc = "Visibility of disabled slots when they are shown."
    )
    @ConfigEditorSlider(minValue = 0.1f, maxValue = 1f, minStep = 0.05f)
    @ConfigOrder(65)
    val disabledOpacity: Property<Float> = Property.of(0.5f)

    override fun repairLoadedValues() {
        repairLoadedConfigs(organization.subOrbit, icon, rarityBackground, petItem)
        disabledOpacity.repairFiniteFloat(MIN_DISABLED_OPACITY, MAX_DISABLED_OPACITY, DEFAULT_DISABLED_OPACITY)
    }

    companion object {
        private const val DEFAULT_DISABLED_OPACITY = 0.5f
        private const val MIN_DISABLED_OPACITY = 0.1f
        private const val MAX_DISABLED_OPACITY = 1.0f
    }
}
