package com.skysoft.features.profit

import com.skysoft.config.ProfitTrackerPriceSource
import com.skysoft.data.ProfileStorageApi
import com.skysoft.data.skyblock.price.SkyBlockPriceData

internal var farmingKernelProfitItem: FarmingKernelProfitItem
    get() = ProfileStorageApi.storage.profitTracker.farmingKernelItem
        .let { stored -> FarmingKernelProfitItem.entries.firstOrNull { it.name == stored } }
        ?: FarmingKernelProfitItem.FEAST
    set(value) {
        ProfileStorageApi.storage.profitTracker.farmingKernelItem = value.name
        ProfileStorageApi.markDirty()
    }

internal var farmingKernelProfitPriceSource: FarmingKernelProfitPriceSource
    get() = ProfileStorageApi.storage.profitTracker.farmingKernelPriceSource
        .let { stored -> FarmingKernelProfitPriceSource.entries.firstOrNull { it.name == stored } }
        ?: FarmingKernelProfitPriceSource.TRACKER_DEFAULT
    set(value) {
        ProfileStorageApi.storage.profitTracker.farmingKernelPriceSource = value.name
        ProfileStorageApi.markDirty()
    }

internal fun farmingKernelProfit(
    kernels: Long,
    item: FarmingKernelProfitItem,
    priceSource: ProfitTrackerPriceSource,
): Double? {
    if (kernels == 0L) return 0.0
    val price = if (item.isBazaar) {
        profitTrackerSourcePrice(
            SkyBlockPriceData.getBazaarPrice(item.itemId),
            SkyBlockPriceData.getNpcSellPrices(item.itemId).coins,
            priceSource,
        )
    } else {
        SkyBlockPriceData.getLowestBin(item.itemId)?.toDouble()
    }
    return price?.takeIf { it.isFinite() && it > 0.0 }?.let { kernels * it / item.totalKernelCost }
}

internal enum class FarmingKernelProfitItem(
    private val displayName: String,
    val itemId: String,
    val totalKernelCost: Int,
) {
    FEAST("Feast I", "ENCHANTMENT_FEAST_1", 25),
    FRESHLY_BAKED_TALISMAN("Freshly Baked Talisman", "FRESHLY_BAKED_TALISMAN", 25),
    FRESHLY_BAKED_RING("Freshly Baked Ring", "FRESHLY_BAKED_RING", 125),
    FRESHLY_BAKED_ARTIFACT("Freshly Baked Artifact", "FRESHLY_BAKED_ARTIFACT", 375),
    FRESHLY_BAKED_RELIC("Freshly Baked Relic", "FRESHLY_BAKED_RELIC", 875),
    FRESHLY_BAKED_HEIRLOOM("Freshly Baked Heirloom", "FRESHLY_BAKED_HEIRLOOM", 1875),
    FINNS_FOCACCIA("Finn's Focaccia", "FINNS_FOCACCIA", 50),
    TEDS_CONTACT_BAKED_IN_BREAD("Ted's Contact Baked in Bread", "TEDS_CONTACT_BAKED_IN_BREAD", 500),
    SCOTTS_CONTACT_BAKED_IN_BREAD("Scott's Contact Baked in Bread", "SCOTTS_CONTACT_BAKED_IN_BREAD", 500),
    ;

    val isBazaar: Boolean
        get() = this == FEAST

    override fun toString(): String = displayName
}

internal enum class FarmingKernelProfitPriceSource(
    private val displayName: String,
    val source: ProfitTrackerPriceSource?,
) {
    TRACKER_DEFAULT("Tracker Default", null),
    INSTANT_SELL("Instant Sell", ProfitTrackerPriceSource.INSTANT_SELL),
    SELL_ORDER("Sell Order", ProfitTrackerPriceSource.SELL_ORDER),
    BUY_ORDER("Buy Order", ProfitTrackerPriceSource.BUY_ORDER),
    ;

    override fun toString(): String = displayName
}
