package com.skysoft.features.profit

import com.skysoft.data.skyblock.AttributeShardTransfers
import com.skysoft.data.skyblock.AttributeShardTransferDirection
import com.skysoft.data.skyblock.SkyBlockDroppedItems
import com.skysoft.data.skyblock.SkyBlockItemChangeBatch
import com.skysoft.data.skyblock.SkyBlockItemChanges
import com.skysoft.data.skyblock.SkyBlockItemNames
import com.skysoft.data.skyblock.SkyBlockSackTransferDirection
import com.skysoft.data.skyblock.SkyBlockSackTransfers
import com.skysoft.utils.chat.ChatEvents
import com.skysoft.utils.chat.ChatMessageVisibility
import kotlin.math.min

internal data class SupercraftResult(
    val displayName: String,
    val amount: Int,
)

internal fun parseSupercraftResult(message: String): SupercraftResult? {
    val match = SUPERCRAFT_PATTERN.matchEntire(message) ?: return null
    val displayName = match.groups["item"]?.value?.trim().orEmpty()
    val amount = match.groups["amount"]?.value?.replace(",", "")?.toIntOrNull() ?: 1
    return SupercraftResult(displayName, amount).takeIf { displayName.isNotEmpty() && amount > 0 }
}

internal class ProfitTrackerItemTracking {
    private val suppressions = ProfitItemSuppressions()

    fun register(isActive: () -> Boolean, listener: (SkyBlockItemChangeBatch) -> Unit) {
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
        SkyBlockItemChanges.onChange("Profit Tracker item changes", isActive, listener)
        ChatEvents.onVisibleMessage("Profit Tracker Supercraft", isActive) { message ->
            parseSupercraftResult(message.cleanText)?.let { crafted ->
                SkyBlockItemNames.itemId(crafted.displayName)?.let { itemId ->
                    suppressions.add(itemId, crafted.amount)
                }
            }
            ChatMessageVisibility.SHOW
        }
    }

    fun consume(batch: SkyBlockItemChangeBatch): Map<String, Int> =
        suppressions.consume(batch.changes, batch.grossGains)

    fun clear() = suppressions.clear()
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
private const val SUPERCRAFT_SUPPRESSION_MILLIS = 60_000L
private const val DROPPED_ITEM_SUPPRESSION_MILLIS = 10_000L
private const val SACK_INSERTION_SUPPRESSION_MILLIS = 60_000L
private const val SACK_WITHDRAWAL_SUPPRESSION_MILLIS = 5_000L
private const val HUNTING_BOX_REMOVAL_SUPPRESSION_MILLIS = 5_000L
