package com.skysoft.data.skyblock

import com.skysoft.data.skyblock.pets.PetRepoConstants
import com.skysoft.utils.TextUtilities.removeColor
import java.util.Locale

object SkyBlockItemNames {
    private var itemIdsByDisplayName: Map<String, String> = emptyMap()
    private var itemIdsByNormalizedName: Map<String, String> = emptyMap()
    private var repositoryVersion = -1L

    fun displayName(internalName: String?): String? {
        if (internalName == null) return null
        SkyBlockDataRepository.ensureLoaded()
        return SkyBlockDataRepository.entry(SkyBlockDataRepository.itemKey(internalName))?.formattedDisplayName
    }

    fun itemId(displayName: String): String? {
        SkyBlockDataRepository.ensureLoaded()
        if (repositoryVersion != SkyBlockDataRepository.snapshotVersion) rebuildIndex()
        return itemIdsByDisplayName[displayName]
    }

    fun resolveItemId(displayName: String): String? {
        SkyBlockDataRepository.ensureLoaded()
        val clean = displayName.removeColor()
        val aliases = PetRepoConstants.data.petItemResolution
        val alias = aliases[displayName] ?: aliases[clean] ?: aliases.entries.firstOrNull { (name, id) ->
            name.removeColor() == clean || id.replace('_', ' ').equals(clean, ignoreCase = true)
        }?.value
        if (alias != null) return alias
        if (repositoryVersion != SkyBlockDataRepository.snapshotVersion) rebuildIndex()
        return itemIdsByNormalizedName[displayName.lowercase(Locale.ROOT)]
            ?: itemIdsByNormalizedName[clean.lowercase(Locale.ROOT)]
    }

    private fun rebuildIndex() {
        val entries = SkyBlockDataRepository.entries.filter { entry -> entry.key.kind == ItemListEntryKind.SKYBLOCK }
        itemIdsByDisplayName = index(entries.groupBy(ItemListEntry::displayName))
        val names = entries.flatMap { entry ->
            listOf(entry.formattedDisplayName, entry.displayName).distinct().map { it.lowercase(Locale.ROOT) to entry }
        }
        itemIdsByNormalizedName = index(names.groupBy({ it.first }, { it.second }))
        repositoryVersion = SkyBlockDataRepository.snapshotVersion
    }

    private fun index(groups: Map<String, List<ItemListEntry>>): Map<String, String> = groups
        .mapNotNull { (name, entries) ->
            resolveDisplayNameItemId(entries) { key ->
                SkyBlockDataRepository.info(key)?.obtain?.status
            }?.let { itemId -> name to itemId }
        }
        .toMap()
}

internal fun resolveDisplayNameItemId(
    entries: List<ItemListEntry>,
    obtainStatus: (ItemListEntryKey) -> SkyBlockObtainStatus?,
): String? {
    val candidates = entries.distinctBy { entry -> entry.key.id }
    return candidates.singleOrNull()?.key?.id
        ?: candidates.singleOrNull { entry -> obtainStatus(entry.key) == SkyBlockObtainStatus.OBTAINABLE }?.key?.id
}
