package com.skysoft.data.skyblock.price

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.skysoft.SkysoftMod
import com.skysoft.config.SkysoftConfigGui
import com.skysoft.config.PriceTooltipLine
import com.skysoft.data.ProfileStorageApi
import com.skysoft.data.hypixel.HypixelLocationState
import com.skysoft.data.hypixel.SkyBlockProfileApi
import com.skysoft.data.skyblock.AttributeShardCatalog
import com.skysoft.utils.net.AsyncRequestSlot
import com.skysoft.utils.net.PendingHttpRequests
import com.skysoft.utils.net.isCancellationFailure
import com.skysoft.utils.ActiveConsumerRegistry
import com.skysoft.utils.SkysoftClientEvents
import com.skysoft.utils.SkysoftErrorBoundary
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

private const val NPC_SELL_PRICES_REFRESH_INTERVAL_NANOS = 24L * 60L * 60L * 1_000_000_000L
private const val NPC_SELL_PRICES_FAILURE_RETRY_NANOS = 60L * 1_000_000_000L

object SkyBlockPriceData {
    private const val BAZAAR_URL = "https://api.findthesoft.com/bazaar"
    private const val BAZAAR_DEPTH_URL = "https://api.findthesoft.com/bazaar-depth"
    private const val LOWEST_BINS_URL = "https://api.findthesoft.com/lowest-bins"
    private const val AUCTION_HOUSE_URL = "https://api.findthesoft.com/auction-house"
    private const val NPC_SELL_PRICES_URL = "https://api.hypixel.net/v2/resources/skyblock/items"
    private const val GEORGE_PET_PRICES_RESOURCE = "/assets/skysoft/data/george_pet_prices.json"
    private const val BAZAAR_REFRESH_INTERVAL_TICKS = 20 * 20
    private const val LOWEST_BINS_REFRESH_INTERVAL_TICKS = 20 * 60 * 2

    private val gson = Gson()
    private val georgePetSellPrices = requireNotNull(javaClass.getResourceAsStream(GEORGE_PET_PRICES_RESOURCE)) {
        "Missing bundled George pet prices"
    }.bufferedReader().use { reader ->
        gson.fromJson<Map<String, Double>>(reader, georgePetPriceMapType)
    }
    private val directRequests = PendingHttpRequests()
    private val bazaarRequests = PendingHttpRequests()
    private val lowestBinRequests = PendingHttpRequests()
    private val npcSellPriceRequests = PendingHttpRequests()
    private val bazaarRequest = AsyncRequestSlot<SkysoftBazaarResponse>()
    private val bazaarDepthRequest = AsyncRequestSlot<Map<String, SkysoftBazaarDepthProduct>>()
    private val lowestBinRequest = AsyncRequestSlot<LowestBinsResponse>()
    private val npcSellPriceRequest = AsyncRequestSlot<HypixelSkyBlockItemsResponse>()
    private val bazaarConsumers = ActiveConsumerRegistry()
    private val lowestBinConsumers = ActiveConsumerRegistry()
    private val npcSellPriceConsumers = ActiveConsumerRegistry()
    private val marketSnapshotVersion = AtomicLong()
    private val rawCraftMarketSnapshotVersion = AtomicLong()
    private val rawCraftMarketSnapshotLock = Any()

    @Volatile
    private var rawCraftMarketSnapshot = RawCraftMarketSnapshot()

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

    @Volatile
    private var npcSellPrices: Map<String, Double> = emptyMap()

    @Volatile
    private var motesSellPrices: Map<String, Double> = emptyMap()

    @Volatile
    var npcSellPricesStatus = BazaarDataStatus(BazaarDataLoadState.NOT_LOADED)
        private set

    val snapshotVersion: Long
        get() = marketSnapshotVersion.get()

    private var ticksUntilBazaarRefresh = 0
    private var ticksUntilLowestBinsRefresh = 0
    private val npcSellPriceRequestSchedule = NpcSellPriceRequestSchedule()
    private var wasDemanded = false
    private var wasBazaarDemanded = false
    private var wasLowestBinDemanded = false
    private var wasNpcSellPriceDemanded = false

