package com.skysoft.features.pets

import com.skysoft.SkysoftMod
import com.skysoft.data.StoredPetData
import com.skysoft.data.skyblock.MayorPerkApi
import com.skysoft.data.skyblock.SkyBlockItemId.skyBlockId
import com.skysoft.features.pets.PetItemUtilities.getPetInfo
import com.skysoft.features.pets.PetItemUtilities.toExactPetDataOrNull
import com.skysoft.utils.ChangeResult
import net.minecraft.world.item.ItemStack
import java.util.UUID

internal object PetStorageExpSharing {
    fun readExpSharePets(inventoryName: String?, inventoryItems: Map<Int, ItemStack>) {
        if (inventoryName != EXP_SHARING_INVENTORY_NAME) return
        var petDataChanged = false
        val expSharePets = EXP_SHARE_SLOTS.map { expShareSlot ->
            val slotItem = inventoryItems[expShareSlot]?.takeIf {
                it.hoverName.string != "No pet in slot"
            } ?: return@map null
            readExpSharePetUuid(expShareSlot, slotItem) { petDataChanged = true }
        }
        if (PetStorageService.petStorage.expSharePets == expSharePets) {
            if (petDataChanged) PetStorageService.markDirty()
            return
        }
        PetStorageService.petStorage.expSharePets.clear()
        PetStorageService.petStorage.expSharePets.addAll(expSharePets)
        PetStorageService.markDirty()
    }

    fun activePets(): List<StoredPetData> =
        PetStorageService.petStorage.expSharePets.take(activeSlotCount()).mapNotNull { uuid ->
            uuid?.let { petUuid -> PetStorageService.petStorage.pets.firstOrNull { it.uuid == petUuid } }
        }

    fun activeUuids(): Set<UUID> =
        PetStorageService.petStorage.expSharePets.take(activeSlotCount()).filterNotNull().toSet()

    fun disabledUuids(): Set<UUID> =
        PetStorageService.petStorage.expSharePets.drop(activeSlotCount()).filterNotNull().toSet()

    fun isSlotDisabled(slot: Int): Boolean =
        slot in EXP_SHARE_SLOTS.drop(activeSlotCount())

    fun isInventory(inventoryName: String?): Boolean =
        inventoryName == EXP_SHARING_INVENTORY_NAME

    private fun readExpSharePetUuid(slot: Int, stack: ItemStack, markPetDataChanged: () -> Unit): UUID? {
        val exactPetData = stack.toExactPetDataOrNull()
        val exactPetUuid = exactPetData?.uuid
        if (exactPetData != null && exactPetUuid != null) {
            if (storeExactPetData(exactPetData) == ChangeResult.CHANGED) markPetDataChanged()
            return exactPetUuid
        }

        stack.getPetInfo()?.ownedUuid?.let { return it }

        SkysoftMod.LOGGER.warn(
            "Unable to read pet UUID from occupied Exp Share slot {}: {} ({})",
            slot,
            stack.hoverName.string,
            stack.skyBlockId() ?: "unknown SkyBlock item",
        )
        return null
    }

    private fun storeExactPetData(petData: StoredPetData): ChangeResult {
        val petUuid = petData.uuid ?: return ChangeResult.UNCHANGED
        val existingPetData = PetStorageService.petStorage.pets.firstOrNull { it.uuid == petUuid }
        PetStorageService.petStorage.pets.addOrReplace(petData) { it.uuid == petUuid }
        return ChangeResult.from(existingPetData?.hasSamePersistedDataAs(petData) != true)
    }

    private fun StoredPetData.hasSamePersistedDataAs(other: StoredPetData): Boolean =
        petInternalName == other.petInternalName &&
            skinInternalName == other.skinInternalName &&
            heldItemInternalName == other.heldItemInternalName &&
            exp == other.exp &&
            uuid == other.uuid &&
            displayIconTexture == other.displayIconTexture

    private fun activeSlotCount(): Int =
        if (MayorPerkApi.sharingIsCaringActive) EXP_SHARE_SLOTS.size else 1
}
