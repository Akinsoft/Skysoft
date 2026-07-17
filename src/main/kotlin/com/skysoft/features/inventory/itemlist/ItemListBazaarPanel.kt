package com.skysoft.features.inventory.itemlist

import com.skysoft.data.ProfileStorage
import com.skysoft.data.hypixel.HypixelLocationState
import com.skysoft.data.hypixel.SkyBlockCookieBuffApi
import com.skysoft.data.skyblock.ItemListEntryKey
import com.skysoft.data.skyblock.SkyBlockDataRepository
import com.skysoft.data.skyblock.price.BazaarDataLoadState
import com.skysoft.data.skyblock.price.BazaarProductAvailability
import com.skysoft.data.skyblock.price.SkyBlockPriceData
import com.skysoft.data.skyblock.price.SkysoftBazaarDepthProduct
import com.skysoft.data.skyblock.price.SkysoftBazaarProduct
import com.skysoft.features.bazaar.BazaarInvestmentPosition
import com.skysoft.features.bazaar.BazaarTracker
import com.skysoft.features.bazaar.bazaarRemoteAccess
import com.skysoft.gui.tooltip.SkysoftNativeTooltip
import com.skysoft.utils.MinecraftClient
import com.skysoft.utils.NumberUtilities.coinAmountFormat
import com.skysoft.utils.SkysoftErrorBoundary
import com.skysoft.utils.gui.PixelButtonRenderer
import com.skysoft.utils.gui.Rect
import com.skysoft.utils.render.LegacyTextRenderer
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor

internal class ItemListBazaarPanel {
    private var currentKey: ItemListEntryKey? = null
    private var depthState = BazaarDepthState.NOT_LOADED
    private var depthProduct: SkysoftBazaarDepthProduct? = null
    private var depthError: String? = null
    private var nextDepthRequestAt = 0L
    private var depthRequestInFlight = false
    private var playerMarketSnapshot = BazaarPlayerMarketSnapshot()
    private var nextPlayerMarketRefreshAt = 0L
    private val graph = ItemListBazaarGraph()

    fun render(
        context: GuiGraphicsExtractor,
        font: Font,
        bounds: Rect,
        key: ItemListEntryKey,
        mouseX: Int,
        mouseY: Int,
    ) {
        prepare(key)
        requestDepthWhenReady(key)
        val product = SkyBlockPriceData.getBazaarProduct(key.id)
        LegacyTextRenderer.draw(context, "§fBazaar", bounds.x + INSET, bounds.y + TITLE_Y)
        when {
            product != null -> renderProduct(context, font, bounds, key, product, mouseX, mouseY)
            SkyBlockPriceData.bazaarStatus.state == BazaarDataLoadState.FAILED -> renderLoadFailure(context, bounds)
            SkyBlockPriceData.bazaarAvailability(key.id) == BazaarProductAvailability.UNAVAILABLE -> {
                LegacyTextRenderer.draw(
                    context,
                    "§8This item is not traded on the Bazaar",
                    bounds.x + INSET,
                    bounds.y + STATUS_Y,
                )
            }
            else -> LegacyTextRenderer.draw(
                context,
                "§7Loading current Bazaar data...",
                bounds.x + INSET,
                bounds.y + STATUS_Y,
            )
        }
        renderOpenButton(context, font, bounds, key, mouseX, mouseY, product != null)
    }

    fun click(bounds: Rect, key: ItemListEntryKey, mouseX: Int, mouseY: Int): BazaarPanelClickResult {
        if (graph.processClick(bounds, mouseX, mouseY).isHandled) {
            return BazaarPanelClickResult.HANDLED
        }
        if (!openButton(bounds).contains(mouseX, mouseY)) return BazaarPanelClickResult.IGNORED
        return openBazaar(key)
    }

