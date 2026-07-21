package com.skysoft.features.bazaar

import com.skysoft.data.ProfileStorage
import com.skysoft.data.hypixel.HypixelLocationState
import com.skysoft.data.skyblock.buyClaimPattern
import com.skysoft.data.skyblock.sellCancelPattern
import com.skysoft.data.skyblock.sellSetupPattern
import com.skysoft.utils.TextUtilities.cleanSkyBlockText
import kotlin.math.max
import kotlin.math.roundToLong

internal fun handleChat(rawMessage: String) {
    if (!config.enabled || !HypixelLocationState.inSkyBlock) return
    val message = rawMessage.cleanSkyBlockText()
    if (!message.startsWith("[Bazaar]") && !message.startsWith("Cancelled!")) return
    handleBazaarChatMessage(message)
}

internal fun handleBazaarChatMessage(message: String) {
    tryHandleInstantTransactionMessage(message) ||
        tryHandleSetupChatMessage(message) ||
        tryHandleOrderFlippedChatMessage(message) ||
        tryHandleCancelChatMessage(message) ||
        tryHandleClaimChatMessage(message) ||
        tryHandleFilledChatMessage(message)
}

internal fun tryHandleSetupChatMessage(message: String): Boolean {
    buySetupPattern.matchEntire(message)?.let { match ->
        confirmSetup(
            BazaarOrderType.BUY,
            match.groupValues[BazaarChatGroups.SETUP_AMOUNT],
            match.groupValues[BazaarChatGroups.SETUP_ITEM],
            match.groupValues[BazaarChatGroups.SETUP_TOTAL],
        )
        return true
    }
    sellSetupPattern.matchEntire(message)?.let { match ->
        confirmSetup(
            BazaarOrderType.SELL,
            match.groupValues[BazaarChatGroups.SETUP_AMOUNT],
            match.groupValues[BazaarChatGroups.SETUP_ITEM],
            match.groupValues[BazaarChatGroups.SETUP_TOTAL],
        )
        return true
    }
    return false
}

internal fun tryHandleOrderFlippedChatMessage(message: String): Boolean {
    orderFlippedPattern.matchEntire(message)?.let { match ->
        flipOrder(
            parseExactLong(match.groupValues[BazaarChatGroups.FLIPPED_AMOUNT]),
            match.groupValues[BazaarChatGroups.FLIPPED_ITEM].trim(),
            parseNumber(match.groupValues[BazaarChatGroups.FLIPPED_PROFIT]).value,
        )
        return true
    }
    return false
}

internal fun tryHandleCancelChatMessage(message: String): Boolean {
    buyCancelPattern.matchEntire(message)?.let { match ->
        cancelBuyOrderFromChat(parseNumber(match.groupValues[BazaarChatGroups.CANCEL_REFUND]).value)
        return true
    }
    sellCancelPattern.matchEntire(message)?.let { match ->
        cancelSellOrderFromChat(
            parseExactLong(match.groupValues[BazaarChatGroups.CANCEL_AMOUNT]),
            match.groupValues[BazaarChatGroups.CANCEL_ITEM].trim(),
        )
        return true
    }
    return false
}

internal fun tryHandleClaimChatMessage(message: String): Boolean {
    buyClaimPattern.matchEntire(message)?.let { match ->
        claimBuyOrder(
            parseExactLong(match.groupValues[BazaarChatGroups.BUY_CLAIM_AMOUNT]),
            match.groupValues[BazaarChatGroups.BUY_CLAIM_ITEM].trim(),
            parseNumber(match.groupValues[BazaarChatGroups.BUY_CLAIM_COINS]).value,
            parseNumber(match.groupValues[BazaarChatGroups.BUY_CLAIM_UNIT_PRICE]).value,
        )
        return true
    }
    sellClaimPattern.matchEntire(message)?.let { match ->
        claimSellOrder(
            parseNumber(match.groupValues[BazaarChatGroups.SELL_CLAIM_COINS]).value,
            parseExactLong(match.groupValues[BazaarChatGroups.SELL_CLAIM_AMOUNT]),
            match.groupValues[BazaarChatGroups.SELL_CLAIM_ITEM].trim(),
            parseNumber(match.groupValues[BazaarChatGroups.SELL_CLAIM_UNIT_PRICE]).value,
        )
        return true
    }
    return false
}

internal fun tryHandleFilledChatMessage(message: String): Boolean {
    filledPattern.matchEntire(message)?.let { match ->
        val type = if (match.groupValues[BazaarChatGroups.FILLED_TYPE] == "Buy Order") BazaarOrderType.BUY else BazaarOrderType.SELL
        markFilled(
            type,
            parseExactLong(match.groupValues[BazaarChatGroups.FILLED_AMOUNT]),
            match.groupValues[BazaarChatGroups.FILLED_ITEM].trim(),
        )
        return true
    }
    return false
}

