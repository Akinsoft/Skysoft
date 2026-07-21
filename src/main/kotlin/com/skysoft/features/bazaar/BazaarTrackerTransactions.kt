package com.skysoft.features.bazaar

import com.skysoft.data.ProfileStorage
import com.skysoft.data.skyblock.instantBuyPattern
import com.skysoft.data.skyblock.instantSellPattern

internal fun tryHandleInstantTransactionMessage(message: String): Boolean {
    val transaction = parseInstantBazaarTransaction(message, System.currentTimeMillis()) ?: return false
    recordBazaarTransaction(storage, transaction)
    markBazaarTrackerChanged()
    return true
}

internal fun recordBazaarTransaction(
    data: ProfileStorage.BazaarTrackerData,
    transaction: ProfileStorage.BazaarTransactionData,
) {
    require(transaction.isUsable()) { "Bazaar transaction must contain an item, amount, total, and timestamp" }
    data.transactions += transaction
    data.transactions.sortByDescending(ProfileStorage.BazaarTransactionData::atMillis)
    if (data.transactions.size > ProfileStorage.BazaarTrackerData.MAX_TRANSACTIONS) {
        data.transactions.subList(ProfileStorage.BazaarTrackerData.MAX_TRANSACTIONS, data.transactions.size).clear()
    }
}

internal fun bazaarTransactionsFor(
    data: ProfileStorage.BazaarTrackerData,
    productId: String,
    itemName: String,
    sinceMillis: Long,
): List<ProfileStorage.BazaarTransactionData> {
    val recorded = data.transactions.asSequence()
        .filter { it.atMillis >= sinceMillis }
        .filter { transactionMatches(it, productId, itemName) }
    val active = data.activeOrders.asSequence()
        .filter { it.createdAtMillis >= sinceMillis }
        .filter { orderMatches(it, productId, itemName) }
        .map(ProfileStorage.BazaarOrderData::toBazaarTransaction)
    return (active + recorded)
        .distinctBy(::transactionIdentity)
        .sortedBy(ProfileStorage.BazaarTransactionData::atMillis)
        .toList()
}

internal fun parseInstantBazaarTransaction(
    message: String,
    atMillis: Long,
    productResolver: (String) -> String? = ::resolveProductId,
): ProfileStorage.BazaarTransactionData? {
    val (type, match) = instantBuyPattern.matchEntire(message)?.let {
        ProfileStorage.BazaarTransactionType.INSTANT_BUY to it
    } ?: instantSellPattern.matchEntire(message)?.let {
        ProfileStorage.BazaarTransactionType.INSTANT_SELL to it
    } ?: return null
    val itemName = match.groupValues[INSTANT_ITEM_GROUP].trim()
    return ProfileStorage.BazaarTransactionData(
        type = type,
        itemName = itemName,
        productId = productResolver(itemName),
        amount = parseExactLong(match.groupValues[INSTANT_AMOUNT_GROUP]),
        totalCoins = parseNumber(match.groupValues[INSTANT_TOTAL_GROUP]).value,
        atMillis = atMillis,
    )
}

internal fun ProfileStorage.BazaarOrderData.toBazaarTransaction() = ProfileStorage.BazaarTransactionData(
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

private fun transactionMatches(
    transaction: ProfileStorage.BazaarTransactionData,
    productId: String,
    itemName: String,
): Boolean = transaction.productId?.equals(productId, ignoreCase = true) == true ||
    transaction.productId == null && namesMatch(transaction.itemName, itemName)

private fun orderMatches(order: ProfileStorage.BazaarOrderData, productId: String, itemName: String): Boolean =
    order.productId?.equals(productId, ignoreCase = true) == true || order.productId == null && namesMatch(order.itemName, itemName)

private fun transactionIdentity(transaction: ProfileStorage.BazaarTransactionData): String = listOf(
    transaction.type.name,
    transaction.productId.orEmpty().uppercase(),
    transaction.itemName.lowercase(),
    transaction.amount.toString(),
    transaction.atMillis.toString(),
).joinToString("|")

private const val INSTANT_AMOUNT_GROUP = 1
private const val INSTANT_ITEM_GROUP = 2
private const val INSTANT_TOTAL_GROUP = 3
