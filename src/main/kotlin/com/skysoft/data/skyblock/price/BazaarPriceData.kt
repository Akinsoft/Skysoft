package com.skysoft.data.skyblock.price

import com.google.gson.annotations.SerializedName

data class BazaarPriceData(
    val instantBuyPrice: Double,
    val instantSellPrice: Double,
    val buyOrderPrice: Double,
    val sellOrderPrice: Double,
)

data class SkysoftBazaarResponse(
    val success: Boolean = false,
    val cause: String? = null,
    val lastUpdated: Long? = null,
    val fetchedAt: Long = 0,
    val products: Map<String, SkysoftBazaarProduct> = emptyMap(),
)

data class SkysoftBazaarProduct(
    val bestBuyOrder: Double = 0.0,
    @SerializedName(value = "bestSellOrder", alternate = ["bestSellOffer"])
    val bestSellOrder: Double = 0.0,
    val buyOrderAmount: Long = 0,
    @SerializedName(value = "sellOrderAmount", alternate = ["sellOfferAmount"])
    val sellOrderAmount: Long = 0,
    val instantBuyPrice: Double = 0.0,
    val instantSellPrice: Double = 0.0,
    @SerializedName(value = "buyOrderPrice", alternate = ["orderBuyPrice"])
    val buyOrderPrice: Double = 0.0,
    @SerializedName(value = "sellOrderPrice", alternate = ["orderSellPrice"])
    val sellOrderPrice: Double = 0.0,
)

data class LowestBinsResponse(
    val success: Boolean = false,
    val cause: String? = null,
    val fetchedAt: Long = 0,
    val prices: Map<String, Long> = emptyMap(),
)

data class SkysoftBazaarDepthResponse(
    val success: Boolean = false,
    val cause: String? = null,
    val fetchedAt: Long = 0,
    val products: Map<String, SkysoftBazaarDepthProduct> = emptyMap(),
)

data class HypixelSkyBlockItemsResponse(
    val success: Boolean = false,
    val lastUpdated: Long = 0L,
    val items: List<HypixelSkyBlockItem> = emptyList(),
)

data class HypixelSkyBlockItem(
    val id: String = "",
    @SerializedName("npc_sell_price")
    val npcSellPrice: Double? = null,
)

data class SkysoftBazaarDepthProduct(
    val buySummary: List<SkysoftBazaarDepthRow> = emptyList(),
    val sellSummary: List<SkysoftBazaarDepthRow> = emptyList(),
    val flowDeltas: List<SkysoftBazaarFlowDelta> = emptyList(),
    val priceHistory: List<SkysoftBazaarPriceSnapshot> = emptyList(),
)

data class SkysoftBazaarDepthRow(
    val amount: Long = 0,
    val pricePerUnit: Double = 0.0,
    val orders: Long = 0,
)

data class SkysoftBazaarFlowDelta(
    val at: Long = 0,
    val buy: Double = 0.0,
    val sell: Double = 0.0,
)

data class SkysoftBazaarPriceSnapshot(
    val at: Long = 0,
    val instantBuyPrice: Double = 0.0,
    val instantSellPrice: Double = 0.0,
    val buyOrderPrice: Double = 0.0,
    val sellOrderPrice: Double = 0.0,
)
