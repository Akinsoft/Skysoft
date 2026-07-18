package com.skysoft.features.inventory

import com.skysoft.config.PriceTooltipLine
import com.skysoft.config.SkysoftConfigGui
import com.skysoft.data.hypixel.HypixelLocationState
import com.skysoft.data.skyblock.ItemListEntryKey
import com.skysoft.data.skyblock.SkyBlockDataRepository
import com.skysoft.data.skyblock.SkyBlockRecipe
import com.skysoft.data.skyblock.SkyBlockRecipeSnapshot
import com.skysoft.data.skyblock.price.RawCraftMarketSnapshot
import com.skysoft.data.skyblock.price.SkyBlockPriceData
import com.skysoft.utils.SkysoftClientEvents
import com.skysoft.utils.SkysoftErrorBoundary
import java.util.concurrent.CancellationException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicBoolean

internal object PriceTooltipRawCraftCosts {
    private var executor: ExecutorService? = null
    private var activeBuild: ActiveRawCraftCostBuild? = null
    private var publishedCosts: PreparedRawCraftCosts? = null
    private var failedBuildKey: RawCraftCostBuildKey? = null
    private var wasActive = false
    private var ticksUntilRefresh = 0

    fun register() {
        SkysoftClientEvents.onEndTick(
            "Raw Craft Cost preparation",
            isActive = { isActive() || wasActive || activeBuild != null },
        ) {
            refreshPreparedCosts()
        }
        SkysoftClientEvents.onDisconnect("Raw Craft Cost disconnect reset") {
            stopBackgroundWork()
        }
        SkysoftClientEvents.onClientStopping("Raw Craft Cost shutdown") {
            stopBackgroundWork()
        }
    }

    fun cost(itemId: String): Double? = publishedCosts?.costs?.get(itemId)

    private fun refreshPreparedCosts() {
        if (!isActive()) {
            if (wasActive || activeBuild != null || publishedCosts != null) stopBackgroundWork()
            wasActive = false
            return
        }
        wasActive = true
        if (ticksUntilRefresh-- > 0) return
        ticksUntilRefresh = REFRESH_INTERVAL_TICKS
        val recipeSnapshot = SkyBlockDataRepository.pricingRecipes ?: return
        val marketSnapshot = SkyBlockPriceData.marketSnapshotForRawCraft() ?: return
        val buildKey = RawCraftCostBuildKey(recipeSnapshot.version, marketSnapshot.version)
        if (
            publishedCosts?.buildKey == buildKey ||
            activeBuild?.buildKey == buildKey ||
            failedBuildKey == buildKey
        ) return
        activeBuild?.let(::cancelBuild)
        scheduleBuild(recipeSnapshot, marketSnapshot, buildKey)
    }

    private fun scheduleBuild(
        recipeSnapshot: SkyBlockRecipeSnapshot,
        marketSnapshot: RawCraftMarketSnapshot,
        buildKey: RawCraftCostBuildKey,
    ) {
        val build = ActiveRawCraftCostBuild(buildKey)
        activeBuild = build
        build.future = backgroundExecutor().submit {
            try {
                val source = SnapshotRawCraftPriceSource(recipeSnapshot, marketSnapshot)
                val resolution = RawCraftCostResolver(source).resolveAll(build.cancelled::get)
                SkysoftErrorBoundary.onClientThread("Raw Craft Cost preparation completion") {
                    if (activeBuild !== build || !isActive()) return@onClientThread
                    activeBuild = null
                    failedBuildKey = null
                    publishedCosts = PreparedRawCraftCosts(buildKey, resolution.costs)
                }
            } catch (_: CancellationException) {
                return@submit
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            } catch (exception: Exception) {
                SkysoftErrorBoundary.onClientThread("Raw Craft Cost preparation failure") {
                    if (activeBuild !== build) return@onClientThread
                    activeBuild = null
                    failedBuildKey = buildKey
                    throw exception
                }
            }
        }
    }

    private fun cancelBuild(build: ActiveRawCraftCostBuild) {
        build.cancelled.set(true)
        build.future?.cancel(true)
        if (activeBuild === build) activeBuild = null
    }

    private fun stopBackgroundWork() {
        activeBuild?.let(::cancelBuild)
        executor?.shutdownNow()
        executor = null
        publishedCosts = null
        failedBuildKey = null
        ticksUntilRefresh = 0
        wasActive = false
    }

    private fun backgroundExecutor(): ExecutorService {
        executor?.let { return it }
        return Executors.newSingleThreadExecutor { action ->
            Thread(action, "Skysoft Raw Craft Costs").apply { isDaemon = true }
        }.also { executor = it }
    }

    private fun isActive(): Boolean {
        if (!HypixelLocationState.inSkyBlock) return false
        val config = SkysoftConfigGui.config().inventory.priceTooltips
        return config.enabled && PriceTooltipLine.RAW_CRAFT_COST in config.settings.priceLines.get()
    }

    private data class RawCraftCostBuildKey(
        val recipeVersion: Long,
        val marketVersion: Long,
    )

    private data class PreparedRawCraftCosts(
        val buildKey: RawCraftCostBuildKey,
        val costs: Map<String, Double>,
    )

    private class ActiveRawCraftCostBuild(
        val buildKey: RawCraftCostBuildKey,
        val cancelled: AtomicBoolean = AtomicBoolean(),
        var future: Future<*>? = null,
    )

    private class SnapshotRawCraftPriceSource(
        private val recipes: SkyBlockRecipeSnapshot,
        private val market: RawCraftMarketSnapshot,
    ) : RawCraftPriceSource {
        override val recipeVersion: Long = recipes.version
        override val marketVersion: Long = market.version
        override val recipeKeys: Set<ItemListEntryKey> = recipes.recipesByResult.keys

        override fun recipesFor(key: ItemListEntryKey): List<SkyBlockRecipe> = recipes.recipesByResult[key].orEmpty()

        override fun bazaarInstantBuy(itemId: String): Double? = market.bazaarProducts[itemId]?.instantBuyPrice

        override fun lowestBin(itemId: String): Double? = market.lowestBins[itemId]?.toDouble()
    }

    private const val REFRESH_INTERVAL_TICKS = 20
}
