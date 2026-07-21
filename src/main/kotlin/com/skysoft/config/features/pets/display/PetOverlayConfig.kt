package com.skysoft.config.features.pets.display

import com.google.gson.annotations.Expose
import com.skysoft.config.SkysoftConfigGui
import com.skysoft.config.core.HudPosition
import com.skysoft.config.core.repairFiniteFloat
import com.skysoft.config.features.pets.display.text.PetTextConfig
import com.skysoft.config.features.pets.display.visual.SharedPetVisualConfig
import com.skysoft.config.features.pets.display.visual.PetVisualConfig
import io.github.notenoughupdates.moulconfig.Config
import io.github.notenoughupdates.moulconfig.annotations.Accordion
import io.github.notenoughupdates.moulconfig.annotations.Category
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorInfoText
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorSlider
import io.github.notenoughupdates.moulconfig.annotations.ConfigLink
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption
import io.github.notenoughupdates.moulconfig.annotations.ConfigOrder
import io.github.notenoughupdates.moulconfig.common.text.StructuredText
import io.github.notenoughupdates.moulconfig.observer.Property

class PetOverlayConfig : Config() {
    override fun saveNow() {
        SkysoftConfigGui.config().saveNow()
    }

    override fun getTitle(): StructuredText = StructuredText.of("Pet Display")

    @JvmField
    @field:Expose
    @field:Category(name = "General", desc = "General settings for the pet display.")
    val general: GeneralPetOverlayConfig = GeneralPetOverlayConfig()

    class GeneralPetOverlayConfig {
        @JvmField
        @field:Expose
        @field:ConfigOption(name = "Enabled", desc = "Show a GUI element for the currently active pet.")
        @field:ConfigEditorBoolean
        val enabled: Property<Boolean> = Property.of(false)

        @JvmField
        @field:Expose
        @field:ConfigOption(name = "Settings", desc = "General Pet Display settings.")
        @field:Accordion
        val settings = GeneralPetOverlaySettingsConfig()

        @JvmField
        @field:Expose
        @field:ConfigLink(owner = GeneralPetOverlayConfig::class, field = "enabled")
        val position: HudPosition = HudPosition(0, 19, centerY = false).rememberDefault()
    }

    class GeneralPetOverlaySettingsConfig {
        @JvmField
        @field:Expose
        @field:ConfigOption(name = "Background", desc = "Draw a background behind the Pet Display.")
        @field:ConfigEditorBoolean
        val background: Property<Boolean> = Property.of(false)

        @JvmField
        @field:Expose
        @field:ConfigOption(name = "Hide in menus", desc = "Hide Pet Display while a container menu is open.")
        @field:ConfigEditorBoolean
        val hideInMenus: Property<Boolean> = Property.of(false)

        @JvmField
        @field:ConfigOption(
            name = "§cXP Accuracy",
            desc = "Pet Display requires the Pet display in Hypixel's /widget menu. " +
                "Skysoft estimates live XP between widget updates. For maxed pets, enable Pet widget overflow XP too."
        )
        @field:ConfigEditorInfoText
        val xpAccuracyWarning: Unit = Unit
    }

    @JvmField
    @field:Expose
    @field:Category(name = "Visual Elements", desc = "Visual element settings for the pet display.")
    val visual: PetOverlayVisualConfig = PetOverlayVisualConfig()

    class PetOverlayVisualConfig {
        @JvmField
        @field:Expose
        @field:ConfigOption(name = "Equipped Pet Visuals", desc = "")
        @field:Accordion
        @field:ConfigOrder(10)
        val equippedPet: EquippedPetVisualConfig = EquippedPetVisualConfig()

        @JvmField
        @field:Expose
        @field:ConfigOption(name = "Exp-Share Pets Visuals", desc = "")
        @field:Accordion
        @field:ConfigOrder(20)
        val expSharePets: SharedPetVisualConfig = SharedPetVisualConfig(scalar = 0.6f)
    }

    class EquippedPetVisualConfig : PetVisualConfig()

    @JvmField
    @field:Expose
    @field:Category(name = "Text Elements", desc = "Text element settings for the pet display.")
    val text: PetTextConfig = PetTextConfig()

    @JvmField
    @field:Expose
    @field:Category(name = "Preview", desc = "Preview settings for the pet display.")
    val preview: PetOverlayPreviewConfig = PetOverlayPreviewConfig()

    class PetOverlayPreviewConfig {
        @JvmField
        @field:Expose
        @field:ConfigOption(
            name = "Preview Scale",
            desc = "How large the pet display preview should be."
        )
        @field:ConfigEditorSlider(minValue = 0.5f, maxValue = 3.0f, minStep = 0.05f)
        @field:ConfigOrder(10)
        val scale: Property<Float> = Property.of(1.0f)

        fun repairLoadedValues() {
            scale.repairFiniteFloat(MIN_PREVIEW_SCALE, MAX_PREVIEW_SCALE, DEFAULT_PREVIEW_SCALE)
        }

        companion object {
            private const val DEFAULT_PREVIEW_SCALE = 1.0f
            private const val MIN_PREVIEW_SCALE = 0.5f
            private const val MAX_PREVIEW_SCALE = 3.0f
        }
    }

    fun repairLoadedValues() {
        visual.equippedPet.repairLoadedValues()
        visual.expSharePets.repairLoadedValues()
        preview.repairLoadedValues()
    }
}
