package com.skysoft.features.bazaar

import com.skysoft.data.ProfileStorage
import com.skysoft.data.hypixel.HypixelLocationState
import com.skysoft.gui.OverlayControlMouse
import com.skysoft.utils.MinecraftClient
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import kotlin.math.floor
import kotlin.math.roundToInt

internal fun renderHud(context: GuiGraphicsExtractor) {
    val minecraft = Minecraft.getInstance()
    if (
        !config.enabled ||
        !shouldShowBazaarTrackerContent(config.details.hideWhenEmpty, storage.activeOrders.isNotEmpty()) ||
        MinecraftClient.isGuiHidden(minecraft) ||
        !HypixelLocationState.inSkyBlock
    ) {
        hoveredControlArea = null
        return
    }
    val inventoryOpen = MinecraftClient.screen(minecraft) is AbstractContainerScreen<*>
    val renderable = buildRenderable(inventoryOpen)
    if (renderable.width <= 0 || renderable.height <= 0) {
        hoveredControlArea = null
        return
    }
    val window = minecraft.window
    val mouseX = minecraft.mouseHandler.getScaledXPos(window).toInt()
    val mouseY = minecraft.mouseHandler.getScaledYPos(window).toInt()
    val (normalMouseX, normalMouseY) = OverlayControlMouse.normalPoint(mouseX, mouseY)
    context.nextStratum()
    renderPositioned(context, renderable, inventoryOpen, normalMouseX, normalMouseY)
    if (inventoryOpen) {
        context.nextStratum()
        renderTrackerControlTooltip(context, mouseX, mouseY)
    }
}

internal fun shouldRenderBazaarTrackerInventoryOverlay(): Boolean {
    val minecraft = Minecraft.getInstance()
    return config.enabled &&
        shouldShowBazaarTrackerContent(config.details.hideWhenEmpty, storage.activeOrders.isNotEmpty()) &&
        !MinecraftClient.isGuiHidden(minecraft) &&
        HypixelLocationState.inSkyBlock &&
        MinecraftClient.screen(minecraft) is AbstractContainerScreen<*>
}

internal fun shouldShowBazaarTrackerContent(hideWhenEmpty: Boolean, hasOrders: Boolean): Boolean =
    !hideWhenEmpty || hasOrders

internal fun renderPositioned(
    context: GuiGraphicsExtractor,
    renderable: BazaarTrackerRenderable,
    updateControls: Boolean,
    mouseX: Int? = null,
    mouseY: Int? = null,
) {
    val scale = config.position.effectiveScale
    val scaledWidth = (renderable.width * scale).roundToInt()
    val scaledHeight = (renderable.height * scale).roundToInt()
    val x = config.position.getAbsX0AllowingOverflow(scaledWidth)
    val y = config.position.getAbsY0AllowingOverflow(scaledHeight)
    val localMouseX = mouseX?.let { floor((it - x) / scale).toInt() }
    val localMouseY = mouseY?.let { floor((it - y) / scale).toInt() }
    context.pose().pushMatrix()
    context.pose().translate(x.toFloat(), y.toFloat())
    context.pose().scale(scale, scale)
    val hoveredArea = if (updateControls) renderable.render(context, localMouseX, localMouseY) else {
        renderable.render(context)
        null
    }
    context.pose().popMatrix()
    hoveredControlArea = if (updateControls) hoveredArea?.toOverlayArea(x, y, scale) else null
}

