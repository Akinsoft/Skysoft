package com.skysoft.config.features.pets.display.visual

import com.google.gson.annotations.Expose
import com.skysoft.config.core.ConfigRepairable
import com.skysoft.config.core.repairFiniteFloat
import com.skysoft.config.features.pets.display.ScalableOverlayConfig
import com.skysoft.data.skyblock.SkyBlockRarity
import com.skysoft.utils.ColorUtilities.toChromaColor
import io.github.notenoughupdates.moulconfig.ChromaColour
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorButton
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorColour
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorSlider
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption
import io.github.notenoughupdates.moulconfig.observer.Property

class PetRarityBackgroundConfig(
    override val scalar: Float = 1.0f,
) : ScalableOverlayConfig, ConfigRepairable {
    private val configuredColors by lazy {
        mapOf(
            SkyBlockRarity.COMMON to commonColor,
            SkyBlockRarity.UNCOMMON to uncommonColor,
            SkyBlockRarity.RARE to rareColor,
            SkyBlockRarity.EPIC to epicColor,
            SkyBlockRarity.LEGENDARY to legendaryColor,
            SkyBlockRarity.MYTHIC to mythicColor,
        )
    }

    fun getRarityBackgroundColor(rarity: SkyBlockRarity): ChromaColour =
        configuredColors[rarity]?.get() ?: rarity.color.toChromaColor()

    @Expose
    @ConfigOption(name = "Background Padding", desc = "How much extra padding should be added to the background circle.")
    @ConfigEditorSlider(minValue = 0f, maxValue = 8f, minStep = 0.25f)
    val padding: Property<Float> = Property.of(defaultPadding())

    @Expose
    @ConfigOption(name = "§fCommon §rColor", desc = "Default color: #FFFFFF")
    @ConfigEditorColour
    val commonColor: Property<ChromaColour> = Property.of(opaque(255, 255, 255))

    @Expose
    @ConfigOption(name = "§aUncommon §rColor", desc = "Default color: #55FF55")
    @ConfigEditorColour
    val uncommonColor: Property<ChromaColour> = Property.of(opaque(85, 255, 85))

    @Expose
    @ConfigOption(name = "§9Rare §rColor", desc = "Default color: #5555FF")
    @ConfigEditorColour
    val rareColor: Property<ChromaColour> = Property.of(opaque(85, 85, 255))

    @Expose
    @ConfigOption(name = "§5Epic §rColor", desc = "Default color: #AA00AA")
    @ConfigEditorColour
    val epicColor: Property<ChromaColour> = Property.of(opaque(170, 0, 170))

    @Expose
    @ConfigOption(name = "§6Legendary §rColor", desc = "Default color: #FFAA00")
    @ConfigEditorColour
    val legendaryColor: Property<ChromaColour> = Property.of(opaque(255, 170, 0))

    @Expose
    @ConfigOption(name = "§dMythic §rColor", desc = "Default color: #FF55FF")
    @ConfigEditorColour
    val mythicColor: Property<ChromaColour> = Property.of(opaque(255, 85, 255))

    @ConfigOption(name = "Reset Colors", desc = "Restore the rarity palette.")
    @ConfigEditorButton(buttonText = "Reset")
    val reset: Runnable = Runnable(::reset)

    override fun repairLoadedValues() {
        padding.repairFiniteFloat(MIN_PADDING, MAX_PADDING, defaultPadding())
    }

    private fun defaultPadding(): Float = if (scalar < 1.0f) DEFAULT_SMALL_VISUAL_PADDING * scalar else DEFAULT_PADDING * scalar

    private fun opaque(red: Int, green: Int, blue: Int): ChromaColour =
        ChromaColour.fromRGB(red, green, blue, 0, OPAQUE_ALPHA)

    companion object {
        private const val DEFAULT_PADDING = 1.5f
        private const val DEFAULT_SMALL_VISUAL_PADDING = 4f
        private const val MIN_PADDING = 0f
        private const val MAX_PADDING = 8f
        private const val OPAQUE_ALPHA = 255
    }
}
