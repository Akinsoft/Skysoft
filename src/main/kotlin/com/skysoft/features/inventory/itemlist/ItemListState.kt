package com.skysoft.features.inventory.itemlist

import com.skysoft.config.ItemListConfig
import com.skysoft.config.SkysoftConfigGui
import com.skysoft.data.skyblock.ItemListEntryKey
import com.skysoft.utils.SkysoftClientEvents

internal object ItemListState {
    var search = ""
    var page = 0
    var isTemporarilyHidden = false

    private val config: ItemListConfig get() = SkysoftConfigGui.config().inventory.itemList

    fun register() {
        SkysoftClientEvents.onDisconnect("Item List disconnect reset") {
            search = ""
            page = 0
            isTemporarilyHidden = false
        }
    }

    fun isFavorite(key: ItemListEntryKey): Boolean = key.serialized() in config.favorites

    fun toggleFavorite(key: ItemListEntryKey) {
        val serialized = key.serialized()
        if (!config.favorites.remove(serialized)) {
            config.favorites.add(0, serialized)
            while (config.favorites.size > ItemListConfig.MAX_FAVORITES) config.favorites.removeLast()
        }
        SkysoftConfigGui.config().saveNow()
    }

    fun favorites(): List<ItemListEntryKey> = config.favorites.mapNotNull(ItemListEntryKey::parse)
}
