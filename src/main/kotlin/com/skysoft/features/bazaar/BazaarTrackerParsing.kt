package com.skysoft.features.bazaar

import com.skysoft.utils.RegexUtilities.whitespacePattern
import com.skysoft.data.skyblock.BazaarOrderType
import com.skysoft.data.ProfileStorage
import com.skysoft.data.ProfileStorageView
import com.skysoft.data.ProfileStorageApi
import com.skysoft.utils.NumberUtilities.CompactNumber
import net.minecraft.world.item.ItemStack
import java.util.UUID
import kotlin.math.abs
import kotlin.math.roundToLong

private fun List<String>.firstNumber(pattern: Regex): CompactNumber? =
    firstNotNullOfOrNull { line -> pattern.matchEntire(line)?.groupValues?.get(1) }?.let(::parseNumber)

internal fun parseConfirmStack(stack: ItemStack, expectedType: BazaarOrderType): PendingOrder? {
    if (stack.isEmpty) return null
    val clean = stack.cleanTextLines()
    val type = when {
        clean.any { it == "Buy Order" } -> BazaarOrderType.BUY
        clean.any { it == "Sell Offer" } -> BazaarOrderType.SELL
        else -> return null
    }
    if (type != expectedType) return null
    val priceParsed = clean.firstNumber(pricePerUnitPattern) ?: return null
    val orderLine = clean.firstNotNullOfOrNull { line ->
        when (type) {
            BazaarOrderType.BUY -> confirmBuyAmountPattern.matchEntire(line)
            BazaarOrderType.SELL -> confirmSellAmountPattern.matchEntire(line)
        }
    } ?: return null
    val amount = parseExactLong(orderLine.groupValues[CONFIRM_ORDER_AMOUNT_GROUP])
    val itemName = orderLine.groupValues[CONFIRM_ORDER_ITEM_GROUP].trim()
    val totalParsed = clean.firstNumber(
        when (type) {
            BazaarOrderType.BUY -> totalPricePattern
            BazaarOrderType.SELL -> youEarnPattern
        },
    )
    val tax = clean.firstNumber(taxPattern)?.value
    return PendingOrder(
        type = type,
        itemName = itemName,
        productId = stack.resolveBazaarOrderProductId(itemName),
        amount = amount,
        amountApproximate = false,
        amountResolution = 0.0,
        pricePerUnit = priceParsed.value,
        pricePerUnitResolution = priceParsed.resolution,
        totalCoins = totalParsed?.value,
        totalCoinsResolution = totalParsed?.resolution ?: 0.0,
        filledAmount = null,
        filledAmountApproximate = false,
        filledAmountResolution = 0.0,
        taxPercent = tax,
        guiSlot = null,
        stackSignature = ItemStack.hashItemAndComponents(stack),
    )
}

internal fun parseOrdersStack(stack: ItemStack): PendingOrder? {
    if (stack.isEmpty) return null
    val clean = stack.cleanTextLines()
    val header = clean.firstOrNull() ?: return null
    val headerMatch = orderHeaderPattern.matchEntire(header) ?: return null
    val type = if (headerMatch.groupValues[ORDERS_HEADER_TYPE_GROUP] == "BUY") BazaarOrderType.BUY else BazaarOrderType.SELL
    val itemName = headerMatch.groupValues[ORDERS_HEADER_ITEM_GROUP].trim()
    val numbers = parseOrderNumbers(clean, type)
    return PendingOrder(
        type = type,
        itemName = itemName,
        productId = stack.resolveBazaarOrderProductId(itemName),
        amount = numbers.amount,
        amountApproximate = numbers.amountParsed.approximate,
        amountResolution = numbers.amountParsed.resolution,
        pricePerUnit = numbers.pricePerUnit,
        pricePerUnitResolution = numbers.priceResolution,
        totalCoins = numbers.totalCoins,
        totalCoinsResolution = numbers.totalResolution,
        filledAmount = numbers.filled,
        filledAmountApproximate = numbers.filledParsed?.approximate ?: false,
        filledAmountResolution = numbers.filledParsed?.resolution ?: 0.0,
        taxPercent = numbers.tax,
        guiSlot = null,
        stackSignature = ItemStack.hashItemAndComponents(stack),
    )
}

