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
    private val snapshotLock = Any()
    private val hasItemListMarketInterest = AtomicBoolean(false)

    @Volatile
    private var snapshot = MarketPriceSnapshot()

    val bazaarStatus: BazaarDataStatus
        get() = snapshot.bazaarStatus

    val lowestBinsStatus: BazaarDataStatus
        get() = snapshot.lowestBinsStatus

    val npcSellPricesStatus: BazaarDataStatus
        get() = snapshot.npcSellPricesStatus

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

    fun getBazaarPrice(itemId: String): BazaarPriceData? = snapshot.bazaar.products[bazaarProductId(itemId)]?.let {
        BazaarPriceData(
            instantBuyPrice = it.instantBuyPrice,
            instantSellPrice = it.instantSellPrice,
            buyOrderPrice = it.buyOrderPrice,
            sellOrderPrice = it.sellOrderPrice,
        )
    }

    fun getBazaarProduct(itemId: String): SkysoftBazaarProduct? = snapshot.bazaar.products[bazaarProductId(itemId)]

    fun getBazaarUpdatedAtMillis(): Long = snapshot.bazaar.updatedAtMillis

    fun bazaarAvailability(itemId: String): BazaarProductAvailability = with(snapshot) {
        bazaarProductAvailability(bazaarStatus.state, bazaar.products.keys, bazaarProductId(itemId))
    }

    fun setItemListMarketInterest(isActive: Boolean) {
        hasItemListMarketInterest.set(isActive)
    }

    fun getLowestBin(itemId: String): Long? = snapshot.lowestBins[itemId]

    fun getNpcSellPrices(itemId: String): SkyBlockNpcSellPrices = with(snapshot) {
        SkyBlockNpcSellPrices(georgePetSellPrices[itemId] ?: npcSellPrices[itemId], motesSellPrices[itemId])
    }

    internal fun marketSnapshotForRawCraft(): RawCraftMarketSnapshot? = with(snapshot) {
        if (bazaarStatus.state != BazaarDataLoadState.READY) return null
        if (lowestBinsStatus.state != BazaarDataLoadState.READY) return null
        rawCraftMarket
    }

    fun lowestBinAvailability(itemId: String): BazaarProductAvailability = with(snapshot) {
        bazaarProductAvailability(lowestBinsStatus.state, lowestBins.keys, itemId)
    }

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
        updateSnapshot { current ->
            if (current.bazaar.products.isEmpty()) {
                current.copy(bazaarStatus = BazaarDataStatus(BazaarDataLoadState.LOADING))
            } else current
        }
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
                    updateSnapshot { current ->
                        current.copy(
                            bazaar = BazaarProducts(response.products, response.updatedAtMillis()),
                            bazaarStatus = BazaarDataStatus(BazaarDataLoadState.READY, response.updatedAtMillis()),
                        ).withUpdatedRawCraftMarket()
                    }
                } else if (error?.isCancellationFailure() != true) {
                    SkysoftMod.LOGGER.warn("Failed to refresh bazaar prices", error)
                    updateSnapshot { current ->
                        current.copy(
                            bazaarStatus = priceRefreshFailureStatus(
                                source = "Bazaar",
                                hasPrices = current.bazaar.products.isNotEmpty(),
                                updatedAtMillis = current.bazaar.updatedAtMillis,
                                error = error,
                            ),
                        )
                    }
                }
            }
        }
    }

    private fun refreshLowestBins() {
        if (lowestBinRequest.isPending) return
        updateSnapshot { current ->
            if (current.lowestBins.isEmpty()) {
                current.copy(lowestBinsStatus = BazaarDataStatus(BazaarDataLoadState.LOADING))
            } else current
        }
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
                    updateSnapshot { current ->
                        current.copy(
                            lowestBins = response.prices,
                            lowestBinsStatus = BazaarDataStatus(BazaarDataLoadState.READY, response.fetchedAt),
                        ).withUpdatedRawCraftMarket()
                    }
                } else if (error?.isCancellationFailure() != true) {
                    SkysoftMod.LOGGER.warn("Failed to refresh lowest BIN prices", error)
                    updateSnapshot { current ->
                        current.copy(
                            lowestBinsStatus = priceRefreshFailureStatus(
                                source = "Lowest BIN",
                                hasPrices = current.lowestBins.isNotEmpty(),
                                updatedAtMillis = current.lowestBinsStatus.updatedAtMillis,
                                error = error,
                            ),
                        )
                    }
                }
            }
        }
    }

    private fun refreshNpcSellPrices() {
        if (npcSellPriceRequest.isPending) return
        npcSellPriceRequestSchedule.recordAttempt(System.nanoTime())
        updateSnapshot { current ->
            if (current.npcSellPrices.isEmpty()) {
                current.copy(npcSellPricesStatus = BazaarDataStatus(BazaarDataLoadState.LOADING))
            } else current
        }
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
                    val coins = npcSellPrices(response)
                    val motes = motesSellPrices(response)
                    updateSnapshot { current ->
                        current.copy(
                            npcSellPrices = coins,
                            motesSellPrices = motes,
                            npcSellPricesStatus = BazaarDataStatus(BazaarDataLoadState.READY, response.lastUpdated),
                        )
                    }
                    npcSellPriceRequestSchedule.recordSuccess(System.nanoTime())
                } else if (error?.isCancellationFailure() != true) {
                    npcSellPriceRequestSchedule.recordFailure(System.nanoTime())
                    SkysoftMod.LOGGER.warn("Failed to refresh NPC sell prices", error)
                    updateSnapshot { current ->
                        current.copy(
                            npcSellPricesStatus = priceRefreshFailureStatus(
                                source = "NPC sell price",
                                hasPrices = current.npcSellPrices.isNotEmpty(),
                                updatedAtMillis = current.npcSellPricesStatus.updatedAtMillis,
                                error = error,
                            ),
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
        bazaarConsumers.register("Crafting Helper") { SkysoftConfigGui.config().inventory.craftingHelper.enabled }
        lowestBinConsumers.register("Item List") { hasItemListMarketInterest.get() }
        lowestBinConsumers.register("Price Tooltips") { arePriceTooltipLinesActive { it.needsLowestBinData } }
        lowestBinConsumers.register("Rare Loot Features", ::isRareLootPricingActive)
        lowestBinConsumers.register("Profit Tracker") { SkysoftConfigGui.config().profitTrackers.isAnyEnabled() }
        lowestBinConsumers.register("Crafting Helper") { SkysoftConfigGui.config().inventory.craftingHelper.enabled }
        npcSellPriceConsumers.register("Item List") { hasItemListMarketInterest.get() }
        npcSellPriceConsumers.register("Price Tooltips") {
            arePriceTooltipLinesActive { it == PriceTooltipLine.NPC_SELL_PRICE }
        }
        npcSellPriceConsumers.register("Profit Tracker") { SkysoftConfigGui.config().profitTrackers.isAnyEnabled() }
    }

    private fun updateSnapshot(update: (MarketPriceSnapshot) -> MarketPriceSnapshot) {
        synchronized(snapshotLock) {
            snapshot = update(snapshot)
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

private fun priceRefreshFailureStatus(
    source: String,
    hasPrices: Boolean,
    updatedAtMillis: Long,
    error: Throwable?,
): BazaarDataStatus = if (hasPrices) {
    BazaarDataStatus(
        BazaarDataLoadState.READY,
        updatedAtMillis,
        error?.message ?: "$source refresh failed",
    )
} else {
    BazaarDataStatus(
        BazaarDataLoadState.FAILED,
        message = error?.message ?: "$source request failed",
    )
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

private data class MarketPriceSnapshot(
    val bazaar: BazaarProducts = BazaarProducts(),
    val bazaarStatus: BazaarDataStatus = BazaarDataStatus(BazaarDataLoadState.NOT_LOADED),
    val lowestBins: Map<String, Long> = emptyMap(),
    val lowestBinsStatus: BazaarDataStatus = BazaarDataStatus(BazaarDataLoadState.NOT_LOADED),
    val npcSellPrices: Map<String, Double> = emptyMap(),
    val motesSellPrices: Map<String, Double> = emptyMap(),
    val npcSellPricesStatus: BazaarDataStatus = BazaarDataStatus(BazaarDataLoadState.NOT_LOADED),
    val rawCraftMarket: RawCraftMarketSnapshot = RawCraftMarketSnapshot(),
) {
    fun withUpdatedRawCraftMarket(): MarketPriceSnapshot = copy(
        rawCraftMarket = RawCraftMarketSnapshot(
            version = rawCraftMarket.version + 1,
            bazaarProducts = bazaarProductsWithAliases(bazaar.products, AttributeShardCatalog.bazaarProductAliases()),
            lowestBins = lowestBins,
        ),
    )
}

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
