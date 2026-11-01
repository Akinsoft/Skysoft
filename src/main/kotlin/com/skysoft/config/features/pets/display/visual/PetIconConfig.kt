package com.skysoft.config.features.pets.display.visual

import com.google.gson.annotations.Expose
import com.skysoft.config.core.ConfigRepairable
import com.skysoft.config.core.ConfigResettable
import com.skysoft.config.core.repairFiniteFloat
import com.skysoft.config.core.repairPositiveFloat
import com.skysoft.config.features.pets.display.ScalableOverlayConfig
import io.github.notenoughupdates.moulconfig.annotations.Accordion
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorButton
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorInfoText
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorSlider
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption
import io.github.notenoughupdates.moulconfig.annotations.ConfigOrder
import io.github.notenoughupdates.moulconfig.observer.Property

open class PetIconConfig(
    override val scalar: Float = 1.0f,
) : ScalableOverlayConfig, ConfigRepairable {
    @Expose
    @ConfigOption(name = "Pet Icon", desc = "Show an icon of your current pet.\n§cRequired for any options below to work§7.")
    @ConfigEditorBoolean
    @ConfigOrder(10)
    val enabled: Property<Boolean> = Property.of(true)

    @Expose
    @ConfigOption(name = "Skin Animation", desc = "Play animated frames supplied by the active pet skin.")
    @ConfigEditorBoolean
    @ConfigOrder(20)
    val skinAnimation: Property<Boolean> = Property.of(true)

    @Expose
    @ConfigOption(name = "Skin Animation Speed", desc = "Playback rate for animated skin frames.")
    @ConfigEditorSlider(minValue = 0.0f, maxValue = 2.0f, minStep = 0.1f)
    @ConfigOrder(25)
    val skinAnimationSpeed: Property<Float> = Property.of(1.0f)

    @Expose
    @ConfigOption(name = "Icon Scale", desc = "How large the pet icon should be.")
    @ConfigEditorSlider(minValue = 0.1f, maxValue = 3.0f, minStep = 0.1f)
    @ConfigOrder(30)
    val scale: Property<Float> = Property.of(defaultIconScale())

    @Expose
    @ConfigOption(name = "Icon Rotation/Spin", desc = "")
    @Accordion
    @ConfigOrder(40)
    val rotation: PetIconRotationConfig = PetIconRotationConfig()

    class PetIconRotationConfig : ConfigResettable {
        @Expose
        @ConfigOption(name = "Static Rotation", desc = "Set a static rotation offset for the pet icon.")
        @Accordion
        val staticRotation = StaticIconRotationConfig()

        class StaticIconRotationConfig : ConfigResettable {
            @Expose
            @ConfigOption(name = "X Rotation", desc = "Fixed X-axis angle in degrees.")
            @ConfigEditorSlider(minValue = 0f, maxValue = 360f, minStep = 5f)
            val xRotation: Property<Float> = Property.of(0f)

            @Expose
            @ConfigOption(name = "Y Rotation", desc = "Fixed Y-axis angle in degrees.")
            @ConfigEditorSlider(minValue = 0f, maxValue = 360f, minStep = 5f)
            val yRotation: Property<Float> = Property.of(0f)

            @Expose
            @ConfigOption(name = "Z Rotation", desc = "Fixed Z-axis angle in degrees.")
            @ConfigEditorSlider(minValue = 0f, maxValue = 360f, minStep = 5f)
            val zRotation: Property<Float> = Property.of(0f)

            @ConfigOption(name = "Reset Rotations", desc = "Clear every fixed rotation angle.")
            @ConfigEditorButton(buttonText = "Reset")
            val reset: Runnable = Runnable(::reset)
        }

        @Expose
        @ConfigOption(name = "Spin Rotation", desc = "Continuously rotate the pet icon at a set speed.")
        @Accordion
        val spinRotation = SpinIconRotationConfig()

        class SpinIconRotationConfig : ConfigResettable {
            @ConfigOption(name = "Note", desc = "Positive values will rotate clockwise, negative values counter-clockwise.")
            @ConfigEditorInfoText
            val note: Unit = Unit

            @Expose
            @ConfigOption(name = "Rotation Speed (X)", desc = "X-axis spin rate in degrees per second.")
            @ConfigEditorSlider(minValue = -725f, maxValue = 725f, minStep = 25f)
            val speedX: Property<Float> = Property.of(0f)

            @Expose
            @ConfigOption(name = "Rotation Speed (Y)", desc = "Y-axis spin rate in degrees per second.")
            @ConfigEditorSlider(minValue = -725f, maxValue = 725f, minStep = 25f)
            val speedY: Property<Float> = Property.of(0f)

            @Expose
            @ConfigOption(name = "Rotation Speed (Z)", desc = "Z-axis spin rate in degrees per second.")
            @ConfigEditorSlider(minValue = -725f, maxValue = 725f, minStep = 1f)
            val speedZ: Property<Float> = Property.of(0f)

            @ConfigOption(name = "Reset Rotation Speeds", desc = "Stop rotation on every axis.")
            @ConfigEditorButton(buttonText = "Reset")
            val reset: Runnable = Runnable(::reset)
        }
    }

    @ConfigOption(name = "Reset Icon Settings", desc = "Restore the icon defaults.")
    @ConfigEditorButton(buttonText = "Reset")
    @ConfigOrder(50)
    val reset: Runnable = Runnable(::reset)

    override fun repairLoadedValues() {
        skinAnimationSpeed.repairFiniteFloat(MIN_SKIN_ANIMATION_SPEED, MAX_SKIN_ANIMATION_SPEED, DEFAULT_SKIN_ANIMATION_SPEED)
        scale.repairPositiveFloat(MIN_ICON_SCALE, MAX_ICON_SCALE, defaultIconScale())
    }

    private fun defaultIconScale(): Float = maxOf(DEFAULT_MIN_ICON_SCALE, DEFAULT_ICON_SCALE * scalar)

    companion object {
        private const val DEFAULT_ICON_SCALE = 1.5f
        private const val DEFAULT_MIN_ICON_SCALE = 1.0f
        private const val MIN_ICON_SCALE = 0.1f
        private const val MAX_ICON_SCALE = 3.0f
        private const val DEFAULT_SKIN_ANIMATION_SPEED = 1.0f
        private const val MIN_SKIN_ANIMATION_SPEED = 0.0f
        private const val MAX_SKIN_ANIMATION_SPEED = 2.0f
    }
}