private fun parseOrderNumbers(clean: List<String>, type: BazaarOrderType): ParsedOrderNumbers {
    val explicitAmountParsed = clean.firstNumber(orderAmountPattern)
    val filledMatch = clean.firstNotNullOfOrNull { line -> filledPatternGui.matchEntire(line) }
    val filledParsed = filledMatch?.groupValues?.get(1)?.let { parseNumber(it) }
    val filled = filledParsed?.value?.roundToLong()
    val filledTotalParsed = filledMatch?.groupValues?.get(FILLED_TOTAL_GROUP)?.let { parseNumber(it) }
    val amountParsed = explicitAmountParsed
        ?: filledTotalParsed
        ?: CompactNumber(0.0, approximate = true, resolution = 0.0)
    val worthParsed = clean.firstNumber(worthPattern)
    val unitPriceParsed = clean.firstNumber(pricePerUnitPattern)
    val tax = clean.firstNumber(taxPattern)?.value
    val amount = amountParsed.value.roundToLong()
    val taxMultiplier = if (type == BazaarOrderType.SELL) sellTaxMultiplier(tax ?: storage.taxPercent) else 1.0
    val pricePerUnit = unitPriceParsed?.value
        ?: if (worthParsed != null && amount > 0) worthParsed.value / amount / taxMultiplier else 0.0
    val priceResolution = unitPriceParsed?.resolution
        ?: if (worthParsed != null && amount > 0) worthParsed.resolution / amount / taxMultiplier else 0.0
    val totalCoins = worthParsed?.value
        ?: if (amount > 0 && pricePerUnit > 0.0) amount * pricePerUnit * taxMultiplier else null
    val totalResolution = worthParsed?.resolution
        ?: if (amount > 0 && priceResolution > 0.0) amount * priceResolution * taxMultiplier else 0.0
    return ParsedOrderNumbers(
        amount = amount,
        amountParsed = amountParsed,
        pricePerUnit = pricePerUnit,
        priceResolution = priceResolution,
        totalCoins = totalCoins,
        totalResolution = totalResolution,
        filled = filled,
        filledParsed = filledParsed,
        tax = tax,
    )
}

private data class ParsedOrderNumbers(
    val amount: Long,
    val amountParsed: CompactNumber,
    val pricePerUnit: Double,
    val priceResolution: Double,
    val totalCoins: Double?,
    val totalResolution: Double,
    val filled: Long?,
    val filledParsed: CompactNumber?,
    val tax: Double?,
)

internal fun parseCancelStack(stack: ItemStack, order: ProfileStorageView.BazaarOrderData?): PendingCancel? {
    if (stack.isEmpty) return null
    val clean = stack.cleanTextLines()
    if (clean.none { it.contains("Cancel Order") }) return null
    if (clean.any { it.startsWith("Cannot cancel order while", ignoreCase = true) }) return null
    val joined = clean.joinToString(" ").replace(whitespacePattern, " ")
    val buyMatch = cancelBuyTooltipPattern.find(joined)
    if (buyMatch != null) {
        return PendingCancel(
            orderId = order?.id,
            type = BazaarOrderType.BUY,
            itemName = order?.itemName.orEmpty(),
            productId = order?.productId,
            amount = parseNumber(buyMatch.groupValues[CANCEL_BUY_AMOUNT_GROUP]).value.roundToLong(),
            refundedCoins = parseNumber(buyMatch.groupValues[1]).value,
        )
    }
    val sellMatch = cancelSellTooltipPattern.find(joined)
    if (sellMatch != null) {
        val parsedItem = sellMatch.groupValues[CANCEL_SELL_ITEM_GROUP].trim()
        val itemName = order?.itemName
            ?: parsedItem.takeUnless { normalizeName(it) == "items" || normalizeName(it) == "missing items" }
                .orEmpty()
        return PendingCancel(
            orderId = order?.id,
            type = BazaarOrderType.SELL,
            itemName = itemName,
            productId = order?.productId,
            amount = parseNumber(sellMatch.groupValues[1]).value.roundToLong(),
            refundedCoins = null,
        )
    }
    return order?.let {
        PendingCancel(
            orderId = it.id,
            type = it.type,
            itemName = it.itemName,
            productId = it.productId,
            amount = it.remainingAmount(),
            refundedCoins = null,
        )
    }
}

