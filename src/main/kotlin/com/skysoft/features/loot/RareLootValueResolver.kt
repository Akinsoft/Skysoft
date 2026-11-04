package com.skysoft.features.loot

import com.skysoft.data.skyblock.price.SkyBlockPriceData

internal object RareLootValueResolver {
    fun resolve(drop: RareLootDrop): Double? =
        resolve(drop.itemId, drop.amount)

    fun resolve(itemId: String?, amount: Int): Double? {
        val cleanItemId = itemId?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val bazaar = SkyBlockPriceData.getBazaarPrice(cleanItemId)
        val price = bazaar?.sellOrderPrice?.takeIf { it > 0.0 }
            ?: bazaar?.instantSellPrice?.takeIf { it > 0.0 }
            ?: SkyBlockPriceData.getLowestBin(cleanItemId)?.takeIf { it > 0L }?.toDouble()
            ?: return null
        return price * amount.coerceAtLeast(1).toDouble()
    }
}
