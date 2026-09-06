package com.skysoft.data.skyblock.pets

import com.skysoft.data.skyblock.ItemListEntry
import com.skysoft.data.skyblock.ItemListEntryKind
import com.skysoft.data.skyblock.SkyBlockDataRepository
import com.skysoft.data.skyblock.SkyBlockItemNames

internal object PetSkinCatalog {
    private var repositoryVersion = -1L
    private var skins: List<ItemListEntry> = emptyList()

    fun colorCode(skinInternalName: String?): String? =
        SkyBlockItemNames.displayName(skinInternalName)?.let { colorCodePattern.find(it)?.value }

    fun findInternalName(petInternalName: String, skinMarker: String?): String? {
        val marker = skinMarker?.takeIf { it.contains('✦') } ?: return null
        val properName = PetInternalNames.properName(petInternalName) ?: return null
        SkyBlockDataRepository.ensureLoaded()
        if (repositoryVersion != SkyBlockDataRepository.snapshotVersion) {
            skins = SkyBlockDataRepository.entries.filter {
                it.key.kind == ItemListEntryKind.SKYBLOCK && it.key.id.startsWith("PET_SKIN_")
            }
            repositoryVersion = SkyBlockDataRepository.snapshotVersion
        }
        val colorCode = colorCodePattern.find(marker)?.value
        return skins.singleOrNull {
            PetSkins.isSkinForPet(it.key.id, properName) &&
                (colorCode == null || it.formattedDisplayName.startsWith(colorCode))
        }?.key?.id
    }

    private val colorCodePattern = Regex("""§.""")
}
