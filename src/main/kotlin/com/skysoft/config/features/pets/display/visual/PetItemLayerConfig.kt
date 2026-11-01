package com.skysoft.config.features.pets.display.visual

import com.google.gson.annotations.Expose
import com.skysoft.config.core.ConfigRepairable
import com.skysoft.config.core.repairPositiveFloat
import com.skysoft.config.features.pets.display.ScalableOverlayConfig
import com.skysoft.utils.gui.GuiAlignment.HorizontalAlignment
import com.skysoft.utils.gui.GuiAlignment.VerticalAlignment
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorDropdown
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorSlider
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption
import io.github.notenoughupdates.moulconfig.observer.Property

class PetItemLayerConfig(
    override val scalar: Float = 1.0f,
) : ScalableOverlayConfig, ConfigRepairable {
    @Expose
    @ConfigOption(name = "Enabled", desc = "Display the pet's held item as an itemstack.")
    @ConfigEditorBoolean
    val enabled: Property<Boolean> = Property.of(true)

    @Expose
    @ConfigOption(name = "Placement", desc = "Where the item should be placed, relative to the pet icon.")
    @ConfigEditorDropdown
    val placement: Property<PetItemPlacement> = Property.of(PetItemPlacement.BOTTOM_RIGHT)

    enum class PetItemPlacement(
        private val label: String,
        val vertical: VerticalAlignment,
        val horizontal: HorizontalAlignment,
    ) {
        TOP_LEFT("Top Left", VerticalAlignment.TOP, HorizontalAlignment.LEFT),
        TOP_CENTER("Top Center", VerticalAlignment.TOP, HorizontalAlignment.CENTER),
        TOP_RIGHT("Top Right", VerticalAlignment.TOP, HorizontalAlignment.RIGHT),
        CENTER_LEFT("Center Left", VerticalAlignment.CENTER, HorizontalAlignment.LEFT),
        CENTER("Center", VerticalAlignment.CENTER, HorizontalAlignment.CENTER),
        CENTER_RIGHT("Center Right", VerticalAlignment.CENTER, HorizontalAlignment.RIGHT),
        BOTTOM_LEFT("Bottom Left", VerticalAlignment.BOTTOM, HorizontalAlignment.LEFT),
        BOTTOM_CENTER("Bottom Center", VerticalAlignment.BOTTOM, HorizontalAlignment.CENTER),
        BOTTOM_RIGHT("Bottom Right", VerticalAlignment.BOTTOM, HorizontalAlignment.RIGHT),
        ;

        override fun toString(): String = label
    }

    @Expose
    @ConfigOption(name = "Item Scale", desc = "How large the pet item icon should be.")
    @ConfigEditorSlider(minValue = 0.1f, maxValue = 2.0f, minStep = 0.1f)
    val scale: Property<Float> = Property.of(DEFAULT_ITEM_SCALE)

    override fun repairLoadedValues() {
        scale.repairPositiveFloat(MIN_ITEM_SCALE, MAX_ITEM_SCALE, DEFAULT_ITEM_SCALE)
    }

    companion object {
        private const val DEFAULT_ITEM_SCALE = 1.0f
        private const val MIN_ITEM_SCALE = 0.1f
        private const val MAX_ITEM_SCALE = 2.0f
    }
}
