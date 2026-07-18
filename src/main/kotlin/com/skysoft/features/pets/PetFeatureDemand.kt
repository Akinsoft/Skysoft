package com.skysoft.features.pets

import com.skysoft.config.SkysoftConfigGui

internal object PetFeatureDemand {
    fun isActive(): Boolean {
        val config = SkysoftConfigGui.config()
        val pets = config.pets
        return pets.petDisplay.enabled.get() ||
            pets.visiblePetPosition.enabled ||
            pets.highlightActivePet ||
            pets.hideAutopet ||
            config.events.diana.enabled
    }

    fun isDisplayActive(): Boolean = SkysoftConfigGui.config().pets.petDisplay.enabled.get()
}
