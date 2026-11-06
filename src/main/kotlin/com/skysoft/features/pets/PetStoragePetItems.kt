package com.skysoft.features.pets

import com.skysoft.data.skyblock.pets.PetInternalNames
import com.skysoft.data.skyblock.pets.PetRepository
import com.skysoft.data.skyblock.pets.PetSkinCatalog
import com.skysoft.data.skyblock.SkyBlockItemNames
import com.skysoft.data.StoredPetData
import com.skysoft.data.skyblock.SkyBlockRarity
import com.skysoft.data.skyblock.SkyBlockItemId.skyBlockId
import com.skysoft.data.skyblock.SkyBlockItemUtilities.formattedHoverName
import com.skysoft.data.skyblock.SkyBlockItemUtilities.loreLines
import com.skysoft.data.skyblock.SkyBlockItemUtilities.playerHeadTexture
import com.skysoft.features.pets.PetItemUtilities.getPetInfo
import com.skysoft.features.pets.PetItemUtilities.toExactPetDataOrNull
import com.skysoft.utils.NumberUtilities.formatDoubleOrNull
import com.skysoft.utils.NumberUtilities.formatInt
import com.skysoft.utils.RegexUtilities.group
import com.skysoft.utils.RegexUtilities.groupOrNull
import com.skysoft.utils.TextUtilities.removeColor
import kotlin.math.abs
import net.minecraft.world.item.ItemStack

internal object PetStoragePetItems {
    fun toClickedPetDataOrNull(item: ItemStack): StoredPetData? {
        val petInfo = item.getPetInfo() ?: return toVisiblePetDataOrNull(item)
        return StoredPetData(
            petInternalName = "${petInfo.type};${petInfo.tier.id}",
            skinInternalName = petInfo.properSkinItem,
            heldItemInternalName = petInfo.heldItem,
            exp = petInfo.exp,
            uuid = petInfo.ownedUuid,
            displayIconTexture = item.playerHeadTexture(),
            exactItemStack = item.copy(),
        )
    }

    private fun toVisiblePetDataOrNull(item: ItemStack): StoredPetData? {
        val match = petMenuPetStackNamePattern.matchEntire(item.formattedHoverName()) ?: return null
        val level = match.group("level").formatInt()
        val petName = match.group("pet").trim()
        val itemInternalName = item.skyBlockId()?.takeIf { PetInternalNames.rarity(it) != null }
        val petInternalName = itemInternalName ?: run {
            val rarity = rarityOrNull(match) ?: return null
            PetRepository.petWithRarityToInternalName(petName, rarity)
        }
        return StoredPetData(
            petInternalName = petInternalName,
            skinInternalName = petSkinInternalNameOrNull(match, petInternalName),
            exp = readMenuPetExp(item.loreLines(), level, petInternalName)?.value
                ?: PetRepository.levelToXp(level, petInternalName)
                ?: 0.0,
            displayIconTexture = item.playerHeadTexture(),
            exactItemStack = item.copy(),
        )
    }

    fun readExactSelectedPetData(item: ItemStack): PetDataReadResult {
        val currentPetData = item.toExactPetDataOrNull() ?: return PetDataReadResult.UNAVAILABLE
        saveExactPetRead(currentPetData, syncXp = true, assertCurrent = true)
        return PetDataReadResult.READ
    }

    fun isCurrentPetStack(item: ItemStack): Boolean =
        item.loreLines().any { it.contains("Click to despawn") }

    fun readMenuPetExp(lore: List<String>, level: Int, petInternalName: String): PetExpRead? =
        lore.firstNotNullOfOrNull { line ->
            val xpMatch = petMenuSelectedPetXpPattern.find(line) ?: return@firstNotNullOfOrNull null
            val current = xpMatch.group("current")
            val currentValue = current.formatDoubleOrNull() ?: return@firstNotNullOfOrNull null
            val levelStartXp = if (xpMatch.groupOrNull("next") == null) {
                0.0
            } else {
                PetRepository.levelToXp(level, petInternalName) ?: 0.0
            }
            PetExpRead(levelStartXp + currentValue, isExactPetExpText(current))
        }

    fun reconcileDisplayedExp(petData: StoredPetData, readExp: Double): Double {
        val storedExp = petData.exp ?: return readExp
        if (readExp % 1.0 != 0.0) return readExp
        return storedExp.coerceIn(readExp, readExp + DISPLAYED_WHOLE_EXP_MAX_FRACTION)
    }

    fun isExactPetExpText(text: String): Boolean =
        !text.contains('k', ignoreCase = true) && !text.contains('m', ignoreCase = true)

    fun isPetStackLocation(slot: Int): Boolean =
        slot in PET_MENU_FIRST_PET_SLOT..PET_MENU_LAST_PET_SLOT &&
            !isPetMenuSideBorder(slot)

    fun isMainPetMenuName(inventoryName: String?): Boolean =
        inventoryName != null && mainPetMenuNamePattern.matchEntire(inventoryName) != null

    fun matchesSelectedPet(petData: StoredPetData, petName: String, rarity: SkyBlockRarity, level: Int, skinTag: String?): Boolean =
        petData.matchesDisplayName(petName) &&
            petData.rarity == rarity &&
            petData.level <= level &&
            matchesSkinTag(petData, skinTag, skinTagKnown = true)

    fun matchesSkinTag(petData: StoredPetData, skinTag: String?, skinTagKnown: Boolean): Boolean {
        if (!skinTagKnown && skinTag == null) return true
        val storedSkinTag = petData.skinTag?.replace(" ", "")
        val readSkinTag = skinTag?.replace(" ", "")
        return if (readSkinTag?.contains('§') == true) {
            storedSkinTag == readSkinTag
        } else {
            storedSkinTag?.removeColor() == readSkinTag?.removeColor()
        }
    }

    fun matchesHeldItemName(petData: StoredPetData, heldItemName: String): Boolean {
        val internalName = petData.heldItemInternalName ?: return false
        return SkyBlockItemNames.displayName(internalName)
            ?.removeColor()
            ?.equals(heldItemName.removeColor(), ignoreCase = true) == true
    }

    fun hasMatchingExp(petData: StoredPetData, exp: Double?, expErrorFactor: Double): Boolean = exp?.let { readExp ->
        val allowedError = (readExp * expErrorFactor).coerceAtLeast(1.0)
        abs((petData.exp ?: 0.0) - readExp) <= allowedError
    } ?: true

    fun petSkinInternalNameOrNull(match: MatchResult, petInternalName: String): String? =
        PetSkinCatalog.findInternalName(petInternalName, rawSkinTagOrNull(match))

    fun skinTagOrNull(match: MatchResult): String? = rawSkinTagOrNull(match)?.replace(" ", "")

    private fun rawSkinTagOrNull(match: MatchResult): String? =
        match.groupOrNull("skin") ?: match.groupOrNull("altskin")

    fun rarityOrNull(match: MatchResult): SkyBlockRarity? =
        SkyBlockRarity.getByColorCode(match.group("rarity").firstOrNull() ?: return null)

    private fun isPetMenuSideBorder(slot: Int): Boolean {
        val column = slot % PET_MENU_COLUMNS
        return column == 0 || column == PET_MENU_COLUMNS - 1
    }

    enum class PetDataReadResult {
        READ,
        UNAVAILABLE,
    }

    private const val DISPLAYED_WHOLE_EXP_MAX_FRACTION = 0.999
    private const val PET_MENU_FIRST_PET_SLOT = 10
    private const val PET_MENU_LAST_PET_SLOT = 43
    private const val PET_MENU_COLUMNS = 9
}
