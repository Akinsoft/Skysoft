package com.skysoft.data.skyblock

object SkyBlockItemNames {
    private var itemIdsByDisplayName: Map<String, String> = emptyMap()
    private var repositoryVersion = -1L

    fun itemId(displayName: String): String? {
        if (repositoryVersion != SkyBlockDataRepository.snapshotVersion) rebuildIndex()
        return itemIdsByDisplayName[displayName]
    }

    private fun rebuildIndex() {
        itemIdsByDisplayName = SkyBlockDataRepository.entries
            .asSequence()
            .filter { entry -> entry.key.kind == ItemListEntryKind.SKYBLOCK }
            .groupBy(ItemListEntry::displayName)
            .mapNotNull { (name, entries) ->
                resolveDisplayNameItemId(entries) { key ->
                    SkyBlockDataRepository.info(key)?.obtain?.status
                }?.let { itemId -> name to itemId }
            }
            .toMap()
        repositoryVersion = SkyBlockDataRepository.snapshotVersion
    }
}

internal fun resolveDisplayNameItemId(
    entries: List<ItemListEntry>,
    obtainStatus: (ItemListEntryKey) -> SkyBlockObtainStatus?,
): String? {
    val candidates = entries.distinctBy { entry -> entry.key.id }
    return candidates.singleOrNull()?.key?.id
        ?: candidates.singleOrNull { entry -> obtainStatus(entry.key) == SkyBlockObtainStatus.OBTAINABLE }?.key?.id
}
