package com.skysoft.features.event.diana

import com.skysoft.data.skyblock.pets.PetInternalNames
import com.skysoft.data.StoredPetData

internal object DianaMythologicalPetRequirement {
    fun canDamageRareMob(currentPet: StoredPetData?): Boolean =
        currentPet?.isGriffinPet == true

    private val StoredPetData.isGriffinPet: Boolean
        get() = PetInternalNames.properName(petInternalName)?.equals(GRIFFIN_PET_NAME, ignoreCase = true) == true

    private const val GRIFFIN_PET_NAME = "GRIFFIN"
}
