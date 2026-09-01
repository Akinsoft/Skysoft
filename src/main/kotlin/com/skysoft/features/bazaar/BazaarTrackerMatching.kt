package com.skysoft.features.bazaar

import com.skysoft.data.skyblock.pets.PetRepository
import com.skysoft.data.ProfileStorage
import com.skysoft.data.skyblock.SkyBlockItemNames
import com.skysoft.data.skyblock.SkyBlockItemUtilities.formattedHoverName
import com.skysoft.data.skyblock.SkyBlockItemUtilities.loreLines
import com.skysoft.utils.TextUtilities.cleanSkyBlockText
import net.minecraft.world.item.ItemStack
import java.util.Locale
import kotlin.math.abs

internal fun ItemStack.textLines(): List<String> = buildList {
    add(formattedHoverName())
    addAll(loreLines())
}

internal fun String.clean(): String = cleanSkyBlockText()

internal fun namesMatch(a: String, b: String): Boolean = normalizeName(a) == normalizeName(b)

internal fun canonicalBazaarProductId(productId: String): String = productId.replace(':', '-')

internal fun productMatches(a: String?, b: String?): Boolean =
    a != null && b != null && canonicalBazaarProductId(a) == canonicalBazaarProductId(b)

internal fun lotMatches(lot: ProfileStorage.BazaarItemLotData, productId: String?, itemName: String): Boolean =
    productMatches(lot.productId, productId) || namesMatch(lot.itemName, itemName)

internal fun resolveProductId(itemName: String): String? =
    SkyBlockItemNames.itemId(itemName.clean()) ?: PetRepository.resolvePetItemOrNull(itemName)

internal fun normalizeName(name: String): String = name.clean().lowercase(Locale.US).replace(Regex("\\s+"), " ")

internal fun orderMatchesParsedIdentity(order: ProfileStorage.BazaarOrderData, parsed: PendingOrder): Boolean {
    if (
        parsed.amount > 0 &&
        !haveOverlappingRanges(
            order.amountOrdered.toDouble(),
            order.amountResolution,
            parsed.amount.toDouble(),
            parsed.amountResolution,
            EXACT_AMOUNT_EPSILON,
        )
    ) {
        return false
    }
    if (
        parsed.pricePerUnit > 0.0 &&
        order.pricePerUnit > 0.0 &&
        !haveOverlappingRanges(
            order.pricePerUnit,
            order.pricePerUnitResolution,
            parsed.pricePerUnit,
            parsed.pricePerUnitResolution,
            BAZAAR_PRICE_EPSILON,
        )
    ) {
        return false
    }
    return parsed.amount > 0 || (parsed.filledAmount ?: 0L) > 0
}

internal fun PendingOrder.canCreateOrderFromGui(): Boolean {
    if (amount <= 0 || pricePerUnit <= 0.0) return false
    val filled = filledAmount ?: return true
    return filled < amount + amountResolution.coerceAtLeast(1.0)
}

internal fun amountDistance(a: Long, b: Long): Long = abs(a - b)

