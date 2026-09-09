package com.skysoft.data.skyblock

import com.skysoft.data.skyblock.pets.PetRepoConstants
import com.skysoft.utils.TextUtilities.removeColor
import java.util.Locale

object SkyBlockItemNames {
    fun displayName(internalName: String?): String? {
        if (internalName == null) return null
        SkyBlockDataRepository.ensureLoaded()
        return SkyBlockDataRepository.entry(SkyBlockDataRepository.itemKey(internalName))?.formattedDisplayName
    }

    fun itemId(displayName: String): String? {
        SkyBlockDataRepository.ensureLoaded()
        return SkyBlockDataRepository.itemNames?.byDisplayName?.get(displayName)
    }

    fun resolveItemId(displayName: String): String? {
        SkyBlockDataRepository.ensureLoaded()
        val clean = displayName.removeColor()
        val aliases = PetRepoConstants.data.petItemResolution
        val alias = aliases[displayName] ?: aliases[clean] ?: aliases.entries.firstOrNull { (name, id) ->
            name.removeColor() == clean || id.replace('_', ' ').equals(clean, ignoreCase = true)
        }?.value
        if (alias != null) return alias
        val index = SkyBlockDataRepository.itemNames ?: return null
        return index.byNormalizedName[displayName.lowercase(Locale.ROOT)]
            ?: index.byNormalizedName[clean.lowercase(Locale.ROOT)]
    }
}

internal class SkyBlockItemNameIndex(snapshot: SkyBlockDataSnapshot) {
    val byDisplayName: Map<String, String>
    val byNormalizedName: Map<String, String>

    init {
        val entries = snapshot.entries.filter { entry -> entry.key.kind == ItemListEntryKind.SKYBLOCK }
        byDisplayName = index(entries.groupBy(ItemListEntry::displayName), snapshot.itemInfo)
        val names = entries.flatMap { entry ->
            listOf(entry.formattedDisplayName, entry.displayName).distinct().map { it.lowercase(Locale.ROOT) to entry }
        }
        byNormalizedName = index(names.groupBy({ it.first }, { it.second }), snapshot.itemInfo)
    }

    private fun index(
        groups: Map<String, List<ItemListEntry>>,
        info: Map<ItemListEntryKey, SkyBlockItemInfo>,
    ): Map<String, String> = groups
        .mapNotNull { (name, entries) ->
            resolveDisplayNameItemId(entries) { key ->
                info[key]?.obtain?.status
            }?.let { itemId -> name to itemId }
        }
        .toMap()
}

private fun resolveDisplayNameItemId(
    entries: List<ItemListEntry>,
    obtainStatus: (ItemListEntryKey) -> SkyBlockObtainStatus?,
): String? {
    val candidates = entries.distinctBy { entry -> entry.key.id }
    return candidates.singleOrNull()?.key?.id
        ?: candidates.singleOrNull { entry -> obtainStatus(entry.key) == SkyBlockObtainStatus.OBTAINABLE }?.key?.id
}
