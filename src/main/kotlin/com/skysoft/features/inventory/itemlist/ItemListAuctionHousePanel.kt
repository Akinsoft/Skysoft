package com.skysoft.features.inventory.itemlist

import com.skysoft.data.hypixel.HypixelLocationState
import com.skysoft.data.skyblock.ItemListEntryKey
import com.skysoft.data.skyblock.ItemListEntryKind
import com.skysoft.data.skyblock.SkyBlockItemId.skyBlockId
import com.skysoft.data.skyblock.price.BazaarProductAvailability
import com.skysoft.data.skyblock.price.SkyBlockPriceData
import com.skysoft.data.skyblock.price.SkysoftAuctionHouseResponse
import com.skysoft.data.skyblock.price.SkysoftAuctionListing
import com.skysoft.features.inventory.registryOps
import com.skysoft.gui.tooltip.SkysoftNativeTooltip
import com.skysoft.utils.MinecraftClient
import com.skysoft.utils.SkysoftErrorBoundary
import com.skysoft.utils.gui.Rect
import com.skysoft.utils.render.LegacyTextRenderer
import java.util.UUID
import java.util.concurrent.CompletableFuture
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.component.ItemLore
import net.minecraft.world.item.component.TooltipDisplay

internal class ItemListAuctionHousePanel {
    private var currentKey: ItemListEntryKey? = null
    private var state = AuctionHousePanelState.NOT_LOADED
    private var listings: List<AuctionListingView> = emptyList()
    private var page = 0
    private var pageCount = 0
    private var nextRequestAt = 0L
    private var requestInFlight = false
    private var requestToken = 0L
    private var lastError: String? = null
    private val sellerNames = AuctionSellerNames()

    val canGoPrevious: Boolean get() = page > 0
    val canGoNext: Boolean get() = page + 1 < pageCount
    val pageLabel: String get() = if (pageCount == 0) "0 / 0" else "${page + 1} / $pageCount"

    fun render(
        context: GuiGraphicsExtractor,
        font: Font,
        bounds: Rect,
        key: ItemListEntryKey,
        mouseX: Int,
        mouseY: Int,
    ) {
        prepare(key)
        requestWhenReady(key)
        when {
            listings.isNotEmpty() -> renderGrid(context, font, bounds, mouseX, mouseY)
            state == AuctionHousePanelState.LOADING -> renderStatus(context, bounds, "§7Loading lowest BIN auctions...")
            state == AuctionHousePanelState.FAILED -> renderStatus(context, bounds, "§cAuction House data failed to load")
            state == AuctionHousePanelState.READY -> renderStatus(context, bounds, "§8No active BIN listings")
            else -> renderStatus(context, bounds, "§7Loading Auction House data...")
        }
    }

    fun click(isVisible: Boolean, bounds: Rect, mouseX: Int, mouseY: Int): ViewerInputResult {
        if (!isVisible) return ViewerInputResult.IGNORED
        val listing = listingAt(bounds, mouseX, mouseY) ?: return ViewerInputResult.IGNORED
        val connection = Minecraft.getInstance().connection
        val command = auctionViewCommand(HypixelLocationState.inSkyBlock, connection != null, listing.data.auctionId)
        if (command == null) return ViewerInputResult.IGNORED
        connection?.sendCommand(command)
        MinecraftClient.setScreen(null)
        return ViewerInputResult.HANDLED
    }

    fun changePage(delta: Int): ViewerInputResult {
        val nextPage = (page + delta).coerceIn(0, (pageCount - 1).coerceAtLeast(0))
        if (nextPage == page) return ViewerInputResult.IGNORED
        page = nextPage
        listings = emptyList()
        state = AuctionHousePanelState.LOADING
        nextRequestAt = 0L
        requestInFlight = false
        requestToken++
        return ViewerInputResult.page(delta)
    }

    private fun prepare(key: ItemListEntryKey) {
        if (currentKey == key) return
        currentKey = key
        state = AuctionHousePanelState.NOT_LOADED
        listings = emptyList()
        page = 0
        pageCount = 0
        nextRequestAt = 0L
        requestInFlight = false
        requestToken++
        lastError = null
        sellerNames.clear()
    }

