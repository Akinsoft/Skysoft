package com.skysoft.features.profit

import com.skysoft.data.skyblock.AttributeShardTransfers
import com.skysoft.data.skyblock.AttributeShardTransferDirection
import com.skysoft.data.skyblock.SkyBlockBazaarTransferDirection
import com.skysoft.data.skyblock.SkyBlockBazaarTransfers
import com.skysoft.data.skyblock.SkyBlockDroppedItems
import com.skysoft.data.skyblock.SkyBlockItemChangeBatch
import com.skysoft.data.skyblock.SkyBlockItemChanges
import com.skysoft.data.skyblock.SkyBlockItemNames
import com.skysoft.data.skyblock.SkyBlockSackTransfers
import com.skysoft.data.skyblock.SkyBlockSupercrafts
import com.skysoft.utils.chat.ChatEvents
import com.skysoft.utils.chat.ChatMessageVisibility
import kotlin.math.min

private fun parseNpcSale(message: String): ParsedItemAmount? {
    val match = NPC_SALE_PATTERN.matchEntire(message) ?: return null
    val displayName = match.groups["item"]?.value?.trim().orEmpty()
    val amount = match.groups["amount"]?.value?.replace(",", "")?.toIntOrNull() ?: return null
    return ParsedItemAmount(displayName, amount).takeIf { displayName.isNotEmpty() && amount > 0 }
}

internal class ProfitTrackerItemTracking {
    private val suppressions = ProfitItemSuppressions(keepLatestExpiry = true)
    private val removalSuppressions = ProfitItemSuppressions(keepLatestExpiry = false)

    fun register(isActive: () -> Boolean, listener: (SkyBlockItemChangeBatch) -> Unit) {
        SkyBlockDroppedItems.onDrop("Profit Tracker dropped items", isActive) { drop ->
            suppressions.add(drop.itemId, drop.amount, DROPPED_ITEM_SUPPRESSION_MILLIS)
            removalSuppressions.add(drop.itemId, drop.amount, DROPPED_ITEM_SUPPRESSION_MILLIS)
        }
        AttributeShardTransfers.onTransfer("Profit Tracker Hunting Box transfers", isActive) { transfer ->
            if (transfer.direction == AttributeShardTransferDirection.FROM_BOX) {
                suppressions.add(transfer.itemId, transfer.amount, HUNTING_BOX_REMOVAL_SUPPRESSION_MILLIS)
            }
        }
        SkyBlockSackTransfers.onTransfer("Profit Tracker Sack transfers", isActive) { transfer ->
            suppressions.add(transfer.itemId, transfer.amount, SACK_TRANSFER_SUPPRESSION_MILLIS)
            removalSuppressions.add(transfer.itemId, transfer.amount, SACK_TRANSFER_SUPPRESSION_MILLIS)
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
        SkyBlockSupercrafts.onCraft("Profit Tracker Supercraft", isActive) { crafted ->
            SkyBlockItemNames.itemId(crafted.displayName)?.let { itemId ->
                suppressions.add(itemId, crafted.amount)
            }
        }
    }

    fun consume(batch: SkyBlockItemChangeBatch): Map<String, Int> {
        val grossRemovals = (batch.changes.keys + batch.grossGains.keys).associateWith { itemId ->
            (batch.grossGains.getOrDefault(itemId, 0) - batch.changes.getOrDefault(itemId, 0)).coerceAtLeast(0)
        }
        val withoutRemovals = removalSuppressions.consume(batch.changes, grossRemovals) { amount, _, suppressed ->
            amount + suppressed
        }
        return suppressions.consume(withoutRemovals, batch.grossGains) { amount, grossGain, suppressed ->
            amount - (suppressed - (grossGain - amount).coerceAtLeast(0)).coerceAtLeast(0)
        }
    }

    fun suppressGain(itemId: String, amount: Int) = suppressions.add(itemId, amount)

    fun clear() {
        suppressions.clear()
        removalSuppressions.clear()
    }
}

private class ProfitItemSuppressions(private val keepLatestExpiry: Boolean) {
    private val suppressions = mutableMapOf<String, Suppression>()

    fun add(itemId: String, amount: Int, lifetimeMillis: Long = SUPERCRAFT_SUPPRESSION_MILLIS) {
        if (amount <= 0) return
        val now = System.currentTimeMillis()
        val current = suppressions[itemId]?.takeIf { it.expiresAtMillis > now }
        val expiresAtMillis = now + lifetimeMillis
        suppressions[itemId] = Suppression(
            amount = (current?.amount ?: 0) + amount,
            expiresAtMillis = if (keepLatestExpiry) maxOf(current?.expiresAtMillis ?: 0L, expiresAtMillis) else expiresAtMillis,
        )
    }

    fun consume(
        changes: Map<String, Int>,
        grossAmounts: Map<String, Int>,
        remainingAmount: (amount: Int, grossAmount: Int, suppressed: Int) -> Int,
    ): Map<String, Int> = buildMap {
        val now = System.currentTimeMillis()
        suppressions.entries.removeIf { (_, suppression) -> suppression.expiresAtMillis <= now }
        (changes.keys + grossAmounts.keys).forEach { itemId ->
            val grossAmount = grossAmounts[itemId] ?: 0
            val remaining = remainingAmount(changes[itemId] ?: 0, grossAmount, take(itemId, grossAmount))
            if (remaining != 0) put(itemId, remaining)
        }
    }

    fun clear() = suppressions.clear()

    private fun take(itemId: String, limit: Int): Int {
        val suppression = suppressions[itemId] ?: return 0
        val taken = min(limit, suppression.amount)
        if (taken <= 0) return 0
        val pending = suppression.amount - taken
        if (pending == 0) suppressions.remove(itemId) else suppressions[itemId] = suppression.copy(amount = pending)
        return taken
    }

    private data class Suppression(val amount: Int, val expiresAtMillis: Long)
}

private val NPC_SALE_PATTERN = Regex("^You sold (?<item>.+) x(?<amount>[\\d,]+) for [\\d,]+ Coins!$")
private const val SUPERCRAFT_SUPPRESSION_MILLIS = 60_000L
private const val DROPPED_ITEM_SUPPRESSION_MILLIS = 10_000L
private const val SACK_TRANSFER_SUPPRESSION_MILLIS = 60_000L
private const val HUNTING_BOX_REMOVAL_SUPPRESSION_MILLIS = 5_000L
