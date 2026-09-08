package com.skysoft.features.pets

import com.skysoft.data.StoredPetData
import com.skysoft.features.pets.ActivePetTracker.PetDataAssertionSource

internal fun saveExactPetRead(petData: StoredPetData, syncXp: Boolean, assertCurrent: Boolean) {
    val previousExp = petData.uuid?.let { uuid ->
        PetStorageService.petStorage.pets.firstOrNull { it.uuid == uuid }?.exp
    }
    PetStorageService.storePet(petData)
    if (syncXp) {
        PetXpEstimator.resyncFromPetDataRead(
            petData,
            exact = true,
            previousExp = previousExp,
            appliedExp = petData.exp,
        )
    }
    if (assertCurrent) {
        ActivePetTracker.assertFoundCurrentData(petData, PetDataAssertionSource.MENU)
    }
}
