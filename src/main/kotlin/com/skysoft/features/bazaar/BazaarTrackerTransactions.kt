package com.skysoft.features.bazaar

import com.skysoft.data.skyblock.BazaarOrderType
import com.skysoft.data.ProfileStorage
import com.skysoft.data.ProfileStorageView
import com.skysoft.data.skyblock.instantBuyPattern
import com.skysoft.data.skyblock.instantSellPattern

internal fun ProfileStorage.BazaarTrackerData.tryHandleInstantTransactionMessage(message: String): Boolean {
    val transaction = parseInstantBazaarTransaction(message, System.currentTimeMillis()) ?: return false
    recordTransaction(transaction)
    return true
}

internal fun bazaarTransactionsFor(
    data: ProfileStorageView.BazaarTrackerData,
    productId: String,
    itemName: String,
    sinceMillis: Long,
): List<ProfileStorageView.BazaarTransactionData> {
    val recorded = data.transactions.asSequence()
        .filter { it.atMillis >= sinceMillis }
        .filter { entryMatches(it.productId, it.itemName, productId, itemName) }
    val active = data.activeOrders.asSequence()
        .filter { it.createdAtMillis >= sinceMillis }
        .filter { entryMatches(it.productId, it.itemName, productId, itemName) }
        .map(ProfileStorageView.BazaarOrderData::toBazaarTransaction)
    return (active + recorded)
        .distinctBy(::transactionIdentity)
        .sortedBy(ProfileStorageView.BazaarTransactionData::atMillis)
        .toList()
}

private fun parseInstantBazaarTransaction(message: String, atMillis: Long): ProfileStorage.BazaarTransactionData? {
    val (type, match) = instantBuyPattern.matchEntire(message)?.let {
        ProfileStorage.BazaarTransactionType.INSTANT_BUY to it
    } ?: instantSellPattern.matchEntire(message)?.let {
        ProfileStorage.BazaarTransactionType.INSTANT_SELL to it
    } ?: return null
    val itemName = match.groupValues[INSTANT_ITEM_GROUP].trim()
    return ProfileStorage.BazaarTransactionData(
        type = type,
        itemName = itemName,
        productId = resolveProductId(itemName),
        amount = parseExactLong(match.groupValues[INSTANT_AMOUNT_GROUP]),
        totalCoins = parseNumber(match.groupValues[INSTANT_TOTAL_GROUP]).value,
        atMillis = atMillis,
    )
}

internal fun ProfileStorageView.BazaarOrderData.toBazaarTransaction() = ProfileStorage.BazaarTransactionData(
    type = when (type) {
        BazaarOrderType.BUY -> ProfileStorage.BazaarTransactionType.BUY_ORDER
        BazaarOrderType.SELL -> ProfileStorage.BazaarTransactionType.SELL_ORDER
    },
    itemName = itemName,
    productId = productId,
    amount = amountOrdered,
    totalCoins = totalCoins,
    atMillis = createdAtMillis,
)

private fun entryMatches(entryProductId: String?, entryItemName: String, productId: String, itemName: String): Boolean =
    entryProductId?.equals(productId, ignoreCase = true) == true ||
        entryProductId == null && namesMatch(entryItemName, itemName)

private fun transactionIdentity(transaction: ProfileStorageView.BazaarTransactionData): String = listOf(
    transaction.type.name,
    transaction.productId.orEmpty().uppercase(),
    transaction.itemName.lowercase(),
    transaction.amount.toString(),
    transaction.atMillis.toString(),
).joinToString("|")

private const val INSTANT_AMOUNT_GROUP = 1
private const val INSTANT_ITEM_GROUP = 2
private const val INSTANT_TOTAL_GROUP = 3
