package com.skysoft.data.skyblock.price

import com.google.gson.Gson
import com.skysoft.SkysoftMod
import com.skysoft.config.SkysoftConfigGui
import com.skysoft.data.ProfileStorageApi
import com.skysoft.data.hypixel.HypixelLocationState
import com.skysoft.data.hypixel.SkyBlockProfileApi
import com.skysoft.utils.net.PendingHttpRequests
import com.skysoft.utils.SkysoftClientEvents
import com.skysoft.utils.SkysoftErrorBoundary
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicBoolean

object SkyBlockPriceData {
    private const val BAZAAR_URL = "https://api.findthesoft.com/bazaar"
    private const val BAZAAR_DEPTH_URL = "https://api.findthesoft.com/bazaar-depth"
    private const val LOWEST_BINS_URL = "https://api.findthesoft.com/lowest-bins"
    private const val AUCTION_HOUSE_URL = "https://api.findthesoft.com/auction-house"
    private const val BAZAAR_REFRESH_INTERVAL_TICKS = 20 * 20
    private const val LOWEST_BINS_REFRESH_INTERVAL_TICKS = 20 * 60 * 2

    private val gson = Gson()
    private val requests = PendingHttpRequests()
    private val fetchingBazaar = AtomicBoolean(false)
    private val fetchingBazaarDepth = AtomicBoolean(false)
    private val fetchingLowestBins = AtomicBoolean(false)

    @Volatile
    private var bazaar = BazaarProducts()

    @Volatile
    var bazaarStatus = BazaarDataStatus(BazaarDataLoadState.NOT_LOADED)
        private set

    private val hasItemListMarketInterest = AtomicBoolean(false)

    @Volatile
    private var lowestBins: Map<String, Long> = emptyMap()

    @Volatile
    var lowestBinsStatus = BazaarDataStatus(BazaarDataLoadState.NOT_LOADED)
        private set

    private var ticksUntilBazaarRefresh = 0
    private var ticksUntilLowestBinsRefresh = 0
    private var wasDemanded = false

    fun register() {
        ProfileStorageApi.registerConsumer("SkyBlock Price Data") {
            SkysoftConfigGui.config().inventory.bazaar.enabled
        }
        SkysoftClientEvents.onEndTick(
            "SkyBlock Price refresh",
            isActive = { hasDemand || wasDemanded },
        ) {
            if (!hasDemand) {
                if (wasDemanded) stopBackgroundWork()
                wasDemanded = false
                return@onEndTick
            }
            wasDemanded = true
            if (shouldRefreshBazaar()) {
                if (ticksUntilBazaarRefresh-- <= 0) {
                    ticksUntilBazaarRefresh = BAZAAR_REFRESH_INTERVAL_TICKS
                    refreshBazaar()
                }
            } else {
                ticksUntilBazaarRefresh = 0
            }
            if (shouldRefreshLowestBins()) {
                if (ticksUntilLowestBinsRefresh-- <= 0) {
                    ticksUntilLowestBinsRefresh = LOWEST_BINS_REFRESH_INTERVAL_TICKS
                    refreshLowestBins()
                }
            } else {
                ticksUntilLowestBinsRefresh = 0
            }
        }
        SkysoftClientEvents.onClientStopping("SkyBlock Price request cancellation") {
            requests.cancelAll()
        }
    }

    fun getBazaarPrice(itemId: String): BazaarPriceData? = bazaar.products[itemId]?.let {
        BazaarPriceData(
            instantBuyPrice = it.instantBuyPrice,
            instantSellPrice = it.instantSellPrice,
            buyOrderPrice = it.buyOrderPrice,
            sellOrderPrice = it.sellOrderPrice,
        )
    }

    fun getBazaarProduct(itemId: String): SkysoftBazaarProduct? = bazaar.products[itemId]

    fun getBazaarUpdatedAtMillis(): Long = bazaar.updatedAtMillis

    fun bazaarAvailability(itemId: String): BazaarProductAvailability = bazaarProductAvailability(
        bazaarStatus.state,
        bazaar.products.keys,
        itemId,
    )

    fun setItemListMarketInterest(isActive: Boolean) {
        hasItemListMarketInterest.set(isActive)
    }

    fun getLowestBin(itemId: String): Long? = lowestBins[itemId]

    fun lowestBinAvailability(itemId: String): BazaarProductAvailability = bazaarProductAvailability(
        lowestBinsStatus.state,
        lowestBins.keys,
        itemId,
    )

