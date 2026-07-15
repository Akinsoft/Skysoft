package com.skysoft.features.pets

import com.skysoft.data.StoredPetData
import com.skysoft.data.hypixel.HypixelLocationState
import com.skysoft.data.hypixel.TabListApi
import com.skysoft.data.skyblock.AccessoryBagData
import com.skysoft.data.skyblock.AttributeShardCatalog
import com.skysoft.data.skyblock.SkyBlockRarity
import com.skysoft.data.skyblock.SkyBlockItemId.skyBlockId
import com.skysoft.data.skyblock.SkyBlockItemUtilities.loreLines
import com.skysoft.features.pets.ActivePetTracker.PetDataAssertionSource
import com.skysoft.features.pets.PetItemUtilities.toExactPetDataOrNull
import com.skysoft.utils.MinecraftClient
import com.skysoft.utils.NumberUtilities.formatDoubleOrNull
import com.skysoft.utils.NumberUtilities.formatInt
import com.skysoft.utils.RegexUtilities.group
import com.skysoft.utils.RegexUtilities.groupOrNull
import com.skysoft.utils.TextUtilities.cleanSkyBlockText
import com.skysoft.utils.TextUtilities.formattedText
import com.skysoft.utils.TextUtilities.removeResets
import com.skysoft.utils.gui.nonPlayerInventoryItems
import com.skysoft.utils.gui.nonPlayerInventoryKey
import java.util.UUID
import kotlin.time.Duration.Companion.seconds
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.world.item.ItemStack

internal object PetStorageInventoryReader {
    fun onClientTick() {
        val inSkyBlock = HypixelLocationState.inSkyBlock
        PetWidgetStateTracker.syncLoadingState()

        if (inSkyBlock) {
            readOpenInventory()
            if (++PetStorageService.ticks % PET_TAB_WIDGET_READ_INTERVAL_TICKS == 0) readPetTabWidget()
        } else {
            PetStorageService.ticks = 0
        }
    }

    fun readOpenInventory() {
        val screen = MinecraftClient.screen() as? AbstractContainerScreen<*> ?: run {
            PetStorageService.lastInventoryKey = null
            return
        }
        val inventoryName = screen.title.cleanSkyBlockText()
        val key = screen.nonPlayerInventoryKey(inventoryName)
        val inventoryItems = screen.nonPlayerInventoryItems()
        AttributeShardCatalog.readOpenInventory(inventoryName, inventoryItems)
        SkillExpGainApi.readOpenInventory(inventoryName, inventoryItems)
        AccessoryBagData.readOpenInventory(inventoryName, inventoryItems, screen.menu.containerId)

        if (key == PetStorageService.lastInventoryKey) return
        PetStorageService.lastInventoryKey = key

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
        if (exactPetUuids.isNotEmpty()) PetStorageService.markDirty()
        return exactPetUuids
    }

    private fun readEquipmentPetData(inventoryName: String?, inventoryItems: Map<Int, ItemStack>) {
        if (inventoryName != "Your Equipment and Stats") return
        val currentPetItem = inventoryItems[EQUIP_MENU_CURRENT_PET_SLOT]?.takeIf {
            it.hoverName.string != "Empty Pet Slot"
        } ?: return
        val data = currentPetItem.toExactPetDataOrNull() ?: return
        saveExactPetRead(data, syncXp = true, assertCurrent = true)
        PetStorageService.markDirty()
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
        val currentPetItemLore = currentPetItem.loreLines().takeIf { it.isNotEmpty() } ?: return

        currentPetItemLore.firstNotNullOfOrNull { line ->
            petMenuSelectedPetNamePattern.find(line)?.let { match ->
                val petName = match.group("pet")
                val rarity = PetStoragePetItems.rarityOrNull(match) ?: return@let null
                val petInternalName = PetRepository.petWithRarityToInternalName(petName, rarity)
                val petSkin = PetStoragePetItems.petSkinInternalNameOrNull(match, petInternalName)
                val skinTag = match.groupOrNull("skin")?.replace(" ", "")
                val level = currentPetItemLore.firstNotNullOfOrNull { progressLine ->
                    petMenuSelectedPetProgressPattern.find(progressLine)?.let { progressMatch ->
                        progressMatch.groupOrNull("next")?.formatInt()?.minus(1)
                            ?: PetRepository.getMaxLevel(petInternalName)
                    }
                } ?: return@let null
                val petExp = currentPetItemLore.firstNotNullOfOrNull { xpLine ->
                    petMenuSelectedPetXpPattern.find(xpLine)?.let { xpMatch ->
                        val current = xpMatch.group("current")
                        val currentValue = current.formatDoubleOrNull() ?: return@let null
                        val exact = PetStoragePetItems.isExactPetExpText(current)
                        when (xpMatch.groupOrNull("next")) {
                            null -> PetExpRead(currentValue, exact)
                            else -> {
                                val currentLevelXp = PetRepository.levelToXp(level, petInternalName) ?: 0.0
                                PetExpRead(currentLevelXp + currentValue, exact)
                            }
                        }
                    }
                }
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
                val previousExp = currentPetData.exp
                val exactPetExp = petExp.exactValue.takeUnless { hasExactPetMenuRead }
                    ?.let { PetStoragePetItems.reconcileDisplayedExp(currentPetData, it) }
                PetStoragePetItems.applyKnownData(currentPetData, exp = exactPetExp, skinInternalName = petSkin)
                PetXpEstimator.resyncFromPetDataRead(
                    currentPetData,
                    exact = exactPetExp != null,
                    previousExp = previousExp,
                    appliedExp = exactPetExp,
                )
                ActivePetTracker.assertFoundCurrentData(currentPetData, PetDataAssertionSource.MENU)
                PetStorageService.markDirty()
                true
            }
        }
    }

