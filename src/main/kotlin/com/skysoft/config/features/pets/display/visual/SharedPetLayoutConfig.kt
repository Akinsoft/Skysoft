package com.skysoft.config.features.pets.display.visual

import com.google.gson.annotations.Expose
import com.skysoft.config.core.ConfigResettable
import com.skysoft.config.core.repairFiniteFloat
import com.skysoft.utils.gui.OrbitDirection
import io.github.notenoughupdates.moulconfig.annotations.Accordion
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorButton
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorDropdown
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorSlider
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption
import io.github.notenoughupdates.moulconfig.observer.Property

class SharedPetLayoutConfig : ConfigResettable {
    @Expose
    @ConfigOption(name = "Placement Location", desc = "Where the other pets should be displayed, relative to the main pet icon.")
    @ConfigEditorDropdown
    val placement: Property<ExpShareLocationOption> = Property.of(ExpShareLocationOption.ORBIT)

    enum class ExpShareLocationOption(private val displayName: String) {
        TOP("Top"),
        BOTTOM("Bottom"),
        LEFT("Left"),
        RIGHT("Right"),
        ORBIT("Orbit"),
        ;

        override fun toString(): String = displayName
    }

    @Expose
    @ConfigOption(name = "Group Orientation", desc = "How the group icons should be oriented.\nDoes not apply to Orbit mode.")
    @ConfigEditorDropdown
    val groupOrientation: Property<GroupOrientation> = Property.of(GroupOrientation.VERTICAL)

    enum class GroupOrientation(private val displayName: String) {
        HORIZONTAL("Horizontally"),
        VERTICAL("Vertically"),
        ;

        override fun toString(): String = displayName
    }

    @Expose
    @ConfigOption(name = "Orbit Customization", desc = "")
    @Accordion
    val subOrbit: OrbitSettings = OrbitSettings()

    class OrbitSettings : ConfigResettable {
        @Expose
        @ConfigOption(name = "Orbit Distance", desc = "Gap between the orbiting pets and the equipped pet.")
        @ConfigEditorSlider(minValue = 1f, maxValue = 10f, minStep = 1f)
        val orbitDistance: Property<Float> = Property.of(1f)

        @Expose
        @ConfigOption(name = "Orbit Direction", desc = "Direction used to advance orbiting icons.")
        @ConfigEditorDropdown
        val orbitDirection: Property<OrbitDirection> = Property.of(OrbitDirection.CLOCKWISE)

        @Expose
        @ConfigOption(name = "Orbit Speed", desc = "Angular travel per second, in degrees.")
        @ConfigEditorSlider(minValue = 10f, maxValue = 360f, minStep = 10f)
        val orbitSpeed: Property<Float> = Property.of(20f)

        fun repairLoadedValues() {
            orbitDistance.repairFiniteFloat(MIN_ORBIT_DISTANCE, MAX_ORBIT_DISTANCE, DEFAULT_ORBIT_DISTANCE)
            orbitSpeed.repairFiniteFloat(MIN_ORBIT_SPEED, MAX_ORBIT_SPEED, DEFAULT_ORBIT_SPEED)
        }

        companion object {
            private const val DEFAULT_ORBIT_DISTANCE = 1f
            private const val MIN_ORBIT_DISTANCE = 1f
            private const val MAX_ORBIT_DISTANCE = 10f
            private const val DEFAULT_ORBIT_SPEED = 20f
            private const val MIN_ORBIT_SPEED = 10f
            private const val MAX_ORBIT_SPEED = 360f
        }
    }

    @ConfigOption(name = "Reset Organization", desc = "Reset the organization settings to the default values.")
    @ConfigEditorButton(buttonText = "Reset")
    val reset: Runnable = Runnable(::reset)

    fun repairLoadedValues() {
        subOrbit.repairLoadedValues()
    }
}
