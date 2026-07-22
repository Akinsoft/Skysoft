package com.skysoft.features.pets

object CanonicalItemNames {
    fun resolve(internalName: String): String? {
        PetRepoCache.itemNames[internalName]?.let { return it }
        LocalSkyBlockCatalog.itemNameOrNull(internalName)?.let { itemName ->
            PetRepoCache.itemNames[internalName] = itemName
            return itemName
        }
        RemoteSkyBlockCatalog.requestItem(internalName)
        return null
    }
}