    private fun renderProduct(
        context: GuiGraphicsExtractor,
        font: Font,
        bounds: Rect,
        key: ItemListEntryKey,
        product: SkysoftBazaarProduct,
        mouseX: Int,
        mouseY: Int,
    ) {
        val values = listOf(
            "Instant Buy" to product.instantBuyPrice,
            "Instant Sell" to product.instantSellPrice,
            "Buy Order" to product.buyOrderPrice,
            "Sell Order" to product.sellOrderPrice,
        )
        val columnWidth = (bounds.width - INSET * 2) / PRICE_COLUMNS
        values.forEachIndexed { index, (label, value) ->
            val x = bounds.x + INSET + index % PRICE_COLUMNS * columnWidth
            val y = bounds.y + PRICE_TOP + index / PRICE_COLUMNS * PRICE_ROW_HEIGHT
            LegacyTextRenderer.draw(context, "§7$label", x, y)
            LegacyTextRenderer.draw(context, formatPrice(value), x, y + PRICE_VALUE_Y_OFFSET)
        }
        val playerMarket = playerMarketSnapshot(key)
        graph.render(
            context = context,
            font = font,
            bounds = bounds,
            product = depthProduct,
            investment = playerMarket.investment,
            transactions = playerMarket.transactions,
            exitPrice = product.instantSellPrice,
            updatedAtMillis = SkyBlockPriceData.getBazaarUpdatedAtMillis(),
            state = depthState,
            error = depthError,
            mouseX = mouseX,
            mouseY = mouseY,
        )
    }

    private fun renderLoadFailure(context: GuiGraphicsExtractor, bounds: Rect) {
        LegacyTextRenderer.draw(context, "§cBazaar data failed to load", bounds.x + INSET, bounds.y + STATUS_Y)
        SkyBlockPriceData.bazaarStatus.message?.let {
            LegacyTextRenderer.draw(context, "§8${it.take(ERROR_TEXT_LIMIT)}", bounds.x + INSET, bounds.y + ERROR_Y)
        }
    }

    private fun renderOpenButton(
        context: GuiGraphicsExtractor,
        font: Font,
        bounds: Rect,
        key: ItemListEntryKey,
        mouseX: Int,
        mouseY: Int,
        hasProduct: Boolean,
    ) {
        val button = openButton(bounds)
        val access = bazaarRemoteAccess(HypixelLocationState.inSkyBlock, SkyBlockCookieBuffApi.status)
        val isEnabled = hasProduct && access.canOpen
        PixelButtonRenderer.draw(context, font, button, "Open Bazaar", false, button.contains(mouseX, mouseY), isEnabled)
        if (button.contains(mouseX, mouseY) && !isEnabled) {
            val reason = when {
                !hasProduct && SkyBlockPriceData.bazaarStatus.state == BazaarDataLoadState.FAILED ->
                    "Bazaar data failed to load"
                !hasProduct -> "Bazaar data is still loading"
                else -> access.unavailableReason ?: "Bazaar is unavailable"
            }
            SkysoftNativeTooltip.setForNextFrame(context, listOf("§c$reason"), mouseX, mouseY)
        } else if (button.contains(mouseX, mouseY)) {
            val name = SkyBlockDataRepository.entry(key)?.displayName ?: key.id
            SkysoftNativeTooltip.setForNextFrame(context, listOf("§e/bz $name"), mouseX, mouseY)
        }
    }

    private fun openBazaar(key: ItemListEntryKey): BazaarPanelClickResult {
        if (SkyBlockPriceData.getBazaarProduct(key.id) == null) return BazaarPanelClickResult.IGNORED
        val access = bazaarRemoteAccess(HypixelLocationState.inSkyBlock, SkyBlockCookieBuffApi.status)
        if (!access.canOpen) return BazaarPanelClickResult.IGNORED
        val connection = Minecraft.getInstance().connection ?: return BazaarPanelClickResult.IGNORED
        val itemName = SkyBlockDataRepository.entry(key)?.displayName ?: key.id.replace('_', ' ')
        connection.sendCommand("bz $itemName")
        MinecraftClient.setScreen(null)
        return BazaarPanelClickResult.OPENED
    }

    private fun prepare(key: ItemListEntryKey) {
        if (currentKey == key) return
        currentKey = key
        depthState = BazaarDepthState.NOT_LOADED
        depthProduct = null
        depthError = null
        nextDepthRequestAt = 0L
        depthRequestInFlight = false
        graph.reset()
        playerMarketSnapshot = BazaarPlayerMarketSnapshot()
        nextPlayerMarketRefreshAt = 0L
        SkyBlockPriceData.refreshBazaarNow()
    }

