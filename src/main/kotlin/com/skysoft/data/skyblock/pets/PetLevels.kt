package com.skysoft.data.skyblock.pets

import java.util.concurrent.ConcurrentHashMap

internal object PetLevels {
    private val fullTrees = ConcurrentHashMap<String, CachedLevelTree>()

    fun fullTree(petInternalName: String): List<Int> {
        val constants = PetRepoConstants.data
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
        val constants = PetRepoConstants.data
        constants.customPetLeveling[properName]?.rarityOffset?.get(rarity)?.let { return it }
        return constants.rarityOffsets[rarity]
    }

    private data class CachedLevelTree(
        val constants: SkysoftPetsRepoJson,
        val levels: List<Int>,
    )
}
