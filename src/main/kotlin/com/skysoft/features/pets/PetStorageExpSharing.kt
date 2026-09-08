package com.skysoft.features.pets

import com.skysoft.SkysoftMod
import com.skysoft.data.StoredPetData
import com.skysoft.data.ProfileStorageApi
import com.skysoft.data.skyblock.MayorPerkApi
import com.skysoft.data.skyblock.SkyBlockItemId.skyBlockId
import com.skysoft.features.pets.PetItemUtilities.getPetInfo
import com.skysoft.features.pets.PetItemUtilities.toExactPetDataOrNull
import net.minecraft.world.item.ItemStack
import java.util.UUID

internal object PetStorageExpSharing {
    fun readExpSharePets(inventoryName: String?, inventoryItems: Map<Int, ItemStack>) {
        if (inventoryName != EXP_SHARING_INVENTORY_NAME) return
        val expSharePets = EXP_SHARE_SLOTS.map { expShareSlot ->
            val slotItem = inventoryItems[expShareSlot]?.takeIf {
                it.hoverName.string != "No pet in slot"
            } ?: return@map null
            readExpSharePetUuid(expShareSlot, slotItem)
        }
        if (PetStorageService.petStorage.expSharePets == expSharePets) return
        ProfileStorageApi.updateProfile { profile ->
            profile.expSharePets.clear()
            profile.expSharePets.addAll(expSharePets)
        }
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

    private fun readExpSharePetUuid(slot: Int, stack: ItemStack): UUID? {
        val exactPetData = stack.toExactPetDataOrNull()
        val exactPetUuid = exactPetData?.uuid
        if (exactPetData != null && exactPetUuid != null) {
            PetStorageService.storePet(exactPetData)
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

    private fun activeSlotCount(): Int =
        if (MayorPerkApi.sharingIsCaringActive) EXP_SHARE_SLOTS.size else 1
}