    private fun playerMarketSnapshot(key: ItemListEntryKey): BazaarPlayerMarketSnapshot {
        val now = System.currentTimeMillis()
        if (now < nextPlayerMarketRefreshAt) return playerMarketSnapshot
        val itemName = SkyBlockDataRepository.entry(key)?.displayName ?: key.id
        playerMarketSnapshot = BazaarPlayerMarketSnapshot(
            investment = BazaarTracker.investmentPosition(key.id),
            transactions = BazaarTracker.transactionsFor(
                key.id,
                itemName,
                now - BazaarGraphWindow.TWENTY_FOUR_HOURS.durationMillis,
            ),
        )
        nextPlayerMarketRefreshAt = now + PLAYER_MARKET_REFRESH_MILLIS
        return playerMarketSnapshot
    }

    private fun requestDepthWhenReady(key: ItemListEntryKey) {
        if (SkyBlockPriceData.bazaarAvailability(key.id) != BazaarProductAvailability.AVAILABLE) return
        val now = System.currentTimeMillis()
        if (depthRequestInFlight || now < nextDepthRequestAt) return
        depthRequestInFlight = true
        if (depthProduct == null) depthState = BazaarDepthState.LOADING
        val future = SkyBlockPriceData.refreshBazaarDepth(
            listOf(key.id),
            now - BazaarGraphWindow.TWENTY_FOUR_HOURS.durationMillis,
        )
        if (future == null) {
            depthRequestInFlight = false
            depthState = if (depthProduct == null) BazaarDepthState.NOT_LOADED else BazaarDepthState.READY
            nextDepthRequestAt = now + DEPTH_BUSY_RETRY_MILLIS
            return
        }
        future.whenComplete { products, error ->
            SkysoftErrorBoundary.onClientThread("Item List Bazaar async completion") {
                if (currentKey != key) return@onClientThread
                depthRequestInFlight = false
                if (error == null) {
                    depthProduct = products?.get(key.id)
                    depthState = BazaarDepthState.READY
                    depthError = null
                    nextDepthRequestAt = System.currentTimeMillis() + DEPTH_REFRESH_MILLIS
                } else {
                    depthState = if (depthProduct == null) BazaarDepthState.FAILED else BazaarDepthState.READY
                    depthError = error.message ?: "Bazaar depth request failed"
                    nextDepthRequestAt = System.currentTimeMillis() + DEPTH_FAILURE_RETRY_MILLIS
                }
            }
        }
    }

    private fun openButton(bounds: Rect) = Rect(
        bounds.x + bounds.width - INSET - OPEN_WIDTH,
        bounds.y + bounds.height - CONTROL_HEIGHT,
        OPEN_WIDTH,
        CONTROL_HEIGHT,
    )

    private fun formatPrice(value: Double): String =
        if (value > 0.0) "§6${value.coinAmountFormat()} coins" else "§8No active orders"

    private companion object {
        const val INSET = 6
        const val TITLE_Y = 2
        const val STATUS_Y = 20
        const val ERROR_Y = 34
        const val PRICE_TOP = 16
        const val PRICE_COLUMNS = 2
        const val PRICE_ROW_HEIGHT = 25
        const val PRICE_VALUE_Y_OFFSET = 11
        const val CONTROL_HEIGHT = 18
        const val OPEN_WIDTH = 94
        const val DEPTH_BUSY_RETRY_MILLIS = 1_000L
        const val DEPTH_FAILURE_RETRY_MILLIS = 10_000L
        const val DEPTH_REFRESH_MILLIS = 20_000L
        const val PLAYER_MARKET_REFRESH_MILLIS = 1_000L
        const val ERROR_TEXT_LIMIT = 52
    }
}

private data class BazaarPlayerMarketSnapshot(
    val investment: BazaarInvestmentPosition? = null,
    val transactions: List<ProfileStorage.BazaarTransactionData> = emptyList(),
)

internal enum class BazaarPanelClickResult(val isHandled: Boolean) {
    IGNORED(false),
    HANDLED(true),
    OPENED(true),
}

internal enum class BazaarDepthState {
    NOT_LOADED,
    LOADING,
    READY,
    FAILED,
}
