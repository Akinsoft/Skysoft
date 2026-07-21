package com.skysoft.features.profit

import com.skysoft.data.skyblock.AttributeShardTransfers
import com.skysoft.data.skyblock.AttributeShardTransferDirection
import com.skysoft.data.skyblock.SkyBlockBazaarTransferDirection
import com.skysoft.data.skyblock.SkyBlockBazaarTransfers
import com.skysoft.data.skyblock.SkyBlockDroppedItems
import com.skysoft.data.skyblock.SkyBlockItemChangeBatch
import com.skysoft.data.skyblock.SkyBlockItemChanges
import com.skysoft.data.skyblock.SkyBlockItemNames
import com.skysoft.data.skyblock.SkyBlockSackTransferDirection
import com.skysoft.data.skyblock.SkyBlockSackTransfers
import com.skysoft.utils.chat.ChatEvents
import com.skysoft.utils.chat.ChatMessageVisibility
import kotlin.math.min

internal data class ParsedItemAmount(
    val displayName: String,
    val amount: Int,
)

internal fun parseSupercraftResult(message: String): ParsedItemAmount? {
    val match = SUPERCRAFT_PATTERN.matchEntire(message) ?: return null
    val displayName = match.groups["item"]?.value?.trim().orEmpty()
    val amount = match.groups["amount"]?.value?.replace(",", "")?.toIntOrNull() ?: 1
    return ParsedItemAmount(displayName, amount).takeIf { displayName.isNotEmpty() && amount > 0 }
}

internal fun parseNpcSale(message: String): ParsedItemAmount? {
    val match = NPC_SALE_PATTERN.matchEntire(message) ?: return null
    val displayName = match.groups["item"]?.value?.trim().orEmpty()
    val amount = match.groups["amount"]?.value?.replace(",", "")?.toIntOrNull() ?: return null
    return ParsedItemAmount(displayName, amount).takeIf { displayName.isNotEmpty() && amount > 0 }
}

internal class ProfitTrackerItemTracking {
    private val suppressions = ProfitItemSuppressions()
    private val removalSuppressions = ProfitItemRemovalSuppressions()

    fun register(
        isActive: () -> Boolean,
        shouldSuppressSupercraft: () -> Boolean,
        listener: (SkyBlockItemChangeBatch) -> Unit,
    ) {
        SkyBlockDroppedItems.onDrop("Profit Tracker dropped items", isActive) { drop ->
            suppressions.add(drop.itemId, drop.amount, DROPPED_ITEM_SUPPRESSION_MILLIS)
        }
        AttributeShardTransfers.onTransfer("Profit Tracker Hunting Box transfers", isActive) { transfer ->
            if (transfer.direction == AttributeShardTransferDirection.FROM_BOX) {
                suppressions.add(transfer.itemId, transfer.amount, HUNTING_BOX_REMOVAL_SUPPRESSION_MILLIS)
            }
        }
        SkyBlockSackTransfers.onTransfer("Profit Tracker Sack transfers", isActive) { transfer ->
            val lifetime = if (transfer.direction == SkyBlockSackTransferDirection.TO_SACKS) {
                SACK_INSERTION_SUPPRESSION_MILLIS
            } else {
                SACK_WITHDRAWAL_SUPPRESSION_MILLIS
            }
            suppressions.add(transfer.itemId, transfer.amount, lifetime)
        }
        SkyBlockBazaarTransfers.onTransfer("Profit Tracker Bazaar transfers", isActive) { transfer ->
            SkyBlockItemNames.itemId(transfer.displayName)?.let { itemId ->
                if (transfer.direction == SkyBlockBazaarTransferDirection.TO_PLAYER) {
                    suppressions.add(itemId, transfer.amount)
                } else {
                    removalSuppressions.add(itemId, transfer.amount)
                }
            }
        }
        SkyBlockItemChanges.onChange("Profit Tracker item changes", isActive, listener)
        ChatEvents.onVisibleMessage("Profit Tracker NPC sales", isActive) { message ->
            parseNpcSale(message.cleanText)?.let { sale ->
                SkyBlockItemNames.itemId(sale.displayName)?.let { itemId ->
                    removalSuppressions.add(itemId, sale.amount)
                }
            }
            ChatMessageVisibility.SHOW
        }
        ChatEvents.onVisibleMessage("Profit Tracker Supercraft", { isActive() && shouldSuppressSupercraft() }) { message ->
            parseSupercraftResult(message.cleanText)?.let { crafted ->
                SkyBlockItemNames.itemId(crafted.displayName)?.let { itemId ->
                    suppressions.add(itemId, crafted.amount)
                }
            }
            ChatMessageVisibility.SHOW
        }
    }

