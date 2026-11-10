package com.skysoft.data.skyblock.price

data class SkysoftAuctionHouseResponse(
    val success: Boolean = false,
    val cause: String? = null,
    val itemId: String = "",
    val page: Int = 0,
    val pageSize: Int = 36,
    val totalListings: Int = 0,
    val totalPages: Int = 0,
    val listings: List<SkysoftAuctionListing> = emptyList(),
)

data class SkysoftAuctionListing(
    val auctionId: String = "",
    val sellerUuid: String = "",
    val price: Long = 0,
    val end: Long = 0,
    val itemBytes: String = "",
)
