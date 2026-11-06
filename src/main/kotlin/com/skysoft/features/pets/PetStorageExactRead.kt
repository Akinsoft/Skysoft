package com.skysoft.features.pets

import com.skysoft.data.StoredPetData
import com.skysoft.features.pets.ActivePetTracker.PetDataAssertionSource

internal fun saveExactPetRead(petData: StoredPetData, syncXp: Boolean, assertCurrent: Boolean) {
    val previousExp = petData.uuid?.let(PetStorageService::storedPet)?.exp
    PetStorageService.storePet(petData)
    if (syncXp) {
        PetXpEstimator.resyncFromPetDataRead(petData, previousExp = previousExp, appliedExp = petData.exp)
    }
    if (assertCurrent) {
        ActivePetTracker.assertFoundCurrentData(petData, PetDataAssertionSource.MENU)
    }
}

internal fun saveDisplayedPetRead(
    petData: StoredPetData,
    exactExp: Double?,
    skinInternalName: String?,
    heldItemInternalName: String?,
    source: PetDataAssertionSource,
) {
    val updatedPet = petData.copy(
        exp = exactExp ?: petData.exp,
        skinInternalName = skinInternalName ?: petData.skinInternalName,
        heldItemInternalName = heldItemInternalName ?: petData.heldItemInternalName,
    )
    PetXpEstimator.resyncFromPetDataRead(updatedPet, previousExp = petData.exp, appliedExp = exactExp)
    ActivePetTracker.assertFoundCurrentData(updatedPet, source)
}
