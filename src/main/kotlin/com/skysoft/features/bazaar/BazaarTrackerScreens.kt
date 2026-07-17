package com.skysoft.features.bazaar

import com.skysoft.data.ProfileStorage
import com.skysoft.data.hypixel.HypixelLocationState
import com.skysoft.utils.ChangeResult
import com.skysoft.utils.TextUtilities.cleanSkyBlockText
import com.skysoft.utils.input.InputHandlingResult
import com.skysoft.utils.render.LegacyTextRenderer
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.ItemStack
import org.lwjgl.glfw.GLFW

internal fun readConfirmScreen(screen: AbstractContainerScreen<*>, expectedType: BazaarOrderType) {
    lastOrdersInventoryKey = null
    pendingOrdersInventoryKey = null
    pendingOrdersInventoryStableTicks = 0
    for (slot in nonPlayerSlots(screen)) {
        val parsed = parseConfirmStack(slot.item, expectedType) ?: continue
        pendingSetup = parsed
        parsed.taxPercent?.let { updateTax(it) }
        return
    }
}

internal fun readOrdersScreen(screen: AbstractContainerScreen<*>) {
    val key = buildInventoryKey(screen)
    if (key == lastOrdersInventoryKey) return
    if (key != pendingOrdersInventoryKey) {
        pendingOrdersInventoryKey = key
        pendingOrdersInventoryStableTicks = 1
        return
    }
    pendingOrdersInventoryStableTicks++
    if (pendingOrdersInventoryStableTicks < GUI_MISSING_PRUNE_INVENTORY_STABLE_TICKS) return
    lastOrdersInventoryKey = key
    pendingOrdersInventoryKey = null
    pendingOrdersInventoryStableTicks = 0

    val slots = nonPlayerSlots(screen)
    if (!ordersMenuLoaded(slots)) return

    val parsedOrders = slots.mapNotNull { slot -> parseOrdersStack(slot.item)?.copy(guiSlot = slot.containerSlot) }
    if (
        parsedOrders.isEmpty() &&
        !isBazaarOrderAreaEmpty(
            slots.map { it.containerSlot },
            slots.filterNot { it.item.isEmpty }.mapTo(mutableSetOf()) { it.containerSlot },
        )
    ) {
        return
    }

    val reconciliation = reconcileBazaarSnapshot(storage.activeOrders.toList(), parsedOrders)
    val matchedOrderIds = reconciliation.matches.mapTo(mutableSetOf()) { it.order.id }
    var changed = false
    for (match in reconciliation.matches) {
        val parsed = match.parsed
        parsed.taxPercent?.let { updateTax(it) }
        val updateResult = updateOrderFromGui(match.order, parsed)
        missingFromOrdersGuiScans.remove(match.order.id)
        changed = updateResult == ChangeResult.CHANGED || changed
    }
    for (parsed in reconciliation.unmatchedRows) {
        parsed.taxPercent?.let { updateTax(it) }
        if (!parsed.canCreateOrderFromGui()) continue
        val added = parsed.toOrderData()
        addActiveOrder(added, requireFreshMarketProof = false)
        matchedOrderIds += added.id
        changed = true
    }
    changed = pruneOrdersMissingFromGui(matchedOrderIds, parsedOrders) == ChangeResult.CHANGED || changed
    if (changed) {
        markBazaarTrackerChanged(refreshFillEstimates = true)
    }
}

internal fun ordersMenuLoaded(slots: List<Slot>): Boolean {
    val names = slots.asSequence()
        .filter { !it.item.isEmpty }
        .mapNotNull { it.item.textLines().firstOrNull()?.clean() }
        .toSet()
    return "Go Back" in names && "Claim All Coins" in names
}

internal fun readOrderOptionsScreen(screen: AbstractContainerScreen<*>) {
    lastOrdersInventoryKey = null
    pendingOrdersInventoryKey = null
    pendingOrdersInventoryStableTicks = 0
    val order = pendingOrderOptionId?.let { id -> storage.activeOrders.firstOrNull { it.id == id } }
    for (slot in nonPlayerSlots(screen)) {
        val parsed = parseCancelStack(slot.item, order) ?: continue
        pendingCancel = parsed
        return
    }
}