internal fun confirmSetup(type: BazaarOrderType, amountText: String, itemName: String, totalText: String) {
    val amount = parseExactLong(amountText)
    val totalCoins = parseNumber(totalText).value
    val pending = pendingSetup?.takeIf {
        it.type == type &&
            namesMatch(it.itemName, itemName) &&
            it.amount == amount
    }
    val pricePerUnit = pending?.pricePerUnit?.takeIf { it > 0.0 } ?: (totalCoins / amount.coerceAtLeast(1))
    val order = ProfileStorage.BazaarOrderData(
        type = type,
        itemName = pending?.itemName ?: itemName.trim(),
        productId = pending?.productId ?: resolveProductId(itemName),
        amountOrdered = amount,
        pricePerUnit = pricePerUnit,
        totalCoins = totalCoins,
        filledAmount = 0L,
        claimedAmount = 0L,
        claimedCoins = 0.0,
        amountResolution = 0.0,
        pricePerUnitResolution = pending?.pricePerUnitResolution ?: 0.0,
        totalCoinsResolution = 0.0,
        setupConfirmed = true,
        flipBatchId = if (type == BazaarOrderType.BUY && config.details.flippingInfo) {
            activeFlipBatch(storage)
        } else {
            null
        },
    )
    addActiveOrder(order, requireFreshMarketProof = true)
    if (type == BazaarOrderType.BUY) sessionBuySetupValue += totalCoins else sessionSellSetupValue += order.totalCoins
    pending?.taxPercent?.let { updateTax(it) }
    pendingSetup = null
    markBazaarTrackerChanged(refreshFillEstimates = true, refreshOrderBook = true)
}

internal fun flipOrder(amount: Long, itemName: String, expectedProfit: Double) {
    val buyOrder = pendingOrderOptionId
        ?.let { id -> storage.activeOrders.firstOrNull { it.id == id && it.type == BazaarOrderType.BUY } }
        ?: storage.activeOrders
            .asSequence()
            .filter { it.type == BazaarOrderType.BUY && namesMatch(it.itemName, itemName) }
            .minWithOrNull(
                compareBy<ProfileStorage.BazaarOrderData> {
                    if (it.filledAmount >= it.amountOrdered) 0 else 1
                }.thenBy { amountDistance(it.amountOrdered, amount) }
            )

    val productId = buyOrder?.productId
    val cleanItemName = buyOrder?.itemName ?: itemName.trim()
    val costPerUnit = buyOrder?.pricePerUnit ?: 0.0
    if (buyOrder != null) addTrackedBuyLot(storage, buyOrder, amount, costPerUnit)

    if (buyOrder != null) {
        removeOrReduceOrderAfterClaim(buyOrder, amount)
    }

    val sellPricePerUnit = costPerUnit + expectedProfit / amount.coerceAtLeast(1)
    val taxMultiplier = (1.0 - storage.taxPercent / BAZAAR_PERCENT_SCALE)
        .coerceAtLeast(MIN_BAZAAR_TAX_MULTIPLIER)
    val totalCoins = sellPricePerUnit * amount * taxMultiplier
    val sellOrder = ProfileStorage.BazaarOrderData(
        type = BazaarOrderType.SELL,
        itemName = cleanItemName,
        productId = productId,
        amountOrdered = amount,
        pricePerUnit = sellPricePerUnit,
        totalCoins = totalCoins,
        filledAmount = 0L,
        claimedAmount = 0L,
        claimedCoins = 0.0,
        setupConfirmed = true,
        flipBatchId = buyOrder?.flipBatchId,
    )
    addActiveOrder(sellOrder, requireFreshMarketProof = true)
    sessionSellSetupValue += totalCoins
    pendingOrderOptionId = null
    markBazaarTrackerChanged(refreshFillEstimates = true, refreshOrderBook = true)
}

internal fun claimBuyOrder(amount: Long, itemName: String, coins: Double, unitPrice: Double) {
    val order = findClaimOrder(BazaarOrderType.BUY, itemName, amount, unitPrice)
    if (order != null) {
        applyClaimedAmount(order, amount)
    } else {
        clearPendingOrderAction()
        return
    }
    val cost = if (unitPrice > 0.0) unitPrice else coins / amount.coerceAtLeast(1)
    addTrackedBuyLot(storage, order, amount, cost)
    clearPendingOrderAction()
    markBazaarTrackerChanged()
}