    fun refreshAuctionHouse(itemId: String, page: Int): CompletableFuture<SkysoftAuctionHouseResponse> {
        val item = URLEncoder.encode(itemId, StandardCharsets.UTF_8)
        return request("$AUCTION_HOUSE_URL?item=$item&page=${page.coerceAtLeast(0)}")
            .thenApply { gson.fromJson(it, SkysoftAuctionHouseResponse::class.java) }
            .thenApply { response ->
                if (!response.success) {
                    throw IllegalStateException("Skysoft Auction House response failed: ${response.cause}")
                }
                response
            }
    }

    fun refreshBazaarNow() {
        ticksUntilBazaarRefresh = BAZAAR_REFRESH_INTERVAL_TICKS
        refreshBazaar()
    }

    fun refreshItemListMarketNow() {
        ticksUntilBazaarRefresh = BAZAAR_REFRESH_INTERVAL_TICKS
        ticksUntilLowestBinsRefresh = LOWEST_BINS_REFRESH_INTERVAL_TICKS
        refreshBazaar()
        refreshLowestBins()
    }

    fun refreshBazaarDepth(
        productIds: Collection<String>,
        sinceMillis: Long,
    ): CompletableFuture<Map<String, SkysoftBazaarDepthProduct>>? {
        val ids = productIds
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .take(BAZAAR_DEPTH_PRODUCT_LIMIT)
        if (ids.isEmpty() || !fetchingBazaarDepth.compareAndSet(false, true)) return null

        val products = ids.joinToString(",") { URLEncoder.encode(it, StandardCharsets.UTF_8) }
        return request("$BAZAAR_DEPTH_URL?products=$products&since=${sinceMillis.coerceAtLeast(0L)}")
            .thenApply { gson.fromJson(it, SkysoftBazaarDepthResponse::class.java) }
            .thenApply { response ->
                if (!response.success) {
                    throw IllegalStateException("Skysoft bazaar depth response failed: ${response.cause}")
                }
                response.products
            }
            .whenComplete { _, error ->
                SkysoftErrorBoundary.run("Bazaar depth async completion") {
                    try {
                        if (error != null) SkysoftMod.LOGGER.warn("Failed to refresh bazaar depth", error)
                    } finally {
                        fetchingBazaarDepth.set(false)
                    }
                }
            }
    }

    private fun refreshBazaar() {
        if (!fetchingBazaar.compareAndSet(false, true)) return
        if (bazaar.products.isEmpty()) bazaarStatus = BazaarDataStatus(BazaarDataLoadState.LOADING)

        request(BAZAAR_URL)
            .thenApply { gson.fromJson(it, SkysoftBazaarResponse::class.java) }
            .thenApply { response ->
                if (!response.success) {
                    throw IllegalStateException("Skysoft bazaar response failed: ${response.cause}")
                }
                response
            }
            .whenComplete { response, error ->
                SkysoftErrorBoundary.run("Bazaar price async completion") {
                    try {
                        if (error == null && response != null) {
                            bazaar = BazaarProducts(response.products, response.updatedAtMillis())
                            bazaarStatus = BazaarDataStatus(BazaarDataLoadState.READY, response.updatedAtMillis())
                        } else {
                            SkysoftMod.LOGGER.warn("Failed to refresh bazaar prices", error)
                            bazaarStatus = if (bazaar.products.isEmpty()) {
                                BazaarDataStatus(
                                    BazaarDataLoadState.FAILED,
                                    message = error?.message ?: "Bazaar request failed",
                                )
                            } else {
                                BazaarDataStatus(
                                    BazaarDataLoadState.READY,
                                    bazaar.updatedAtMillis,
                                    error?.message ?: "Bazaar refresh failed",
                                )
                            }
                        }
                    } finally {
                        fetchingBazaar.set(false)
                    }
                }
            }
    }

    private fun refreshLowestBins() {
        if (!fetchingLowestBins.compareAndSet(false, true)) return
        if (lowestBins.isEmpty()) lowestBinsStatus = BazaarDataStatus(BazaarDataLoadState.LOADING)

        request(LOWEST_BINS_URL)
            .thenApply { gson.fromJson(it, LowestBinsResponse::class.java) }
            .thenApply { response ->
                if (!response.success) {
                    throw IllegalStateException("Skysoft lowest BIN response failed: ${response.cause}")
                }
                response
            }
            .whenComplete { response, error ->
                SkysoftErrorBoundary.run("Lowest BIN async completion") {
                    try {
                        if (error == null && response != null) {
                            lowestBins = response.prices
                            lowestBinsStatus = BazaarDataStatus(BazaarDataLoadState.READY, response.fetchedAt)
                        } else {
                            SkysoftMod.LOGGER.warn("Failed to refresh lowest BIN prices", error)
                            lowestBinsStatus = if (lowestBins.isEmpty()) {
                                BazaarDataStatus(
                                    BazaarDataLoadState.FAILED,
                                    message = error?.message ?: "Lowest BIN request failed",
                                )
                            } else {
                                BazaarDataStatus(
                                    BazaarDataLoadState.READY,
                                    lowestBinsStatus.updatedAtMillis,
                                    error?.message ?: "Lowest BIN refresh failed",
                                )
                            }
                        }
                    } finally {
                        fetchingLowestBins.set(false)
                    }
                }
            }
    }