    private fun requestWhenReady(key: ItemListEntryKey) {
        val now = System.currentTimeMillis()
        if (requestInFlight || now < nextRequestAt) return
        requestInFlight = true
        if (listings.isEmpty()) state = AuctionHousePanelState.LOADING
        val requestedPage = page
        val token = ++requestToken
        val ops = registryOps()
        SkyBlockPriceData.refreshAuctionHouse(key.id, requestedPage)
            .thenApplyAsync { response -> decodedResponse(response, key, ops) }
            .whenComplete { response, error ->
                SkysoftErrorBoundary.onClientThread("Item List Auction House async completion") {
                    if (currentKey != key || token != requestToken) return@onClientThread
                    requestInFlight = false
                    if (error == null && response != null) {
                        applyResponse(response)
                        nextRequestAt = System.currentTimeMillis() + REFRESH_MILLIS
                    } else {
                        lastError = errorMessage(error)
                        state = if (listings.isEmpty()) AuctionHousePanelState.FAILED else AuctionHousePanelState.READY
                        nextRequestAt = System.currentTimeMillis() + FAILURE_RETRY_MILLIS
                    }
                }
            }
    }

    private fun applyResponse(response: DecodedAuctionResponse) {
        page = response.page
        pageCount = response.pageCount
        listings = response.listings
        listings.forEach { sellerNames.prefetch(it.data.sellerUuid) }
        state = AuctionHousePanelState.READY
        lastError = null
    }

    private fun renderGrid(
        context: GuiGraphicsExtractor,
        font: Font,
        bounds: Rect,
        mouseX: Int,
        mouseY: Int,
    ) {
        val grid = AuctionHouseGridLayout.create(bounds)
        grid.slots.forEach { slot ->
            context.fill(slot.x, slot.y, slot.x + slot.width, slot.y + slot.height, SLOT_BORDER)
            context.fill(slot.x + 1, slot.y + 1, slot.x + slot.width - 1, slot.y + slot.height - 1, SLOT_FILL)
        }
        grid.slots.zip(listings).forEach { (slot, listing) ->
            val stack = listing.stack ?: FAILED_STACK
            renderViewerItem(context, font, stack, slot, stack.count.takeIf { it > 1 }?.toString())
            if (!slot.contains(mouseX, mouseY)) return@forEach
            if (listing.stack == null) {
                SkysoftNativeTooltip.setForNextFrame(
                    context,
                    listOf("§cAuction item failed to decode", "§8${listing.decodeError.orEmpty().take(ERROR_LIMIT)}"),
                    mouseX,
                    mouseY,
                )
            } else {
                val tooltipStack = auctionTooltipStack(
                    listing.stack,
                    listing.data,
                    sellerNames.state(listing.data.sellerUuid),
                    System.currentTimeMillis(),
                )
                context.setTooltipForNextFrame(font, tooltipStack, mouseX, mouseY)
            }
        }
    }

    private fun listingAt(bounds: Rect, mouseX: Int, mouseY: Int): AuctionListingView? =
        AuctionHouseGridLayout.create(bounds).slots.withIndex()
            .firstOrNull { (_, slot) -> slot.contains(mouseX, mouseY) }
            ?.index
            ?.let(listings::getOrNull)

    private fun renderStatus(context: GuiGraphicsExtractor, bounds: Rect, text: String) {
        LegacyTextRenderer.draw(context, text, bounds.x + STATUS_X, bounds.y + STATUS_Y)
        lastError?.takeIf { state == AuctionHousePanelState.FAILED }?.let { error ->
            LegacyTextRenderer.draw(context, "§8${error.take(ERROR_LIMIT)}", bounds.x + STATUS_X, bounds.y + ERROR_Y)
        }
    }

    private companion object {
        const val REFRESH_MILLIS = 20_000L
        const val FAILURE_RETRY_MILLIS = 10_000L
        const val STATUS_X = 8
        const val STATUS_Y = 8
        const val ERROR_Y = 22
        const val ERROR_LIMIT = 72
        val SLOT_BORDER = 0xFF111315.toInt()
        val SLOT_FILL = 0xD0202428.toInt()
        val FAILED_STACK = ItemStack(Items.BARRIER)
    }
}

internal data class AuctionHouseGridLayout(val slots: List<Rect>) {
    companion object {
        fun create(bounds: Rect): AuctionHouseGridLayout {
            val slotSize = minOf(MAX_SLOT_SIZE, bounds.width / COLUMNS, bounds.height / ROWS)
                .coerceAtLeast(MIN_SLOT_SIZE)
            val width = COLUMNS * slotSize
            val height = ROWS * slotSize
            val x = bounds.x + (bounds.width - width) / 2
            val y = bounds.y + (bounds.height - height) / 2
            return AuctionHouseGridLayout(
                List(COLUMNS * ROWS) { index ->
                    Rect(x + index % COLUMNS * slotSize, y + index / COLUMNS * slotSize, slotSize, slotSize)
                },
            )
        }

        const val COLUMNS = 9
        const val ROWS = 4
        const val PAGE_SIZE = COLUMNS * ROWS
        private const val MIN_SLOT_SIZE = 18
        private const val MAX_SLOT_SIZE = 27
    }
}