    private fun readPetTabWidget() {
        if (!TabListApi.isSkyBlockDataLoaded) return
        val lines = TabListApi.skyBlockLines
        var foundUsableWidgetPet = false
        for (component in lines) {
            val match = petTabWidgetNamePattern.matchEntire(component.string) ?: continue
            val petName = match.group("pet")
            val level = match.group("level").formatInt()
            val rarity = SkyBlockRarity.getByComponent(component, petName) ?: continue
            val petInternalName = PetRepository.petWithRarityToInternalName(petName, rarity)
            val petSkin = PetStoragePetItems.petSkinInternalNameOrNull(match, petInternalName)
            val petSkinTag = (match.groupOrNull("skin") ?: match.groupOrNull("altskin"))?.replace(" ", "")
            val skinTagAbsenceKnown = petSkinTag == null
            val petHeldItem = lines.firstNotNullOfOrNull { line ->
                val itemName = line.formattedText()
                    .trim()
                    .removeResets()
                    .takeIf { it.isNotBlank() }
                    ?: return@firstNotNullOfOrNull null
                PetRepository.resolvePetItemOrNull(itemName)
            }

            var maxedWithoutOverflowXp = false
            val petExp = lines.firstNotNullOfOrNull { line ->
                petTabWidgetXpPattern.matchEntire(line.string)?.let { xpMatch ->
                    if (xpMatch.groupOrNull("max") != null) {
                        maxedWithoutOverflowXp = true
                        return@let null
                    }
                    val currentLevelXp = PetRepository.levelToXp(level, petInternalName) ?: return@let null
                    val current = xpMatch.groupOrNull("current") ?: return@let null
                    val readXpGroup = current.formatDoubleOrNull() ?: return@let null
                    PetExpRead(currentLevelXp + readXpGroup, PetStoragePetItems.isExactPetExpText(current))
                }
            }

            val resolvedPet = PetStorageService.resolvePetDataOrNull(
                name = petName,
                rarity = rarity,
                level = level,
                heldItem = petHeldItem,
                skinTag = petSkinTag,
                skinTagKnown = skinTagAbsenceKnown,
                exp = petExp?.value,
            )
            val matchingCurrentPet = ActivePetTracker.currentPet?.takeIf { currentPet ->
                currentPet.matchesDisplayName(petName) &&
                    currentPet.rarity == rarity &&
                    currentPet.level == level &&
                    PetStoragePetItems.matchesSkinTag(currentPet, petSkinTag, skinTagKnown = false)
            }
            val currentPetData = resolvedPet ?: matchingCurrentPet ?: StoredPetData(
                petInternalName = petInternalName,
                skinInternalName = petSkin,
                heldItemInternalName = petHeldItem,
                exp = petExp?.value ?: PetRepository.levelToXp(level, petInternalName) ?: 0.0,
            )
            val previousExp = currentPetData.exp
            val exactPetExp = petExp.exactValue?.let { PetStoragePetItems.reconcileDisplayedExp(currentPetData, it) }
            val appliedExactPetExp = exactPetExp?.takeUnless {
                PetXpEstimator.shouldIgnoreStalePetWidgetRead(
                    currentPetData,
                    readExp = it,
                    previousExp = previousExp,
                )
            }
            PetStoragePetItems.applyKnownData(
                currentPetData,
                exp = appliedExactPetExp,
                skinInternalName = petSkin,
                heldItemInternalName = petHeldItem,
            )
            PetXpEstimator.resyncFromPetDataRead(
                currentPetData,
                exact = appliedExactPetExp != null,
                previousExp = previousExp,
                appliedExp = appliedExactPetExp,
            )
            ActivePetTracker.assertFoundCurrentData(currentPetData, PetDataAssertionSource.TAB)
            if (maxedWithoutOverflowXp) {
                PetWidgetStateTracker.setMaxedWithoutOverflowXp()
                foundUsableWidgetPet = true
            } else if (exactPetExp != null) {
                PetWidgetStateTracker.setReady()
                foundUsableWidgetPet = true
            }
            PetStorageService.markDirty()
        }
        if (!foundUsableWidgetPet) {
            PetWidgetStateTracker.setNotReady()
        }
    }

    private val PetExpRead?.exactValue get() = this?.takeIf { it.exact }?.value
    private const val PET_TAB_WIDGET_READ_INTERVAL_TICKS = 10
}
