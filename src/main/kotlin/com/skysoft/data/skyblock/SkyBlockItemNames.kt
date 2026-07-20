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
                entries.map { entry -> entry.key.id }
                    .distinct()
                    .singleOrNull()
                    ?.let { itemId -> name to itemId }
            }
            .toMap()
        repositoryVersion = SkyBlockDataRepository.snapshotVersion
    }
}