    fun consume(batch: SkyBlockItemChangeBatch): Map<String, Int> =
        removalSuppressions.consume(suppressions.consume(batch.changes, batch.grossGains))

    fun suppressGain(itemId: String, amount: Int) = suppressions.add(itemId, amount)

    fun clear() {
        suppressions.clear()
        removalSuppressions.clear()
    }
}

internal class ProfitItemRemovalSuppressions(
    private val currentTimeMillis: () -> Long = System::currentTimeMillis,
) {
    private val suppressions = mutableMapOf<String, RemovalSuppression>()

    fun add(itemId: String, amount: Int) {
        if (amount <= 0) return
        val now = currentTimeMillis()
        val current = suppressions[itemId]?.takeIf { it.expiresAtMillis > now }
        suppressions[itemId] = RemovalSuppression(
            amount = (current?.amount ?: 0) + amount,
            expiresAtMillis = now + SUPERCRAFT_SUPPRESSION_MILLIS,
        )
    }

    fun consume(changes: Map<String, Int>): Map<String, Int> = buildMap {
        val now = currentTimeMillis()
        suppressions.entries.removeIf { (_, suppression) -> suppression.expiresAtMillis <= now }
        changes.forEach { (itemId, amount) ->
            val suppression = suppressions[itemId]
            val suppressed = minOf((-amount).coerceAtLeast(0), suppression?.amount ?: 0)
            val remaining = amount + suppressed
            if (remaining != 0) put(itemId, remaining)
            if (suppression != null && suppressed > 0) {
                val pending = suppression.amount - suppressed
                if (pending == 0) suppressions.remove(itemId) else {
                    suppressions[itemId] = suppression.copy(amount = pending)
                }
            }
        }
    }

    fun clear() = suppressions.clear()

    private data class RemovalSuppression(val amount: Int, val expiresAtMillis: Long)
}

internal class ProfitItemSuppressions(
    private val currentTimeMillis: () -> Long = System::currentTimeMillis,
) {
    private val suppressions = mutableMapOf<String, Suppression>()

    fun add(itemId: String, amount: Int, lifetimeMillis: Long = SUPERCRAFT_SUPPRESSION_MILLIS) {
        if (amount <= 0) return
        val current = suppressions[itemId]?.takeIf { it.expiresAtMillis > currentTimeMillis() }
        suppressions[itemId] = Suppression(
            amount = (current?.amount ?: 0) + amount,
            expiresAtMillis = maxOf(current?.expiresAtMillis ?: 0L, currentTimeMillis() + lifetimeMillis),
        )
    }

    fun consume(
        changes: Map<String, Int>,
        grossGains: Map<String, Int> = changes.filterValues { it > 0 },
    ): Map<String, Int> = buildMap {
        val now = currentTimeMillis()
        suppressions.entries.removeIf { (_, suppression) -> suppression.expiresAtMillis <= now }
        (changes.keys + grossGains.keys).forEach { itemId ->
            val amount = changes[itemId] ?: 0
            val grossGain = grossGains[itemId] ?: 0
            val suppression = suppressions[itemId]
            if (grossGain <= 0 || suppression == null) {
                if (amount != 0) put(itemId, amount)
                return@forEach
            }
            val consumed = min(grossGain, suppression.amount)
            val remainingSuppression = suppression.amount - consumed
            if (remainingSuppression == 0) {
                suppressions.remove(itemId)
            } else {
                suppressions[itemId] = suppression.copy(amount = remainingSuppression)
            }
            val grossRemoval = (grossGain - amount).coerceAtLeast(0)
            val remainingAmount = amount - (consumed - grossRemoval).coerceAtLeast(0)
            if (remainingAmount != 0) put(itemId, remainingAmount)
        }
    }

    fun clear() = suppressions.clear()

    private data class Suppression(
        val amount: Int,
        val expiresAtMillis: Long,
    )
}

private val SUPERCRAFT_PATTERN = Regex("^You Supercrafted (?<item>.+?)(?: x(?<amount>[\\d,]+))?!$")
private val NPC_SALE_PATTERN = Regex("^You sold (?<item>.+) x(?<amount>[\\d,]+) for [\\d,]+ Coins!$")
private const val SUPERCRAFT_SUPPRESSION_MILLIS = 60_000L
private const val DROPPED_ITEM_SUPPRESSION_MILLIS = 10_000L
private const val SACK_INSERTION_SUPPRESSION_MILLIS = 60_000L
private const val SACK_WITHDRAWAL_SUPPRESSION_MILLIS = 5_000L
private const val HUNTING_BOX_REMOVAL_SUPPRESSION_MILLIS = 5_000L
