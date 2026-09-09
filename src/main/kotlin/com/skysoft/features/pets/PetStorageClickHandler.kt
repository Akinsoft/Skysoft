package com.skysoft.features.pets

import com.skysoft.data.StoredPetData
import com.skysoft.data.ProfileStorageApi
import com.skysoft.features.pets.ActivePetTracker.PetDataAssertionSource
import com.skysoft.utils.MinecraftClient
import com.skysoft.utils.ElapsedTimeMark
import com.skysoft.utils.input.InputUtilities
import java.util.UUID
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.ItemStack

internal object PetStorageClickHandler {
    fun onSlotClick(slot: Slot?, slotId: Int, clickedButton: Int) {
        val screen = MinecraftClient.screen()
        val inventoryName = screen?.title?.string
        if (!PetStoragePetItems.isMainPetMenuName(inventoryName)) return
        if (!PetStoragePetItems.isPetStackLocation(slotId)) return
        val clickedItem = slot?.item?.takeUnless { it.isEmpty } ?: return
        val clickedPetData = PetStoragePetItems.toClickedPetDataOrNull(clickedItem) ?: return
        val clickedPetUuid = clickedPetData.uuid
        val currentPetUuid = PetStorageService.petStorage.currentPetUuid
        when (clickedButton) {
            1 -> removeClickedPet(clickedPetUuid, currentPetUuid)
            0 -> selectClickedPet(clickedItem, clickedPetData, clickedPetUuid, currentPetUuid)
            else -> Unit
        }
    }

    private fun removeClickedPet(clickedPetUuid: UUID?, currentPetUuid: UUID?) {
        clickedPetUuid ?: return
        ProfileStorageApi.updateProfile { profile ->
            profile.pets.removeIf { it.uuid == clickedPetUuid }
            profile.expSharePets.replaceAll { uuid -> uuid.takeUnless { it == clickedPetUuid } }
        }
        if (currentPetUuid == clickedPetUuid) ActivePetTracker.clearCurrentPet()
    }

    private fun selectClickedPet(
        clickedItem: ItemStack,
        clickedPetData: StoredPetData,
        clickedPetUuid: UUID?,
        currentPetUuid: UUID?,
    ) {
        if (InputUtilities.isShiftDown()) return
        PetStorageService.lastExactPetMenuClick = ElapsedTimeMark.now()
        if (PetStoragePetItems.isCurrentPetStack(clickedItem) || currentPetUuid == clickedPetUuid) {
            ActivePetTracker.clearCurrentPet()
        } else {
            ActivePetTracker.assertFoundCurrentData(clickedPetData, PetDataAssertionSource.MENU)
        }
    }
}