internal fun claimSellOrder(coins: Double, amount: Long, itemName: String, unitPrice: Double) {
    val order = findClaimOrder(BazaarOrderType.SELL, itemName, amount, unitPrice)
    if (order != null) {
        applyClaimedAmount(order, amount, claimedCoins = coins)
    } else {
        clearPendingOrderAction()
        return
    }
    prepareCraftedCostBasis(order.productId, itemName, amount)
    val sale = consumeTrackedLotsForSale(storage, order.productId, itemName, amount, coins)
    val knownProfit = sale.profit
    storage.totalKnownProfit += knownProfit
    sessionKnownProfit += knownProfit
    clearPendingOrderAction()
    markBazaarTrackerChanged()
}

internal fun markFilled(type: BazaarOrderType, amount: Long, itemName: String) {
    val candidates = storage.activeOrders
        .filter { it.type == type && namesMatch(it.itemName, itemName) }
        .filter {
            haveOverlappingRanges(
                it.amountOrdered.toDouble(),
                it.amountResolution,
                amount.toDouble(),
                0.0,
                EXACT_AMOUNT_EPSILON,
            )
        }
        .filter { it.filledAmount < it.maximumAmount() }
    val order = selectFilledOrder(candidates, type)
        ?: return
    val previousFilled = order.filledAmount
    order.amountOrdered = amount
    order.amountResolution = 0.0
    order.filledAmount = max(order.filledAmount, amount)
    playProgressAlert(order, previousFilled)
    order.updatedAtMillis = System.currentTimeMillis()
    if (pendingCancel?.orderId == order.id) pendingCancel = null
    markBazaarTrackerChanged()
}

internal fun selectFilledOrder(
    candidates: List<ProfileStorage.BazaarOrderData>,
    type: BazaarOrderType,
): ProfileStorage.BazaarOrderData? {
    val priceComparator = when (type) {
        BazaarOrderType.BUY -> compareByDescending<ProfileStorage.BazaarOrderData> { it.pricePerUnit }
        BazaarOrderType.SELL -> compareBy { it.pricePerUnit }
    }
    return candidates.minWithOrNull(priceComparator.thenBy { it.createdAtMillis })
}

internal fun cancelBuyOrderFromChat(refundedCoins: Double) {
    val pending = pendingCancel?.takeIf { it.type == BazaarOrderType.BUY }
    val order = pending?.orderId?.let { id -> storage.activeOrders.firstOrNull { it.id == id } }
        ?: pendingOrderOptionId?.let { id -> storage.activeOrders.firstOrNull { it.id == id && it.type == BazaarOrderType.BUY } }
    val orderId = order?.id ?: pending?.orderId ?: run {
        clearPendingOrderAction()
        return
    }
    val amount = pending?.amount ?: order?.let {
        (refundedCoins / it.pricePerUnit.coerceAtLeast(BAZAAR_PRICE_EPSILON)).roundToLong()
    }
    val cancel = PendingCancel(
        orderId = orderId,
        type = BazaarOrderType.BUY,
        itemName = order?.itemName ?: pending?.itemName.orEmpty(),
        productId = order?.productId ?: pending?.productId,
        amount = amount,
        refundedCoins = refundedCoins,
    )
    applyCancel(cancel)
}

internal fun cancelSellOrderFromChat(amount: Long, itemName: String) {
    val pending = pendingCancel?.takeIf {
        it.type == BazaarOrderType.SELL && (it.itemName.isBlank() || namesMatch(it.itemName, itemName))
    }
    val order = pending?.orderId?.let { id -> storage.activeOrders.firstOrNull { it.id == id } }
        ?: pendingOrderOptionId?.let { id -> storage.activeOrders.firstOrNull { it.id == id && it.type == BazaarOrderType.SELL } }
    val cancel = PendingCancel(
        orderId = order?.id ?: pending?.orderId,
        type = BazaarOrderType.SELL,
        itemName = order?.itemName ?: itemName.trim(),
        productId = order?.productId ?: pending?.productId,
        amount = amount,
        refundedCoins = null,
    )
    applyCancel(cancel)
}

private object BazaarChatGroups {
    const val SETUP_AMOUNT = 1
    const val SETUP_ITEM = 2
    const val SETUP_TOTAL = 3
    const val FLIPPED_AMOUNT = 1
    const val FLIPPED_ITEM = 2
    const val FLIPPED_PROFIT = 3
    const val CANCEL_REFUND = 1
    const val CANCEL_AMOUNT = 1
    const val CANCEL_ITEM = 2
    const val BUY_CLAIM_AMOUNT = 1
    const val BUY_CLAIM_ITEM = 2
    const val BUY_CLAIM_COINS = 3
    const val BUY_CLAIM_UNIT_PRICE = 4
    const val SELL_CLAIM_COINS = 1
    const val SELL_CLAIM_AMOUNT = 2
    const val SELL_CLAIM_ITEM = 3
    const val SELL_CLAIM_UNIT_PRICE = 4
    const val FILLED_TYPE = 1
    const val FILLED_AMOUNT = 2
    const val FILLED_ITEM = 3
}