    fun register() {
        registerConsumers()
        AttributeShardCatalog.registerConsumer("SkyBlock Price Data", ::hasDemand)
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
            val needsBazaar = shouldRefreshPriceData(
                isInSkyBlock = HypixelLocationState.inSkyBlock,
                hasActiveConsumers = bazaarConsumers.hasActiveConsumers,
            )
            if (needsBazaar) {
                if (ticksUntilBazaarRefresh-- <= 0) {
                    ticksUntilBazaarRefresh = BAZAAR_REFRESH_INTERVAL_TICKS
                    refreshBazaar()
                }
            } else {
                if (wasBazaarDemanded) cancelPriceSourceRequests(bazaarRequests, bazaarRequest)
                ticksUntilBazaarRefresh = 0
            }
            wasBazaarDemanded = needsBazaar
            val needsLowestBins = shouldRefreshPriceData(
                isInSkyBlock = HypixelLocationState.inSkyBlock,
                hasActiveConsumers = lowestBinConsumers.hasActiveConsumers,
            )
            if (needsLowestBins) {
                if (ticksUntilLowestBinsRefresh-- <= 0) {
                    ticksUntilLowestBinsRefresh = LOWEST_BINS_REFRESH_INTERVAL_TICKS
                    refreshLowestBins()
                }
            } else {
                if (wasLowestBinDemanded) cancelPriceSourceRequests(lowestBinRequests, lowestBinRequest)
                ticksUntilLowestBinsRefresh = 0
            }
            wasLowestBinDemanded = needsLowestBins
            val needsNpcSellPrices = shouldRefreshPriceData(
                isInSkyBlock = HypixelLocationState.inSkyBlock,
                hasActiveConsumers = npcSellPriceConsumers.hasActiveConsumers,
            )
            if (needsNpcSellPrices) {
                if (npcSellPriceRequestSchedule.shouldRequest(System.nanoTime())) refreshNpcSellPrices()
            } else if (
                wasNpcSellPriceDemanded &&
                cancelPriceSourceRequests(npcSellPriceRequests, npcSellPriceRequest)
            ) {
                npcSellPriceRequestSchedule.recordCancellation()
            }
            wasNpcSellPriceDemanded = needsNpcSellPrices
        }
        SkysoftClientEvents.onClientStopping("SkyBlock Price request cancellation") {
            bazaarRequest.cancel()
            bazaarDepthRequest.cancel()
            lowestBinRequest.cancel()
            npcSellPriceRequest.cancel()
            directRequests.cancelAll()
            bazaarRequests.cancelAll()
            lowestBinRequests.cancelAll()
            npcSellPriceRequests.cancelAll()
        }
    }

    fun getBazaarPrice(itemId: String): BazaarPriceData? = bazaar.products[bazaarProductId(itemId)]?.let {
        BazaarPriceData(
            instantBuyPrice = it.instantBuyPrice,
            instantSellPrice = it.instantSellPrice,
            buyOrderPrice = it.buyOrderPrice,
            sellOrderPrice = it.sellOrderPrice,
        )
    }

    fun getBazaarProduct(itemId: String): SkysoftBazaarProduct? = bazaar.products[bazaarProductId(itemId)]

    fun getBazaarUpdatedAtMillis(): Long = bazaar.updatedAtMillis

    fun bazaarAvailability(itemId: String): BazaarProductAvailability = bazaarProductAvailability(
        bazaarStatus.state,
        bazaar.products.keys,
        bazaarProductId(itemId),
    )

    fun setItemListMarketInterest(isActive: Boolean) {
        hasItemListMarketInterest.set(isActive)
    }

    fun getLowestBin(itemId: String): Long? = lowestBins[itemId]

    fun getNpcSellPrices(itemId: String): SkyBlockNpcSellPrices =
        SkyBlockNpcSellPrices(georgePetSellPrices[itemId] ?: npcSellPrices[itemId], motesSellPrices[itemId])

    internal fun marketSnapshotForRawCraft(): RawCraftMarketSnapshot? {
        if (bazaarStatus.state != BazaarDataLoadState.READY) return null
        if (lowestBinsStatus.state != BazaarDataLoadState.READY) return null
        return rawCraftMarketSnapshot
    }

    fun lowestBinAvailability(itemId: String): BazaarProductAvailability = bazaarProductAvailability(
        lowestBinsStatus.state,
        lowestBins.keys,
        itemId,
    )

    fun refreshAuctionHouse(itemId: String, page: Int): CompletableFuture<SkysoftAuctionHouseResponse> {
        val item = URLEncoder.encode(itemId, StandardCharsets.UTF_8)
        return directRequests.getString("$AUCTION_HOUSE_URL?item=$item&page=${page.coerceAtLeast(0)}")
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
        refreshNpcSellPrices()
    }

    fun refreshBazaarDepth(
        productIds: Collection<String>,
        sinceMillis: Long,
    ): CompletableFuture<Map<String, SkysoftBazaarDepthProduct>>? {
        val requestedIds = productIds
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .take(BAZAAR_DEPTH_PRODUCT_LIMIT)
        if (requestedIds.isEmpty()) return null
        val requestedIdByProductId = requestedIds.associateBy(::bazaarProductId)
        val products = requestedIdByProductId.keys.joinToString(",") { URLEncoder.encode(it, StandardCharsets.UTF_8) }
        return bazaarDepthRequest.startIfIdleFuture(
            requestFactory = {
                directRequests.getString("$BAZAAR_DEPTH_URL?products=$products&since=${sinceMillis.coerceAtLeast(0L)}")
                    .thenApply { gson.fromJson(it, SkysoftBazaarDepthResponse::class.java) }
                    .thenApply { response ->
                        if (!response.success) {
                            throw IllegalStateException("Skysoft bazaar depth response failed: ${response.cause}")
                        }
                        response.products.mapKeys { (productId, _) -> requestedIdByProductId[productId] ?: productId }
                    }
            },
        ) { _, error ->
            SkysoftErrorBoundary.run("Bazaar depth async completion") {
                if (error != null && !error.isCancellationFailure()) {
                    SkysoftMod.LOGGER.warn("Failed to refresh bazaar depth", error)
                }
            }
        }
    }

    private fun refreshBazaar() {
        if (bazaarRequest.isPending) return
        if (bazaar.products.isEmpty()) bazaarStatus = BazaarDataStatus(BazaarDataLoadState.LOADING)
        bazaarRequest.startIfIdle(
            requestFactory = {
                bazaarRequests.getString(BAZAAR_URL)
                    .thenApply { gson.fromJson(it, SkysoftBazaarResponse::class.java) }
                    .thenApply { response ->
                        if (!response.success) {
                            throw IllegalStateException("Skysoft bazaar response failed: ${response.cause}")
                        }
                        response
                    }
            },
        ) { response, error ->
            SkysoftErrorBoundary.run("Bazaar price async completion") {
                if (error == null && response != null) {
                    bazaar = BazaarProducts(response.products, response.updatedAtMillis())
                    bazaarStatus = BazaarDataStatus(BazaarDataLoadState.READY, response.updatedAtMillis())
                    updateRawCraftMarketSnapshot()
                    marketSnapshotVersion.incrementAndGet()
                } else if (error?.isCancellationFailure() != true) {
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
            }
        }
    }

    private fun refreshLowestBins() {
        if (lowestBinRequest.isPending) return
        if (lowestBins.isEmpty()) lowestBinsStatus = BazaarDataStatus(BazaarDataLoadState.LOADING)
        lowestBinRequest.startIfIdle(
            requestFactory = {
                lowestBinRequests.getString(LOWEST_BINS_URL)
                    .thenApply { gson.fromJson(it, LowestBinsResponse::class.java) }
                    .thenApply { response ->
                        if (!response.success) {
                            throw IllegalStateException("Skysoft lowest BIN response failed: ${response.cause}")
                        }
                        response
                    }
            },
        ) { response, error ->
            SkysoftErrorBoundary.run("Lowest BIN async completion") {
                if (error == null && response != null) {
                    lowestBins = response.prices
                    lowestBinsStatus = BazaarDataStatus(BazaarDataLoadState.READY, response.fetchedAt)
                    updateRawCraftMarketSnapshot()
                    marketSnapshotVersion.incrementAndGet()
                } else if (error?.isCancellationFailure() != true) {
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
            }
        }
    }

    private fun refreshNpcSellPrices() {
        if (npcSellPriceRequest.isPending) return
        npcSellPriceRequestSchedule.recordAttempt(System.nanoTime())
        if (npcSellPrices.isEmpty()) npcSellPricesStatus = BazaarDataStatus(BazaarDataLoadState.LOADING)
        npcSellPriceRequest.startIfIdle(
            requestFactory = {
                npcSellPriceRequests.getString(NPC_SELL_PRICES_URL)
                    .thenApply { gson.fromJson(it, HypixelSkyBlockItemsResponse::class.java) }
                    .thenApply { response ->
                        if (!response.success) error("Hypixel SkyBlock items response failed")
                        response
                    }
            },
        ) { response, error ->
            SkysoftErrorBoundary.run("NPC sell price async completion") {
                if (error == null && response != null) {
                    npcSellPrices = npcSellPrices(response)
                    motesSellPrices = motesSellPrices(response)
                    npcSellPricesStatus = BazaarDataStatus(BazaarDataLoadState.READY, response.lastUpdated)
                    npcSellPriceRequestSchedule.recordSuccess(System.nanoTime())
                    marketSnapshotVersion.incrementAndGet()
                } else if (error?.isCancellationFailure() != true) {
                    npcSellPriceRequestSchedule.recordFailure(System.nanoTime())
                    SkysoftMod.LOGGER.warn("Failed to refresh NPC sell prices", error)
                    npcSellPricesStatus = if (npcSellPrices.isEmpty()) {
                        BazaarDataStatus(
                            BazaarDataLoadState.FAILED,
                            message = error?.message ?: "NPC sell price request failed",
                        )
                    } else {
                        BazaarDataStatus(
                            BazaarDataLoadState.READY,
                            npcSellPricesStatus.updatedAtMillis,
                            error?.message ?: "NPC sell price refresh failed",
                        )
                    }
                }
            }
        }
    }

    private val hasDemand: Boolean
        get() {
            return bazaarConsumers.hasActiveConsumers ||
                lowestBinConsumers.hasActiveConsumers ||
                npcSellPriceConsumers.hasActiveConsumers
        }

    private fun stopBackgroundWork() {
        bazaarDepthRequest.cancel()
        directRequests.cancelAll()
        cancelPriceSourceRequests(bazaarRequests, bazaarRequest)
        cancelPriceSourceRequests(lowestBinRequests, lowestBinRequest)
        val wasFetchingNpcSellPrices = cancelPriceSourceRequests(npcSellPriceRequests, npcSellPriceRequest)
        ticksUntilBazaarRefresh = 0
        ticksUntilLowestBinsRefresh = 0
        if (wasFetchingNpcSellPrices) npcSellPriceRequestSchedule.recordCancellation()
        wasBazaarDemanded = false
        wasLowestBinDemanded = false
        wasNpcSellPriceDemanded = false
    }

    private fun registerConsumers() {
        bazaarConsumers.register("Item List") { hasItemListMarketInterest.get() }
        bazaarConsumers.register("Price Tooltips") { arePriceTooltipLinesActive { it.needsBazaarData } }
        bazaarConsumers.register("Rare Loot Features", ::isRareLootPricingActive)
        bazaarConsumers.register("Bazaar Tracker") {
            SkysoftConfigGui.config().inventory.bazaar.enabled && hasCurrentBazaarTrackerOrders()
        }
        bazaarConsumers.register("Profit Tracker") { SkysoftConfigGui.config().profitTrackers.isAnyEnabled() }
        bazaarConsumers.register("Sack Display") { SkysoftConfigGui.config().inventory.sackDisplay.enabled }
        lowestBinConsumers.register("Item List") { hasItemListMarketInterest.get() }
        lowestBinConsumers.register("Price Tooltips") { arePriceTooltipLinesActive { it.needsLowestBinData } }
        lowestBinConsumers.register("Rare Loot Features", ::isRareLootPricingActive)
        lowestBinConsumers.register("Profit Tracker") { SkysoftConfigGui.config().profitTrackers.isAnyEnabled() }
        npcSellPriceConsumers.register("Item List") { hasItemListMarketInterest.get() }
        npcSellPriceConsumers.register("Price Tooltips") {
            arePriceTooltipLinesActive { it == PriceTooltipLine.NPC_SELL_PRICE }
        }
        npcSellPriceConsumers.register("Profit Tracker") { SkysoftConfigGui.config().profitTrackers.isAnyEnabled() }
    }

    private fun updateRawCraftMarketSnapshot() {
        synchronized(rawCraftMarketSnapshotLock) {
            rawCraftMarketSnapshot = RawCraftMarketSnapshot(
                version = rawCraftMarketSnapshotVersion.incrementAndGet(),
                bazaarProducts = bazaarProductsWithAliases(
                    bazaar.products,
                    AttributeShardCatalog.bazaarProductAliases(),
                ),
                lowestBins = lowestBins,
            )
        }
    }

    private const val BAZAAR_DEPTH_PRODUCT_LIMIT = 50
}

private val georgePetPriceMapType = object : TypeToken<Map<String, Double>>() {}.type

private fun bazaarProductId(itemId: String): String =
    AttributeShardCatalog.bazaarProductAliases()[itemId] ?: itemId

internal fun bazaarProductsWithAliases(
    products: Map<String, SkysoftBazaarProduct>,
    aliases: Map<String, String>,
): Map<String, SkysoftBazaarProduct> = buildMap {
    putAll(products)
    aliases.forEach { (itemId, productId) -> products[productId]?.let { product -> put(itemId, product) } }
}

private fun arePriceTooltipLinesActive(predicate: (PriceTooltipLine) -> Boolean): Boolean {
    val config = SkysoftConfigGui.config().inventory.priceTooltips
    return config.enabled && config.settings.priceLines.get().any(predicate)
}

private fun isRareLootPricingActive(): Boolean =
    SkysoftConfigGui.config().misc.isAnyRareLootFeatureEnabled()

private fun hasCurrentBazaarTrackerOrders(): Boolean {
    if (SkyBlockProfileApi.currentProfileId == null) return false
    return ProfileStorageApi.storage.bazaarTracker.activeOrders.isNotEmpty()
}

private fun cancelPriceSourceRequests(
    requests: PendingHttpRequests,
    requestSlot: AsyncRequestSlot<*>,
): Boolean {
    val wasFetching = requestSlot.isPending
    requestSlot.cancel()
    requests.cancelAll()
    return wasFetching
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

private fun shouldRefreshPriceData(
    isInSkyBlock: Boolean,
    hasActiveConsumers: Boolean,
): Boolean = isInSkyBlock && hasActiveConsumers

internal class NpcSellPriceRequestSchedule(
    private val successIntervalNanos: Long = NPC_SELL_PRICES_REFRESH_INTERVAL_NANOS,
    private val failureRetryNanos: Long = NPC_SELL_PRICES_FAILURE_RETRY_NANOS,
) {
    @Volatile
    private var nextRequestAtNanos: Long? = null

    fun shouldRequest(nowNanos: Long): Boolean =
        nextRequestAtNanos?.let { nextRequest -> nowNanos - nextRequest >= 0L } ?: true

    fun recordAttempt(nowNanos: Long) {
        nextRequestAtNanos = nowNanos + failureRetryNanos
    }

    fun recordSuccess(nowNanos: Long) {
        nextRequestAtNanos = nowNanos + successIntervalNanos
    }

    fun recordFailure(nowNanos: Long) {
        nextRequestAtNanos = nowNanos + failureRetryNanos
    }

    fun recordCancellation() {
        nextRequestAtNanos = null
    }
}

internal fun npcSellPrices(response: HypixelSkyBlockItemsResponse): Map<String, Double> =
    itemSellPrices(response, HypixelSkyBlockItem::npcSellPrice)

internal fun motesSellPrices(response: HypixelSkyBlockItemsResponse): Map<String, Double> =
    itemSellPrices(response, HypixelSkyBlockItem::motesSellPrice)

private fun itemSellPrices(
    response: HypixelSkyBlockItemsResponse,
    priceFor: (HypixelSkyBlockItem) -> Double?,
): Map<String, Double> = response.items.mapNotNull { item ->
    val price = priceFor(item)
    if (item.id.isBlank() || price == null || !price.isFinite() || price <= 0.0) null else item.id to price
}.toMap()

private data class BazaarProducts(
    val products: Map<String, SkysoftBazaarProduct> = emptyMap(),
    val updatedAtMillis: Long = 0L,
)

internal data class RawCraftMarketSnapshot(
    val version: Long = 0L,
    val bazaarProducts: Map<String, SkysoftBazaarProduct> = emptyMap(),
    val lowestBins: Map<String, Long> = emptyMap(),
)

private fun SkysoftBazaarResponse.updatedAtMillis(): Long = lastUpdated?.takeIf { it > 0L } ?: 0L
