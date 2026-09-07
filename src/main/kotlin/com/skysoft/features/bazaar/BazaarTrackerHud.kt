package com.skysoft.features.bazaar

import com.skysoft.data.skyblock.BazaarOrderType
import com.skysoft.data.ProfileStorageView
import com.skysoft.features.inventory.InventoryOverlayInput
import com.skysoft.gui.OverlayControlMouse
import com.skysoft.gui.transform
import com.skysoft.utils.MinecraftClient
import com.skysoft.utils.input.InputUtilities
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen

internal fun renderHud(context: GuiGraphicsExtractor) {
    val minecraft = Minecraft.getInstance()
    if (!isBazaarTrackerVisible(minecraft)) {
        hoveredControlArea = null
        return
    }
    val inventoryScreen = MinecraftClient.screen(minecraft) as? AbstractContainerScreen<*>
    val inventoryOpen = inventoryScreen != null
    val renderable = buildRenderable(inventoryOpen)
    if (renderable.width <= 0 || renderable.height <= 0) {
        hoveredControlArea = null
        return
    }
    val (mouseX, mouseY) = InputUtilities.scaledMousePosition(minecraft)
    val (normalMouseX, normalMouseY) = OverlayControlMouse.normalPoint(mouseX, mouseY)
    val (screenMouseX, screenMouseY) = OverlayControlMouse.screenPoint(mouseX, mouseY)
    val interactive = inventoryScreen != null &&
        !InventoryOverlayInput.isPointCovered(inventoryScreen, screenMouseX.toDouble(), screenMouseY.toDouble())
    context.nextStratum()
    renderPositioned(context, renderable, interactive, normalMouseX, normalMouseY)
    if (interactive) {
        context.nextStratum()
        renderTrackerControlTooltip(context, mouseX, mouseY)
    }
}

internal fun shouldRenderBazaarTrackerInventoryOverlay(): Boolean {
    val minecraft = Minecraft.getInstance()
    return isBazaarTrackerVisible(minecraft) &&
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
    val transform = config.position.transform(renderable.width, renderable.height)
    val localMouseX = mouseX?.let(transform::localX)
    val localMouseY = mouseY?.let(transform::localY)
    val hoveredArea = transform.render(context) {
        if (updateControls) renderable.render(context, localMouseX, localMouseY) else {
            renderable.render(context)
            null
        }
    }
    hoveredControlArea = if (updateControls) hoveredArea?.toOverlayArea(transform) else null
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
                    MIN_TRACKER_DISPLAY_ORDERS,
                    MAX_TRACKER_DISPLAY_ORDERS,
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

private fun displayModeLine(): DisplayLine = DisplayLine.segments(
    LineSegment("§7Display Mode "),
    LineSegment(
        if (displayMode == TrackerDisplayMode.SESSION) "§a§l[Session]" else "§a§l[Total]",
        TrackerControl.TOGGLE_MODE,
    ),
)

internal fun resetLine(): DisplayLine = DisplayLine.segments(
    LineSegment("§c[Reset ${displayMode.displayName}]", TrackerControl.RESET),
)

private fun displayOrders(): List<ProfileStorageView.BazaarOrderData> =
    storage.activeOrders.sortedWith(
        compareByDescending<ProfileStorageView.BazaarOrderData> { statusPriority(statusFor(it)) }
            .thenByDescending { it.updatedAtMillis }
            .thenBy { it.createdAtMillis },
    )

internal fun orderLine(order: ProfileStorageView.BazaarOrderData): DisplayLine {
    val status = statusFor(order)
    val typeColor = if (order.type == BazaarOrderType.BUY) "§b" else "§d"
    val progress = "${fillProgressStyle(order)}(${formatAmount(visibleFilledAmount(order))}/${formatOrderAmount(order)})"
    val text = "$typeColor${order.type.label} §e${formatOrderAmount(order)}x §f${order.itemName} " +
        "§7@ §6${formatCoins(order.pricePerUnit)} §7$progress"
    return DisplayLine(status.label, status.color, listOf(LineSegment(text)))
}

internal fun markFillHighlight(order: ProfileStorageView.BazaarOrderData, filled: Long) {
    if (isPartialFill(order, filled)) {
        fillHighlightExpiresAt[order.id] = System.currentTimeMillis() + FILL_HIGHLIGHT_MILLIS
    }
}

private fun fillProgressStyle(order: ProfileStorageView.BazaarOrderData): String {
    val expiresAt = fillHighlightExpiresAt[order.id] ?: return "§8"
    val filled = visibleFilledAmount(order)
    if (System.currentTimeMillis() >= expiresAt || !isPartialFill(order, filled)) {
        fillHighlightExpiresAt.remove(order.id)
        return "§8"
    }
    return "§a§l"
}

internal fun isPartialFill(order: ProfileStorageView.BazaarOrderData, filled: Long): Boolean =
    order.amountOrdered > 0 && filled > order.claimedAmount && filled < order.maximumAmount()

internal fun requireMarketProof(order: ProfileStorageView.BazaarOrderData) {
    val market = BazaarOrderBookApi.get(order.productId)
    if (market == null || !rawMarketStatusFor(order, market).isWarning) marketProofMillis[order.id] = order.createdAtMillis
}

internal fun statusFor(order: ProfileStorageView.BazaarOrderData): OrderStatus {
    if (order.amountOrdered > 0 && visibleFilledAmount(order) >= order.maximumAmount()) return OrderStatus.FILLED
    return marketStatusFor(order)
}

private fun statusPriority(status: OrderStatus): Int = when (status) {
    OrderStatus.FILLED -> FILLED_STATUS_PRIORITY
    OrderStatus.OUTBID, OrderStatus.UNDERCUT -> WARNING_STATUS_PRIORITY
    OrderStatus.COMPETITIVE -> COMPETITIVE_STATUS_PRIORITY
}

