package com.skysoft.config.features.pets.display.visual

import com.google.gson.annotations.Expose
import com.skysoft.config.core.ConfigRepairable
import com.skysoft.config.core.repairLoadedConfigs
import com.skysoft.config.features.pets.display.ScalableOverlayConfig
import io.github.notenoughupdates.moulconfig.annotations.Accordion
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption
import io.github.notenoughupdates.moulconfig.annotations.ConfigOrder
import io.github.notenoughupdates.moulconfig.observer.Property

open class PetVisualConfig(
    override val scalar: Float = 1.0f,
) : ScalableOverlayConfig, ConfigRepairable {
    @Expose
    @ConfigOption(name = "Pet Icon", desc = "")
    @Accordion
    @ConfigOrder(10)
    open val icon: PetIconConfig = PetIconConfig(scalar)

    @Expose
    @ConfigOption(name = "Background Color", desc = "")
    @Accordion
    @ConfigOrder(20)
    open val rarityBackground: BackgroundColorConfig = BackgroundColorConfig(scalar)

    open class BackgroundColorConfig(
        override val scalar: Float = 1.0f,
    ) : ScalableOverlayConfig, ConfigRepairable {
        @Expose
        @ConfigOption(
            name = "Enabled",
            desc = "Display a background color around your pet.\nDefault is to display the rarity color of the pet.",
        )
        @ConfigEditorBoolean
        @ConfigOrder(10)
        val enabled: Property<Boolean> = Property.of(true)

        @Expose
        @ConfigOption(name = "Customization", desc = "")
        @Accordion
        @ConfigOrder(20)
        val customization: PetRarityBackgroundConfig = PetRarityBackgroundConfig(scalar)

        @Expose
        @ConfigOption(name = "Border Ring", desc = "")
        @Accordion
        @ConfigOrder(30)
        open val borderRing: PetBorderConfig = PetBorderConfig(scalar)

        override fun repairLoadedValues() = repairLoadedConfigs(customization, borderRing)
    }

    @Expose
    @ConfigOption(name = "Pet Item", desc = "")
    @Accordion
    @ConfigOrder(30)
    open val petItem: PetItemLayerConfig = PetItemLayerConfig(scalar)

    override fun repairLoadedValues() = repairLoadedConfigs(icon, rarityBackground, petItem)
}
