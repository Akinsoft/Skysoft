package com.skysoft.features.pets

import com.skysoft.data.StoredPetData
import com.skysoft.data.skyblock.AttributeShardCatalog
import com.skysoft.data.skyblock.SkillExpGainApi
import com.skysoft.data.skyblock.SkyBlockItemId.skyBlockId
import com.skysoft.data.skyblock.SkyBlockItemUtilities.loreLines
import com.skysoft.data.skyblock.SkyBlockOpenInventorySnapshot
import com.skysoft.data.skyblock.StatsEquipmentMenu
import com.skysoft.data.skyblock.pets.PetRepository
import com.skysoft.features.pets.ActivePetTracker.PetDataAssertionSource
import com.skysoft.features.pets.PetItemUtilities.toExactPetDataOrNull
import com.skysoft.utils.NumberUtilities.formatInt
import com.skysoft.utils.RegexUtilities.group
import com.skysoft.utils.RegexUtilities.groupOrNull
import java.util.UUID
import kotlin.time.Duration.Companion.seconds
import net.minecraft.world.item.ItemStack

internal object PetStorageInventoryReader {
    fun readOpenInventory(snapshot: SkyBlockOpenInventorySnapshot?) {
        if (snapshot == null) return
        val inventoryName = snapshot.title
        val inventoryItems = snapshot.items
        AttributeShardCatalog.readOpenInventory(inventoryName, inventoryItems)
        SkillExpGainApi.readOpenInventory(inventoryName, inventoryItems)

        val exactPetMenuUuids = readPetsMenuItems(inventoryName, inventoryItems)
        readEquipmentPetData(inventoryName, inventoryItems)
        readSelectedPetData(inventoryName, inventoryItems, exactPetMenuUuids)
        PetStorageExpSharing.readExpSharePets(inventoryName, inventoryItems)
    }

    private fun readPetsMenuItems(inventoryName: String?, inventoryItems: Map<Int, ItemStack>): Set<UUID> {
        if (!PetStoragePetItems.isMainPetMenuName(inventoryName)) return emptySet()
        val currentPetUuid = PetStorageService.petStorage.currentPetUuid
        val exactPetUuids = mutableSetOf<UUID>()

        inventoryItems.filter { (slotNumber, stack) ->
            PetStoragePetItems.isPetStackLocation(slotNumber) && stack.skyBlockId() != null
        }.mapNotNull { (_, item) ->
            item.toExactPetDataOrNull()
        }.forEach { petData ->
            petData.uuid?.let(exactPetUuids::add)
            val isCurrentPet = petData.uuid == currentPetUuid
            saveExactPetRead(
                petData,
                syncXp = isCurrentPet || PetXpEstimator.shouldRecordPetMenuRead(petData.uuid),
                assertCurrent = isCurrentPet,
            )
        }
        return exactPetUuids
    }

    private fun readEquipmentPetData(inventoryName: String?, inventoryItems: Map<Int, ItemStack>) {
        if (!StatsEquipmentMenu.isTitle(inventoryName)) return
        val currentPetItem = inventoryItems[EQUIP_MENU_CURRENT_PET_SLOT]?.takeIf {
            it.hoverName.string != "Empty Pet Slot"
        } ?: return
        val data = currentPetItem.toExactPetDataOrNull() ?: return
        saveExactPetRead(data, syncXp = true, assertCurrent = true)
    }

    private fun readSelectedPetData(
        inventoryName: String?,
        inventoryItems: Map<Int, ItemStack>,
        exactPetMenuUuids: Set<UUID>,
    ) {
        val isPetMenu = PetStoragePetItems.isMainPetMenuName(inventoryName)
        if (isPetMenu && PetStorageService.lastExactPetMenuClick.passedSince() < 5.seconds) return

        val petItemSlot = when {
            isPetMenu -> PET_MENU_CURRENT_PET_SLOT
            inventoryName == "SkyBlock Menu" -> SB_MENU_CURRENT_PET_SLOT
            else -> return
        }
        val currentPetItem = inventoryItems[petItemSlot] ?: return
        if (PetStoragePetItems.readExactSelectedPetData(currentPetItem) == PetStoragePetItems.PetDataReadResult.READ) return
        val lore = currentPetItem.loreLines().takeIf { it.isNotEmpty() } ?: return
        lore.any { line ->
            petMenuSelectedPetNamePattern.find(line)?.let { didReadSelectedPetLore(it, lore, exactPetMenuUuids) } == true
        }
    }

    private fun didReadSelectedPetLore(match: MatchResult, lore: List<String>, exactPetMenuUuids: Set<UUID>): Boolean {
        val petName = match.group("pet")
        val rarity = PetStoragePetItems.rarityOrNull(match) ?: return false
        val petInternalName = PetRepository.petWithRarityToInternalName(petName, rarity)
        val petSkin = PetStoragePetItems.petSkinInternalNameOrNull(match, petInternalName)
        val skinTag = PetStoragePetItems.skinTagOrNull(match)
        val level = lore.firstNotNullOfOrNull { line ->
            petMenuSelectedPetProgressPattern.find(line)?.let { progressMatch ->
                progressMatch.groupOrNull("next")?.formatInt()?.minus(1) ?: PetRepository.getMaxLevel(petInternalName)
            }
        } ?: return false
        val petExp = PetStoragePetItems.readMenuPetExp(lore, level, petInternalName)
        val resolvedPet = PetStorageService.resolvePetDataOrNull(
            name = petName,
            skinTag = skinTag,
            skinTagKnown = true,
            rarity = rarity,
            level = level,
            exp = petExp?.value,
        )
        val matchingCurrentPet = ActivePetTracker.currentPet?.takeIf {
            PetStoragePetItems.matchesSelectedPet(it, petName, rarity, level, skinTag)
        }
        val currentPetData = resolvedPet ?: matchingCurrentPet ?: StoredPetData(
            petInternalName = petInternalName,
            skinInternalName = petSkin,
            exp = petExp?.value ?: PetRepository.levelToXp(level, petInternalName) ?: 0.0,
        )
        val hasExactPetMenuRead = currentPetData.uuid?.let { it in exactPetMenuUuids } == true
        val exactPetExp = petExp.exactValue.takeUnless { hasExactPetMenuRead }
            ?.let { PetStoragePetItems.reconcileDisplayedExp(currentPetData, it) }
        saveDisplayedPetRead(
            currentPetData,
            exactExp = exactPetExp,
            skinInternalName = petSkin,
            heldItemInternalName = null,
            source = PetDataAssertionSource.MENU,
        )
        return true
    }
}