internal fun handleBazaarTrackerMouseClick(
    screen: AbstractContainerScreen<*>,
    click: MouseButtonEvent,
): InputHandlingResult {
    if (!config.enabled) return InputHandlingResult.IGNORED
    if (click.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT || click.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
        if (handleTrackerControlClick(click.button()) == InputHandlingResult.CONSUMED) {
            return InputHandlingResult.CONSUMED
        }
    }
    if (click.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) return InputHandlingResult.IGNORED
    recordClickedOrder(screen, click)
    recordOrderOptionsClick(screen, click)
    return InputHandlingResult.IGNORED
}

internal fun renderBazaarTrackerSlotIndicatorBackground(
    screen: AbstractContainerScreen<*>,
    context: GuiGraphicsExtractor,
    slot: Slot,
) {
    val indicator = slotIndicator(screen, slot) ?: return
    context.fill(
        slot.x - SLOT_INDICATOR_INSET,
        slot.y - SLOT_INDICATOR_INSET,
        slot.x + SLOT_INDICATOR_END_OFFSET,
        slot.y + SLOT_INDICATOR_END_OFFSET,
        indicator.fillColor,
    )
}

internal fun renderBazaarTrackerSlotIndicatorOverlay(
    screen: AbstractContainerScreen<*>,
    context: GuiGraphicsExtractor,
    slot: Slot,
) {
    val indicator = slotIndicator(screen, slot) ?: return
    context.outline(
        slot.x - SLOT_INDICATOR_INSET,
        slot.y - SLOT_INDICATOR_INSET,
        SLOT_INDICATOR_SIZE,
        SLOT_INDICATOR_SIZE,
        indicator.outlineColor,
    )
    if (!indicator.partial) return
    context.fill(
        slot.x + PARTIAL_MARKER_X_OFFSET,
        slot.y + PARTIAL_MARKER_Y_OFFSET,
        slot.x + SLOT_INDICATOR_END_OFFSET,
        slot.y + SLOT_INDICATOR_END_OFFSET,
        PARTIAL_MARKER_BACKGROUND,
    )
    LegacyTextRenderer.draw(
        context,
        "§e%",
        slot.x + PARTIAL_MARKER_TEXT_X_OFFSET,
        slot.y + PARTIAL_MARKER_TEXT_Y_OFFSET,
        shadow = true,
        defaultColor = PARTIAL_MARKER_TEXT_COLOR,
    )
}

internal fun slotIndicator(screen: AbstractContainerScreen<*>, slot: Slot): SlotIndicator? {
    if (!config.enabled || !config.details.visualIndicators || !HypixelLocationState.inSkyBlock) return null
    val title = screen.title.cleanSkyBlockText()
    if (!title.contains("Bazaar Orders")) return null
    val parsed = parseOrdersStack(slot.item)?.copy(guiSlot = slot.containerSlot) ?: return null
    val order = findMatchingOrder(parsed, emptySet()) ?: return null
    val parsedFilled = parsed.filledAmount ?: 0L
    val parsedMaximumAmount = if (parsed.amountResolution > 0.0) {
        (kotlin.math.ceil(parsed.amount + parsed.amountResolution).toLong() - 1L).coerceAtLeast(parsed.amount)
    } else {
        parsed.amount
    }
    val filled = parsedMaximumAmount > 0L && parsedFilled >= parsedMaximumAmount
    val partial = !filled && parsedFilled > 0L
    val status = if (filled) OrderStatus.FILLED else marketStatusFor(order)
    return SlotIndicator(
        fillColor = slotFillColor(status),
        outlineColor = slotOutlineColor(status),
        partial = partial,
    )
}

