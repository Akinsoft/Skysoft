package com.skysoft.data.skyblock.pets

import com.skysoft.data.skyblock.SkyBlockRarity
import java.util.concurrent.ConcurrentHashMap

internal object PetLevels {
    private val fullTrees = ConcurrentHashMap<String, CachedLevelTree>()

    fun fullTree(petInternalName: String): List<Int> {
        val constants = PetRepoCache.petsJson ?: return emptyList()
        val properName = PetInternalNames.properName(petInternalName) ?: return constants.basePetLeveling
        fullTrees[properName]?.takeIf { it.constants === constants }?.let { return it.levels }

        val customLevels = constants.customPetLeveling[properName]?.petLevels.orEmpty()
        val levels = if (customLevels.isEmpty()) {
            constants.basePetLeveling
        } else {
            constants.basePetLeveling + customLevels
        }
        fullTrees[properName] = CachedLevelTree(constants, levels)
        return levels
    }

    fun rarityOffset(petInternalName: String): Int? {
        val (properName, rarity) = PetInternalNames.split(petInternalName) ?: return null
        PetRepoCache.petsJson?.customPetLeveling?.get(properName)?.rarityOffset?.get(rarity)?.let { return it }
        return DEFAULT_RARITY_OFFSETS[rarity]
    }

    private val DEFAULT_RARITY_OFFSETS = mapOf(
        SkyBlockRarity.COMMON to 0,
        SkyBlockRarity.UNCOMMON to 6,
        SkyBlockRarity.RARE to 11,
        SkyBlockRarity.EPIC to 16,
        SkyBlockRarity.LEGENDARY to 20,
        SkyBlockRarity.MYTHIC to 20,
    )

    private data class CachedLevelTree(
        val constants: SkysoftPetsRepoJson,
        val levels: List<Int>,
    )
}