internal fun auctionViewCommand(isInSkyBlock: Boolean, hasConnection: Boolean, auctionId: String): String? =
    if (isInSkyBlock && hasConnection && auctionId.isNotBlank()) "viewauction $auctionId" else null

internal fun hasAuctionHouseObtainSource(key: ItemListEntryKey): Boolean =
    auctionHouseCategoryAvailable(
        key.kind == ItemListEntryKind.SKYBLOCK,
        SkyBlockPriceData.lowestBinAvailability(key.id),
    )

internal fun auctionHouseCategoryAvailable(
    isSkyBlockItem: Boolean,
    availability: BazaarProductAvailability,
): Boolean = isSkyBlockItem && availability != BazaarProductAvailability.UNAVAILABLE

internal fun auctionRemainingTime(endMillis: Long, nowMillis: Long): String {
    val seconds = ((endMillis - nowMillis).coerceAtLeast(0L) + MILLIS_PER_SECOND - 1) / MILLIS_PER_SECOND
    return when {
        seconds >= SECONDS_PER_DAY -> "${seconds / SECONDS_PER_DAY}d"
        seconds >= SECONDS_PER_HOUR -> "${seconds / SECONDS_PER_HOUR}h"
        seconds >= SECONDS_PER_MINUTE -> "${seconds / SECONDS_PER_MINUTE}m"
        else -> "${seconds}s"
    }
}

internal fun auctionTooltipLines(
    listing: SkysoftAuctionListing,
    seller: AuctionSellerName,
    nowMillis: Long,
): List<Component> = listOf(
    styled("-----------------", ChatFormatting.DARK_GRAY, strikethrough = true),
    styled("Seller: ", ChatFormatting.GRAY).append(styled(seller.text, ChatFormatting.GRAY)),
    styled("Buy it now: ", ChatFormatting.GRAY).append(
        styled("${ItemListFormatting.number(listing.price)} coins", ChatFormatting.GOLD),
    ),
    Component.empty(),
    styled("Ends in: ", ChatFormatting.GRAY).append(
        styled(auctionRemainingTime(listing.end, nowMillis), ChatFormatting.YELLOW),
    ),
    Component.empty(),
    styled("Click to inspect!", ChatFormatting.YELLOW),
)

private fun auctionTooltipStack(
    stack: ItemStack,
    listing: SkysoftAuctionListing,
    seller: AuctionSellerName,
    nowMillis: Long,
): ItemStack = stack.copy().apply {
    val lore = get(DataComponents.LORE)?.lines().orEmpty() + auctionTooltipLines(listing, seller, nowMillis)
    set(DataComponents.LORE, ItemLore(lore))
    set(
        DataComponents.TOOLTIP_DISPLAY,
        get(DataComponents.TOOLTIP_DISPLAY)?.withHidden(DataComponents.ATTRIBUTE_MODIFIERS, true)
            ?: TooltipDisplay.DEFAULT.withHidden(DataComponents.ATTRIBUTE_MODIFIERS, true),
    )
}

private fun decodedResponse(
    response: SkysoftAuctionHouseResponse,
    key: ItemListEntryKey,
    ops: com.mojang.serialization.DynamicOps<net.minecraft.nbt.Tag>,
): DecodedAuctionResponse {
    require(response.itemId == key.id) { "Auction House returned the wrong item" }
    require(response.pageSize == AuctionHouseGridLayout.PAGE_SIZE) { "Auction House returned an invalid page size" }
    require(response.page >= 0 && response.totalPages >= 0 && response.totalListings >= 0) {
        "Auction House returned invalid pagination"
    }
    require(response.listings.size <= AuctionHouseGridLayout.PAGE_SIZE) { "Auction House returned too many listings" }
    val listings = response.listings.map { data ->
        require(data.auctionId.isNotBlank() && data.sellerUuid.isNotBlank() && data.price > 0 && data.end > 0) {
            "Auction House returned an invalid listing"
        }
        runCatching {
            decodeAuctionItemStack(data.itemBytes, ops).also { stack ->
                require(stack.skyBlockId() == key.id) { "Auction item ID does not match ${key.id}" }
            }
        }.fold(
            onSuccess = { stack -> AuctionListingView(data, stack) },
            onFailure = { error -> AuctionListingView(data, decodeError = error.message ?: "Unknown decode failure") },
        )
    }
    return DecodedAuctionResponse(
        response.page,
        response.totalPages,
        listings,
    )
}

