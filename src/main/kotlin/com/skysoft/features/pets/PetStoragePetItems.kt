package com.skysoft.features.pets

import com.skysoft.data.StoredPetData
import com.skysoft.data.skyblock.SkyBlockRarity
import com.skysoft.data.skyblock.SkyBlockItemId.skyBlockId
import com.skysoft.data.skyblock.SkyBlockItemUtilities.formattedHoverName
import com.skysoft.data.skyblock.SkyBlockItemUtilities.loreLines
import com.skysoft.features.pets.PetItemUtilities.getPetInfo
import com.skysoft.features.pets.PetItemUtilities.playerHeadTextureOrNull
import com.skysoft.features.pets.PetItemUtilities.toExactPetDataOrNull
import com.skysoft.utils.NumberUtilities.formatDoubleOrNull
import com.skysoft.utils.NumberUtilities.formatInt
import com.skysoft.utils.RegexUtilities.group
import com.skysoft.utils.RegexUtilities.groupOrNull
import com.skysoft.utils.TextUtilities.removeColor
import kotlin.math.abs
import net.minecraft.world.item.ItemStack

internal object PetStoragePetItems {
    fun toVisiblePetDataOrNull(item: ItemStack, petInfo: PetItemUtilities.PetInfo? = item.getPetInfo()): StoredPetData? {
        val match = petMenuPetStackNamePattern.matchEntire(item.formattedHoverName()) ?: return null
        val level = match.group("level").formatInt()
        val petName = match.group("pet").trim()
        val itemInternalName = item.skyBlockId()?.takeIf { PetInternalNames.rarity(it) != null }
        val petInternalName = itemInternalName ?: run {
            val rarity = rarityOrNull(match) ?: return null
            PetRepository.petWithRarityToInternalName(petName, rarity)
        }
        val petSkin = petSkinInternalNameOrNull(match, petInternalName)
        val lore = item.loreLines()
        val petExp = lore.firstNotNullOfOrNull { line ->
            petMenuSelectedPetXpPattern.find(line)?.let { xpMatch ->
                val currentValue = xpMatch.group("current").formatDoubleOrNull() ?: return@let null
                when (xpMatch.groupOrNull("next")) {
                    null -> currentValue
                    else -> {
                        val currentLevelXp = PetRepository.levelToXp(level, petInternalName) ?: 0.0
                        currentLevelXp + currentValue
                    }
                }
            }
        }
        val petInfoExp = petInfo?.exp?.takeIf { it > 0.0 || level <= 1 }
        return StoredPetData(
            petInternalName = petInternalName,
            skinInternalName = petInfo?.properSkinItem ?: petSkin,
            heldItemInternalName = petInfo?.heldItem,
            exp = petInfoExp ?: petExp ?: PetRepository.levelToXp(level, petInternalName) ?: 0.0,
            uuid = petInfo?.ownedUuid,
            displayIconTexture = item.playerHeadTextureOrNull(),
            exactItemStack = item.copy(),
        )
    }

    fun toClickedPetDataOrNull(item: ItemStack, petInfo: PetItemUtilities.PetInfo? = item.getPetInfo()): StoredPetData? =
        petInfo?.let {
            StoredPetData(
                petInternalName = "${it.type};${it.tier.id}",
                skinInternalName = it.properSkinItem,
                heldItemInternalName = it.heldItem,
                exp = it.exp,
                uuid = it.ownedUuid,
                displayIconTexture = item.playerHeadTextureOrNull(),
                exactItemStack = item.copy(),
            )
        } ?: toVisiblePetDataOrNull(item, null)

    fun readExactSelectedPetData(item: ItemStack): PetDataReadResult {
        val currentPetData = item.toExactPetDataOrNull() ?: return PetDataReadResult.UNAVAILABLE
        saveExactPetRead(currentPetData, syncXp = true, assertCurrent = true)
        PetStorageService.markDirty()
        return PetDataReadResult.READ
    }

    fun isCurrentPetStack(item: ItemStack): Boolean =
        item.loreLines().any { it.contains("Click to despawn") }

    fun applyKnownData(
        petData: StoredPetData,
        exp: Double? = null,
        skinInternalName: String? = null,
        heldItemInternalName: String? = null,
    ) {
        petData.exp = exp ?: petData.exp
        petData.skinInternalName = skinInternalName ?: petData.skinInternalName
        petData.heldItemInternalName = heldItemInternalName ?: petData.heldItemInternalName
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

    fun hasMatchingExp(petData: StoredPetData, exp: Double?, expErrorFactor: Double): Boolean = exp?.let { readExp ->
        val allowedError = (readExp * expErrorFactor).coerceAtLeast(1.0)
        abs((petData.exp ?: 0.0) - readExp) <= allowedError
    } ?: true

    fun petSkinInternalNameOrNull(match: MatchResult, petInternalName: String): String? =
        PetRepository.findPetSkinInternalNameOrNull(
            petInternalName,
            match.groupOrNull("skin") ?: match.groupOrNull("altskin"),
        )

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
