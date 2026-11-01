package com.skysoft.config.features.pets.display.visual

import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName
import com.skysoft.config.core.ConfigRepairable
import com.skysoft.config.core.repairFiniteFloat
import com.skysoft.config.core.repairLoadedConfigs
import com.skysoft.config.features.pets.display.ScalableOverlayConfig
import io.github.notenoughupdates.moulconfig.ChromaColour
import io.github.notenoughupdates.moulconfig.annotations.Accordion
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorButton
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorColour
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorSlider
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption
import io.github.notenoughupdates.moulconfig.annotations.ConfigOrder
import io.github.notenoughupdates.moulconfig.annotations.ConfigOverride
import io.github.notenoughupdates.moulconfig.observer.Property

open class PetBorderConfig(
    override val scalar: Float = 1.0f,
) : ScalableOverlayConfig, ConfigRepairable {
    @Expose
    @ConfigOption(
        name = "Enabled",
        desc = "Draw level progress as a ring surrounding the pet background.",
    )
    @ConfigEditorBoolean
    @ConfigOrder(10)
    open val enabled: Property<Boolean> = Property.of(true)

    @Expose
    @ConfigOption(name = "Customization", desc = "")
    @Accordion
    @ConfigOrder(20)
    @field:SerializedName(value = "progress", alternate = ["customization"])
    val progress: ProgressRingConfig = ProgressRingConfig(scalar)

    @Expose
    @ConfigOption(name = "Separator Ring", desc = "")
    @Accordion
    @ConfigOrder(30)
    @field:SerializedName(value = "divider", alternate = ["separator"])
    open val divider: DividerRingConfig = DividerRingConfig(scalar)

    class DividerRingConfig(scalar: Float = 1.0f) : RingStyleConfig(scalar) {
        @Expose
        @ConfigOption(name = "Enabled", desc = "Separate the background fill from the progress ring.")
        @ConfigEditorBoolean
        @ConfigOrder(10)
        val enabled: Property<Boolean> = Property.of(true)

        @Expose
        @ConfigOption(name = "Ring Color", desc = "Divider color; defaults to #808080.")
        @ConfigEditorColour
        @ConfigOverride
        @ConfigOrder(20)
        override val color: Property<ChromaColour> = Property.of(DEFAULT_RING_COLOR)
    }

    @ConfigOption(name = "Reset Ring Settings", desc = "Restore progress and divider ring defaults.")
    @ConfigEditorButton(buttonText = "Reset")
    @ConfigOrder(40)
    val reset: Runnable = Runnable(::reset)

    override fun repairLoadedValues() = repairLoadedConfigs(progress, divider)
}

class ProgressRingConfig(scalar: Float = 1.0f) : RingStyleConfig(scalar) {
    override val color: Property<ChromaColour> get() = filledColor

    @Expose
    @ConfigOption(
        name = "Filled Ring Color",
        desc = "The color of the filled portion of the ring.\n§7Default: §#§0§0§f§f§f§f§/#00FFFF",
    )
    @ConfigEditorColour
    @ConfigOrder(21)
    val filledColor: Property<ChromaColour> = Property.of(ChromaColour.fromRGB(0, 255, 255, 0, 255))

    @Expose
    @ConfigOption(
        name = "Unfilled Ring Color",
        desc = "The color of the unfilled portion of the ring.\n§7Default: §#§c§0§c§0§c§0§/#C0C0C0",
    )
    @ConfigEditorColour
    @ConfigOrder(22)
    val unfilledColor: Property<ChromaColour> = Property.of(ChromaColour.fromRGB(192, 192, 192, 0, 255))

    @ConfigOption(name = "Reset", desc = "Reset XP ring settings to the default values.")
    @ConfigEditorButton(buttonText = "Reset")
    @ConfigOverride
    override val reset: Runnable = Runnable(::reset)
}

open class RingStyleConfig(
    scalar: Float = 1.0f,
) : ScalableOverlayConfig, ConfigRepairable {
    @Transient
    override val scalar: Float = scalar

    @Expose
    @ConfigOption(name = "Ring Padding", desc = "Thickness added outside the wrapped visual.")
    @ConfigEditorSlider(minValue = 2f, maxValue = 10f, minStep = 0.5f)
    @ConfigOrder(10)
    val padding: Property<Float> = Property.of(defaultPadding())

    @Transient
    open val color: Property<ChromaColour> = Property.of(DEFAULT_RING_COLOR)

    @ConfigOption(name = "Reset Ring Settings", desc = "Restore this ring's defaults.")
    @ConfigEditorButton(buttonText = "Reset")
    @ConfigOrder(30)
    open val reset: Runnable = Runnable(::reset)

    override fun repairLoadedValues() {
        padding.repairFiniteFloat(MIN_PADDING, MAX_PADDING, defaultPadding())
    }

    private fun defaultPadding(): Float = maxOf(MIN_PADDING, DEFAULT_PADDING * scalar)

    companion object {
        internal val DEFAULT_RING_COLOR: ChromaColour = ChromaColour.fromRGB(128, 128, 128, 0, 255)
        private const val DEFAULT_PADDING = 2f
        private const val MIN_PADDING = 2f
        private const val MAX_PADDING = 10f
    }
}