internal fun marketStatusFor(order: ProfileStorage.BazaarOrderData): OrderStatus {
    val market = BazaarOrderBookApi.get(order.productId) ?: return OrderStatus.COMPETITIVE
    val status = rawMarketStatusFor(order, market)
    if (status.isWarning && !hasMarketProof(order, market)) return OrderStatus.COMPETITIVE
    return status
}

internal fun rawMarketStatusFor(order: ProfileStorage.BazaarOrderData, market: BazaarMarket): OrderStatus = when (order.type) {
    BazaarOrderType.BUY -> if (order.pricePerUnit + BAZAAR_PRICE_EPSILON >= market.bestBuyOrder) {
        OrderStatus.COMPETITIVE
    } else {
        OrderStatus.OUTBID
    }
    BazaarOrderType.SELL -> if (order.pricePerUnit <= market.bestSellOrder + BAZAAR_PRICE_EPSILON) {
        OrderStatus.COMPETITIVE
    } else {
        OrderStatus.UNDERCUT
    }
}

private fun hasMarketProof(order: ProfileStorage.BazaarOrderData, market: BazaarMarket): Boolean {
    val proofMillis = marketProofMillis[order.id] ?: return true
    if (market.updatedAtMillis <= 0L) {
        marketProofMillis.remove(order.id)
        return true
    }
    if (market.updatedAtMillis <= proofMillis) return false
    marketProofMillis.remove(order.id)
    return true
}

internal fun slotFillColor(status: OrderStatus): Int = when (status) {
    OrderStatus.FILLED -> SLOT_FILLED_FILL
    OrderStatus.OUTBID, OrderStatus.UNDERCUT -> SLOT_UNDERCUT_FILL
    OrderStatus.COMPETITIVE -> SLOT_COMPETITIVE_FILL
}

internal fun slotOutlineColor(status: OrderStatus): Int = when (status) {
    OrderStatus.FILLED -> SLOT_FILLED_OUTLINE
    OrderStatus.OUTBID, OrderStatus.UNDERCUT -> SLOT_UNDERCUT_OUTLINE
    OrderStatus.COMPETITIVE -> SLOT_COMPETITIVE_OUTLINE
}

internal fun recordClickedOrder(screen: AbstractContainerScreen<*>, click: MouseButtonEvent) {
    if (click.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) return
    val title = screen.title.cleanSkyBlockText()
    if (!title.contains("Bazaar Orders")) return
    val slot = slotAt(screen, click.x().toInt(), click.y().toInt()) ?: return
    val parsed = parseOrdersStack(slot.item)?.copy(guiSlot = slot.containerSlot) ?: return
    val now = System.currentTimeMillis()
    val signature = "${screen.menu.containerId}|${slot.containerSlot}|${ItemStack.hashItemAndComponents(slot.item)}"
    if (
        signature == lastOrdersGuiClickSignature &&
        now - lastOrdersGuiClickMillis < DUPLICATE_CLICK_SUPPRESS_MILLIS
    ) return
    lastOrdersGuiClickMillis = now
    lastOrdersGuiClickSignature = signature
    pendingOrderOptionId = findMatchingOrderMatch(parsed, emptySet())?.order?.id
}

internal fun recordOrderOptionsClick(screen: AbstractContainerScreen<*>, click: MouseButtonEvent) {
    val title = screen.title.cleanSkyBlockText()
    if (title != "Order options") return
    val slot = slotAt(screen, click.x().toInt(), click.y().toInt()) ?: return
    val clean = slot.item.textLines().map { it.clean() }
    if (clean.none { it.contains("Cancel Order") }) return
    if (clean.any { it.startsWith("Cannot cancel order while", ignoreCase = true) }) return

    val order = pendingOrderOptionId?.let { id -> storage.activeOrders.firstOrNull { it.id == id } }
    val cancel = parseCancelStack(slot.item, order) ?: pendingCancel ?: order?.let {
        PendingCancel(
            orderId = it.id,
            type = it.type,
            itemName = it.itemName,
            productId = it.productId,
            amount = it.remainingAmount(),
            refundedCoins = null,
        )
    } ?: return
    pendingCancel = cancel
}
