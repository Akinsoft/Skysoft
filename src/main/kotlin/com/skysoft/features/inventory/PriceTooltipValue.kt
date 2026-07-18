package com.skysoft.features.inventory

import com.skysoft.config.BazaarTooltipWording
import com.skysoft.config.PriceTooltipLine
import com.skysoft.data.skyblock.price.BazaarPriceData
import com.skysoft.utils.NumberUtilities.coinAmountFormat
import net.minecraft.network.chat.Component

internal data class PriceTooltipValue(
    val line: PriceTooltipLine,
    val label: String,
    val unitPrice: Double,
)

internal fun collectPriceTooltipValues(
    selectedLines: List<PriceTooltipLine>,
    wording: BazaarTooltipWording,
    bazaar: BazaarPriceData?,
    lowestBin: Double?,
    npcSellPrice: Double?,
    rawCraftCost: () -> Double?,
): List<PriceTooltipValue> = selectedLines.distinct().mapNotNull { line ->
    val price = when (line) {
        PriceTooltipLine.BAZAAR_BUY_ORDER -> bazaar?.buyOrderPrice
        PriceTooltipLine.BAZAAR_SELL_ORDER -> bazaar?.sellOrderPrice
        PriceTooltipLine.BAZAAR_INSTANT_BUY -> bazaar?.instantBuyPrice
        PriceTooltipLine.BAZAAR_INSTANT_SELL -> bazaar?.instantSellPrice
        PriceTooltipLine.LOWEST_BIN -> lowestBin
        PriceTooltipLine.NPC_SELL_PRICE -> npcSellPrice
        PriceTooltipLine.RAW_CRAFT_COST -> rawCraftCost()
    }
    price?.takeIf { it.isFinite() && it > 0.0 }?.let {
        PriceTooltipValue(line, line.tooltipLabel(wording), it)
    }
}

internal fun formatPriceTooltipValue(
    value: PriceTooltipValue,
    stackMultiplier: Int,
    labelRgb: Int,
    priceRgb: Int,
    isBold: Boolean,
): Component {
    val quantity = if (stackMultiplier > 1) " (${stackMultiplier}x)" else ""
    val totalPrice = value.unitPrice * stackMultiplier
    val label = Component.literal("${value.label}$quantity: ").withStyle { style ->
        style.withColor(labelRgb and RGB_MASK).withBold(isBold).withItalic(false)
    }
    val price = Component.literal(
        "${totalPrice.coinAmountFormat()} ${if (totalPrice == 1.0) "coin" else "coins"}",
    ).withStyle { style ->
        style.withColor(priceRgb and RGB_MASK).withBold(isBold).withItalic(false)
    }
    return label.append(price)
}

private fun PriceTooltipLine.tooltipLabel(wording: BazaarTooltipWording): String = when (this) {
    PriceTooltipLine.BAZAAR_BUY_ORDER -> when (wording) {
        BazaarTooltipWording.ORDERS -> "Bazaar Buy Order"
        BazaarTooltipWording.OFFERS -> "Bazaar Buy Offer"
    }
    PriceTooltipLine.BAZAAR_SELL_ORDER -> when (wording) {
        BazaarTooltipWording.ORDERS -> "Bazaar Sell Order"
        BazaarTooltipWording.OFFERS -> "Bazaar Sell Offer"
    }
    PriceTooltipLine.BAZAAR_INSTANT_BUY -> "Bazaar Instant Buy"
    PriceTooltipLine.BAZAAR_INSTANT_SELL -> "Bazaar Instant Sell"
    PriceTooltipLine.LOWEST_BIN -> "Lowest BIN"
    PriceTooltipLine.NPC_SELL_PRICE -> "NPC Sell Price"
    PriceTooltipLine.RAW_CRAFT_COST -> "Raw Craft Cost"
}

private const val RGB_MASK = 0xFFFFFF