private fun styled(
    text: String,
    color: ChatFormatting,
    strikethrough: Boolean = false,
): MutableComponent = Component.literal(text).withStyle { style ->
    style.withColor(color).withItalic(false).withStrikethrough(strikethrough)
}

internal data class AuctionSellerName(val text: String, val state: AuctionSellerState)

internal enum class AuctionSellerState {
    LOADING,
    RESOLVED,
    FAILED,
}

private class AuctionSellerNames {
    private val names = mutableMapOf<String, AuctionSellerName>()
    private var queue: CompletableFuture<*> = CompletableFuture.completedFuture(null)
    private var generation = 0

    fun clear() {
        names.clear()
        generation++
    }

    fun prefetch(value: String) {
        if (value in names) return
        val uuid = parseAuctionSellerUuid(value)
        if (uuid == null) {
            names[value] = AuctionSellerName("Unavailable", AuctionSellerState.FAILED)
            return
        }
        val visibleName = Minecraft.getInstance().connection?.getPlayerInfo(uuid)?.profile?.name
        if (!visibleName.isNullOrBlank()) {
            names[value] = AuctionSellerName(visibleName, AuctionSellerState.RESOLVED)
            return
        }
        names[value] = AuctionSellerName("Loading...", AuctionSellerState.LOADING)
        val requestedGeneration = generation
        queue = queue.handle { _, _ -> null }.thenRunAsync {
            val name = runCatching {
                Minecraft.getInstance().services().profileResolver().fetchById(uuid).orElse(null)?.name
            }.getOrNull()
            SkysoftErrorBoundary.onClientThread("Auction seller name async completion") {
                if (requestedGeneration != generation) return@onClientThread
                names[value] = if (name.isNullOrBlank()) {
                    AuctionSellerName("Unavailable", AuctionSellerState.FAILED)
                } else {
                    AuctionSellerName(name, AuctionSellerState.RESOLVED)
                }
            }
        }
    }

    fun state(value: String): AuctionSellerName = names[value]
        ?: AuctionSellerName("Loading...", AuctionSellerState.LOADING)

}

internal fun parseAuctionSellerUuid(value: String): UUID? {
    val compact = value.replace("-", "")
    if (compact.length != UUID_HEX_LENGTH || compact.any { it.digitToIntOrNull(HEX_RADIX) == null }) return null
    val dashed = buildString(UUID_STRING_LENGTH) {
        append(compact, 0, UUID_TIME_LOW_END)
        append('-')
        append(compact, UUID_TIME_LOW_END, UUID_TIME_MID_END)
        append('-')
        append(compact, UUID_TIME_MID_END, UUID_TIME_HIGH_END)
        append('-')
        append(compact, UUID_TIME_HIGH_END, UUID_CLOCK_SEQUENCE_END)
        append('-')
        append(compact, UUID_CLOCK_SEQUENCE_END, UUID_HEX_LENGTH)
    }
    return runCatching { UUID.fromString(dashed) }.getOrNull()
}

private fun errorMessage(error: Throwable?): String = generateSequence(error) { it.cause }
    .lastOrNull()
    ?.message
    ?: "Auction House request failed"

private data class AuctionListingView(
    val data: SkysoftAuctionListing,
    val stack: ItemStack? = null,
    val decodeError: String? = null,
)

private data class DecodedAuctionResponse(
    val page: Int,
    val pageCount: Int,
    val listings: List<AuctionListingView>,
)

private enum class AuctionHousePanelState {
    NOT_LOADED,
    LOADING,
    READY,
    FAILED,
}

private const val UUID_HEX_LENGTH = 32
private const val UUID_STRING_LENGTH = 36
private const val UUID_TIME_LOW_END = 8
private const val UUID_TIME_MID_END = 12
private const val UUID_TIME_HIGH_END = 16
private const val UUID_CLOCK_SEQUENCE_END = 20
private const val HEX_RADIX = 16
private const val MILLIS_PER_SECOND = 1_000L
private const val SECONDS_PER_MINUTE = 60L
private const val SECONDS_PER_HOUR = 60 * SECONDS_PER_MINUTE
private const val SECONDS_PER_DAY = 24 * SECONDS_PER_HOUR