    private fun request(url: String) = requests.getString(url)

    private fun shouldRefreshBazaar(): Boolean {
        val inventoryConfig = SkysoftConfigGui.config().inventory
        return shouldRefreshBazaarData(
            isInSkyBlock = HypixelLocationState.inSkyBlock,
            hasItemListInterest = hasItemListMarketInterest.get(),
            arePriceTooltipsEnabled = inventoryConfig.priceTooltips.enabled,
            isRareLootSharingEnabled = isRareLootSharingEnabled(),
            isBazaarTrackerEnabled = inventoryConfig.bazaar.enabled,
            hasActiveOrders = currentBazaarTrackerHasActiveOrders(),
        )
    }

    private val hasDemand: Boolean
        get() {
            val config = SkysoftConfigGui.config()
            return hasItemListMarketInterest.get() ||
                config.inventory.priceTooltips.enabled ||
                config.misc.rareLootSharing.enabled ||
                config.inventory.bazaar.enabled
        }

    private fun stopBackgroundWork() {
        requests.cancelAll()
        fetchingBazaar.set(false)
        fetchingBazaarDepth.set(false)
        fetchingLowestBins.set(false)
        ticksUntilBazaarRefresh = 0
        ticksUntilLowestBinsRefresh = 0
    }

    private fun shouldRefreshLowestBins(): Boolean = shouldRefreshLowestBinData(
        isInSkyBlock = HypixelLocationState.inSkyBlock,
        hasItemListInterest = hasItemListMarketInterest.get(),
        arePriceTooltipsEnabled = SkysoftConfigGui.config().inventory.priceTooltips.enabled,
        isRareLootSharingEnabled = isRareLootSharingEnabled(),
    )

    private fun isRareLootSharingEnabled(): Boolean =
        SkysoftConfigGui.config().misc.rareLootSharing.enabled

    private fun currentBazaarTrackerHasActiveOrders(): Boolean {
        if (SkyBlockProfileApi.currentProfileId == null) return false
        return ProfileStorageApi.storage.bazaarTracker.activeOrders.isNotEmpty()
    }

    private const val BAZAAR_DEPTH_PRODUCT_LIMIT = 50
}

enum class BazaarDataLoadState {
    NOT_LOADED,
    LOADING,
    READY,
    FAILED,
}

enum class BazaarProductAvailability {
    AVAILABLE,
    UNAVAILABLE,
    UNKNOWN,
}

data class BazaarDataStatus(
    val state: BazaarDataLoadState,
    val updatedAtMillis: Long = 0L,
    val message: String? = null,
)

internal fun bazaarProductAvailability(
    state: BazaarDataLoadState,
    productIds: Set<String>,
    itemId: String,
): BazaarProductAvailability = when (state) {
    BazaarDataLoadState.READY -> if (itemId in productIds) {
        BazaarProductAvailability.AVAILABLE
    } else {
        BazaarProductAvailability.UNAVAILABLE
    }
    BazaarDataLoadState.NOT_LOADED,
    BazaarDataLoadState.LOADING,
    BazaarDataLoadState.FAILED,
    -> BazaarProductAvailability.UNKNOWN
}

internal fun shouldRefreshBazaarData(
    isInSkyBlock: Boolean,
    hasItemListInterest: Boolean,
    arePriceTooltipsEnabled: Boolean,
    isRareLootSharingEnabled: Boolean,
    isBazaarTrackerEnabled: Boolean,
    hasActiveOrders: Boolean,
): Boolean = isInSkyBlock && (
    hasItemListInterest ||
        arePriceTooltipsEnabled ||
        isRareLootSharingEnabled ||
        isBazaarTrackerEnabled && hasActiveOrders
    )

internal fun shouldRefreshLowestBinData(
    isInSkyBlock: Boolean,
    hasItemListInterest: Boolean,
    arePriceTooltipsEnabled: Boolean,
    isRareLootSharingEnabled: Boolean,
): Boolean = isInSkyBlock && (hasItemListInterest || arePriceTooltipsEnabled || isRareLootSharingEnabled)

private data class BazaarProducts(
    val products: Map<String, SkysoftBazaarProduct> = emptyMap(),
    val updatedAtMillis: Long = 0L,
)

private fun SkysoftBazaarResponse.updatedAtMillis(): Long = lastUpdated?.takeIf { it > 0L } ?: 0L
