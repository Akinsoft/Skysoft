package com.skysoft.features.pets

import com.skysoft.config.SkysoftConfigGui
import com.skysoft.data.SkyBlockIsland

internal object PetFeatureDemand {
    fun isActive(): Boolean {
        val config = SkysoftConfigGui.config()
        val pets = config.pets
        return isDisplayActive() ||
            pets.visiblePetPosition.enabled ||
            pets.highlightActivePet ||
            pets.hideAutopet ||
            config.events.diana.rareMobSharing.enabled ||
            config.events.diana.lootshare.enabled ||
            config.foraging.throwingAxeHelper.enabled
    }

    fun isDisplayActive(): Boolean =
        SkysoftConfigGui.config().pets.petDisplay.enabled.get() && !SkyBlockIsland.SAFARI.isInIsland()
}