internal fun buildRenderable(inventoryOpen: Boolean): BazaarTrackerRenderable {
    val orders = displayOrders()
    val lines = buildList {
        add(DisplayLine.title("§e§lBazaar Tracker"))
        if (orders.isEmpty()) {
            add(DisplayLine.text("§7Open §eBazaar Orders §7to load orders."))
        } else {
            orders.take(
                config.settings.maxOrders.coerceIn(
                    BazaarTrackerDisplayLimits.MIN_ORDERS,
                    BazaarTrackerDisplayLimits.MAX_ORDERS,
                ),
            )
                .forEach { add(orderLine(it)) }
        }
        if (config.details.flippingInfo) {
            val activeValue = trackedInvestedValue(storage)
            val profit = if (displayMode == TrackerDisplayMode.SESSION) sessionKnownProfit else storage.totalKnownProfit
            add(DisplayLine.text("§7Invested: §6${formatCoins(activeValue)}"))
            add(DisplayLine.text("§7Profit: §a${formatSigned(profit)}"))
            if (inventoryOpen) add(displayModeLine())
            if (inventoryOpen) add(resetLine())
        }
    }
    return BazaarTrackerRenderable(lines, config.details.showBackground)
}

internal fun displayModeLine(): DisplayLine = DisplayLine.segments(
    LineSegment("§7Display Mode "),
    LineSegment(
        if (displayMode == TrackerDisplayMode.SESSION) "§a§l[Session]" else "§a§l[Total]",
        TrackerControl.TOGGLE_MODE,
    ),
)

internal fun resetLine(): DisplayLine = DisplayLine.segments(
    LineSegment("§c[Reset ${displayMode.displayName}]", TrackerControl.RESET),
)

internal fun displayOrders(): List<ProfileStorage.BazaarOrderData> =
    storage.activeOrders.sortedWith(
        compareByDescending<ProfileStorage.BazaarOrderData> { statusPriority(statusFor(it)) }
            .thenByDescending { it.updatedAtMillis }
            .thenBy { it.createdAtMillis },
    )

internal fun orderLine(order: ProfileStorage.BazaarOrderData): DisplayLine {
    val status = statusFor(order)
    val typeColor = if (order.type == BazaarOrderType.BUY) "§b" else "§d"
    val progress = "${fillProgressStyle(order)}(${formatAmount(visibleFilledAmount(order))}/${formatOrderAmount(order)})"
    val text = "$typeColor${order.type.label} §e${formatOrderAmount(order)}x §f${order.itemName} " +
        "§7@ §6${formatCoins(order.pricePerUnit)} §7$progress"
    return DisplayLine(status.label, status.color, listOf(LineSegment(text)))
}

internal fun markFillHighlight(order: ProfileStorage.BazaarOrderData, filled: Long) {
    if (isPartialFill(order, filled)) {
        fillHighlightExpiresAt[order.id] = System.currentTimeMillis() + BazaarTrackerTiming.FILL_HIGHLIGHT_MILLIS
    }
}

internal fun fillProgressStyle(order: ProfileStorage.BazaarOrderData): String {
    val expiresAt = fillHighlightExpiresAt[order.id] ?: return "§8"
    val filled = visibleFilledAmount(order)
    if (System.currentTimeMillis() >= expiresAt || !isPartialFill(order, filled)) {
        fillHighlightExpiresAt.remove(order.id)
        return "§8"
    }
    return "§a§l"
}

internal fun isPartialFill(order: ProfileStorage.BazaarOrderData, filled: Long): Boolean =
    order.amountOrdered > 0 && filled > order.claimedAmount && filled < order.maximumAmount()

internal fun requireMarketProof(order: ProfileStorage.BazaarOrderData) {
    val market = BazaarOrderBookApi.get(order.productId)
    if (market == null || !rawMarketStatusFor(order, market).isWarning) marketProofMillis[order.id] = order.createdAtMillis
}

internal fun statusFor(order: ProfileStorage.BazaarOrderData): OrderStatus {
    if (order.amountOrdered > 0 && visibleFilledAmount(order) >= order.maximumAmount()) return OrderStatus.FILLED
    return marketStatusFor(order)
}

internal fun statusPriority(status: OrderStatus): Int = when (status) {
    OrderStatus.FILLED -> BazaarTrackerStatusPriority.FILLED
    OrderStatus.OUTBID, OrderStatus.UNDERCUT -> BazaarTrackerStatusPriority.WARNING
    OrderStatus.COMPETITIVE -> BazaarTrackerStatusPriority.COMPETITIVE
}

