package com.skysoft.features.pets

import com.skysoft.config.SkysoftConfigGui
import com.skysoft.data.StoredPetData
import com.skysoft.data.ProfileStorageApi
import com.skysoft.data.skyblock.AccessoryBagData
import com.skysoft.data.skyblock.SkyBlockRarity
import com.skysoft.data.hypixel.SkyBlockProfileApi
import com.skysoft.data.hypixel.TabListApi
import com.skysoft.data.skyblock.MayorPerkApi
import com.skysoft.features.pets.PetItemUtilities.getPetInfo
import com.skysoft.utils.ElapsedTimeMark
import com.skysoft.utils.SkysoftClientEvents
import com.skysoft.utils.chat.ChatEvents
import java.util.UUID
import net.minecraft.world.inventory.Slot

object PetStorageService {
    internal val config get() = SkysoftConfigGui.config().pets
    internal val petStorage get() = ProfileStorageApi.storage

    internal var lastExactPetMenuClick = ElapsedTimeMark.farPast()
    internal var ticks = 0
    internal var lastInventoryKey: String? = null

    val isPetWidgetReadyForDisplay: Boolean
        get() = PetWidgetStateTracker.isReadyForDisplay

    val petWidgetDisplayMessage: List<String>?
        get() = PetWidgetStateTracker.displayMessage

    fun register() {
        ProfileStorageApi.registerConsumer("Pet Storage", PetFeatureDemand::isActive)
        TabListApi.registerConsumer("Pet Storage", PetFeatureDemand::isActive)
        MayorPerkApi.registerConsumer("Pet Storage", PetFeatureDemand::isActive)
        SkyBlockProfileApi.onProfileChange("Pet Storage profile reset", PetFeatureDemand::isActive) {
            PetWidgetStateTracker.reset()
            lastInventoryKey = null
        }
        ChatEvents.onVisibleMessage(
            "Pet Storage chat",
            isActive = PetFeatureDemand::isActive,
        ) { message ->
            PetStorageChat.handleIncomingMessage(message.component)
        }
        SkysoftClientEvents.onEndTick(
            "Pet Storage inventory reader",
            isActive = PetFeatureDemand::isActive,
        ) {
            PetStorageInventoryReader.onClientTick()
        }
        SkysoftClientEvents.onDisconnect("Pet Storage disconnect reset") {
            PetWidgetStateTracker.reset()
            lastInventoryKey = null
        }
    }

    fun markDirty() {
        ProfileStorageApi.markDirty()
    }

    @JvmStatic
    fun onSlotClick(slot: Slot?, slotId: Int, clickedButton: Int) {
        if (!PetFeatureDemand.isActive()) return
        AccessoryBagData.onSlotClick(slot, clickedButton)
        PetStorageClickHandler.onSlotClick(slot, slotId, clickedButton)
    }

    @JvmStatic
    fun onUseItem() {
        if (PetFeatureDemand.isActive()) PetStorageHeldPetAdd.recordUseItem()
    }

    fun getActiveExpSharePets(): List<StoredPetData> = PetStorageExpSharing.activePets()

    fun getActiveExpSharePetUuids(): Set<UUID> = PetStorageExpSharing.activeUuids()

    fun getDisabledExpSharePetUuids(): Set<UUID> = PetStorageExpSharing.disabledUuids()

    fun isExpShareSlotDisabled(slot: Int): Boolean = PetStorageExpSharing.isSlotDisabled(slot)

    fun isExpSharingInventory(inventoryName: String?): Boolean = PetStorageExpSharing.isInventory(inventoryName)

    @JvmStatic
    fun shouldHighlightActivePetSlot(inventoryName: String?, slot: Slot): Boolean {
        if (!config.highlightActivePet) return false
        if (!PetStoragePetItems.isMainPetMenuName(inventoryName)) return false
        if (!PetStoragePetItems.isPetStackLocation(slot.containerSlot)) return false
        val stack = slot.item.takeUnless { it.isEmpty } ?: return false
        val petInfo = stack.getPetInfo()
        if (petInfo?.active == true) return true
        if (PetStoragePetItems.isCurrentPetStack(stack)) return true
        val currentPetUuid = petStorage.currentPetUuid ?: return false
        return petInfo?.ownedUuid == currentPetUuid
    }

    fun resolvePetDataOrNull(
        name: String,
        rarity: SkyBlockRarity? = null,
        heldItem: String? = null,
        heldItemKnown: Boolean = false,
        skinTag: String? = null,
        skinTagKnown: Boolean = false,
        level: Int? = null,
        exp: Double? = null,
        expErrorFactor: Double = 0.01,
    ): StoredPetData? = petStorage.pets
        .filter { it.uuid != null }
        .filter { it.matchesDisplayName(name) }
        .filter { rarity == null || it.rarity == rarity }
        .filter {
            when {
                heldItem != null -> it.heldItemInternalName == heldItem
                heldItemKnown -> it.heldItemInternalName == null
                else -> true
            }
        }
        .filter { PetStoragePetItems.matchesSkinTag(it, skinTag, skinTagKnown) }
        .filter { level == null || it.level == level }
        .singleOrNull { PetStoragePetItems.hasMatchingExp(it, exp, expErrorFactor) }
}
