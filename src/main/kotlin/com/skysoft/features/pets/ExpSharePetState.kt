package com.skysoft.features.pets

import com.skysoft.config.SkysoftConfigGui
import com.skysoft.data.StoredPetData

internal data class ExpSharePetState(val petData: StoredPetData, val disabled: Boolean) {
    val opacity get() = if (disabled) SkysoftConfigGui.config().pets.display.visual.expSharePets.disabledOpacity.get() else 1.0f

    fun copyState(): ExpSharePetState = copy(petData = petData.copy())
}
