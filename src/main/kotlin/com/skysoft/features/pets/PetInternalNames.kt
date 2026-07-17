package com.skysoft.features.pets

import com.skysoft.data.skyblock.SkyBlockRarity

internal object PetInternalNames {
    fun properName(internalName: String): String? = split(internalName)?.first

    fun rarity(internalName: String): SkyBlockRarity? = split(internalName)?.second

    fun split(internalName: String): Pair<String, SkyBlockRarity>? {
        val parts = internalName.split(";")
        if (parts.size < PET_INTERNAL_NAME_PART_COUNT) return null
        val name = parts[0].takeIf { it.isNotBlank() }?.let(::canonicalName) ?: return null
        val rarityId = parts[1].toIntOrNull() ?: return null
        val rarity = SkyBlockRarity.getById(rarityId) ?: return null
        return name to rarity
    }

    fun canonicalName(name: String): String =
        if (name == "T-REX") "TYRANNOSAURUS" else name
}

private const val PET_INTERNAL_NAME_PART_COUNT = 2