internal fun sellTaxMultiplier(taxPercent: Double): Double =
    (1.0 - taxPercent / BAZAAR_PERCENT_SCALE).coerceAtLeast(MIN_BAZAAR_TAX_MULTIPLIER)

internal fun updateTax(taxPercent: Double?) {
    if (taxPercent == null || taxPercent <= 0.0 || abs(storage.taxPercent - taxPercent) < BAZAAR_PRICE_EPSILON) return
    ProfileStorageApi.updateProfile { it.bazaarTracker.taxPercent = taxPercent }
}

private const val CONFIRM_ORDER_AMOUNT_GROUP = 1
private const val CONFIRM_ORDER_ITEM_GROUP = 2
private const val ORDERS_HEADER_TYPE_GROUP = 1
private const val ORDERS_HEADER_ITEM_GROUP = 2
private const val FILLED_TOTAL_GROUP = 2
private const val CANCEL_BUY_AMOUNT_GROUP = 2
private const val CANCEL_SELL_ITEM_GROUP = 2

internal fun PendingOrder.toOrderData(): ProfileStorage.BazaarOrderData =
    ProfileStorage.BazaarOrderData(
        id = UUID.randomUUID().toString(),
        type = type,
        itemName = itemName,
        productId = productId,
        amountOrdered = amount,
        pricePerUnit = pricePerUnit,
        totalCoins = totalCoins ?: (amount * pricePerUnit),
        filledAmount = filledAmount ?: 0L,
        claimedAmount = 0L,
        claimedCoins = 0.0,
        createdAtMillis = System.currentTimeMillis(),
        updatedAtMillis = System.currentTimeMillis(),
        lastGuiSlot = guiSlot ?: -1,
        amountResolution = amountResolution,
        pricePerUnitResolution = pricePerUnitResolution,
        totalCoinsResolution = totalCoinsResolution,
        setupConfirmed = false,
    )

internal data class PendingOrder(
    val type: BazaarOrderType,
    val itemName: String,
    val productId: String?,
    val amount: Long,
    val amountApproximate: Boolean,
    val amountResolution: Double,
    val pricePerUnit: Double,
    val pricePerUnitResolution: Double,
    val totalCoins: Double?,
    val totalCoinsResolution: Double,
    val filledAmount: Long?,
    val filledAmountApproximate: Boolean,
    val filledAmountResolution: Double,
    val taxPercent: Double?,
    val guiSlot: Int?,
    val stackSignature: Int?,
)

internal data class PendingCancel(
    val orderId: String?,
    val type: BazaarOrderType,
    val itemName: String,
    val productId: String?,
    val amount: Long?,
    val refundedCoins: Double?,
)

private val pricePerUnitPattern = Regex("""^Price per unit: ([\d,.]+[kKmMbB]?) coins$""")
private val confirmBuyAmountPattern = Regex("""^Order: ([\d,]+)x (.+)$""")
private val confirmSellAmountPattern = Regex("""^Selling: ([\d,]+)x (.+)$""")
private val totalPricePattern = Regex("""^Total price: ([\d,.]+) coins$""")
private val youEarnPattern = Regex("""^You earn: ([\d,.]+) coins$""")
private val taxPattern = Regex("""^Current tax: ([\d,.]+)%$""")
private val orderHeaderPattern = Regex("""^(BUY|SELL) (.+)$""")
private val orderAmountPattern = Regex("""^(?:Order|Offer) amount: ([\d,.]+[kKmMbB]?)x$""")
private val worthPattern = Regex("""^Worth:? ([\d,.]+[kKmMbB]?) coins$""")
private val filledPatternGui = Regex("""^Filled: ([\d,.]+[kKmMbB]?)/([\d,.]+[kKmMbB]?).*$""")
private val cancelBuyTooltipPattern =
    Regex(
        """refunded ([\d,.]+) coins from ([\d,.]+[kKmMbB]?)x missing items""",
        RegexOption.IGNORE_CASE,
    )

private val cancelSellTooltipPattern = Regex("""refunded ([\d,.]+[kKmMbB]?)x (.+?)(?: from|\.|$)""", RegexOption.IGNORE_CASE)
private const val BAZAAR_PERCENT_SCALE = 100.0
private const val MIN_BAZAAR_TAX_MULTIPLIER = 0.01
