package com.skysoft.data.skyblock.pets

import com.skysoft.data.skyblock.SkyBlockDataRepository
import com.skysoft.data.skyblock.SkyBlockItemNames

internal object PetSkinCatalog {
    fun colorCode(skinInternalName: String?): String? =
        SkyBlockItemNames.displayName(skinInternalName)?.let { colorCodePattern.find(it)?.value }

    fun findInternalName(petInternalName: String, skinMarker: String?): String? {
        val marker = skinMarker?.takeIf { it.contains('✦') } ?: return null
        val properName = PetInternalNames.properName(petInternalName) ?: return null
        SkyBlockDataRepository.ensureLoaded()
        val colorCode = colorCodePattern.find(marker)?.value
        return SkyBlockDataRepository.petSkinEntries.singleOrNull {
            PetSkins.isSkinForPet(it.key.id, properName) &&
                (colorCode == null || it.formattedDisplayName.startsWith(colorCode))
        }?.key?.id
    }

    private val colorCodePattern = Regex("""§.""")
}
