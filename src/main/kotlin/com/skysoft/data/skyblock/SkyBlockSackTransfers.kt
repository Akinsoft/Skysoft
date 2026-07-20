package com.skysoft.data.skyblock

import com.skysoft.data.hypixel.HypixelLocationState
import com.skysoft.utils.SkysoftClientEvents
import com.skysoft.utils.SkysoftErrorBoundary
import com.skysoft.utils.chat.ChatEvents
import com.skysoft.utils.chat.ChatMessageVisibility

object SkyBlockSackTransfers {
    private var listeners: List<Listener> = emptyList()
    private var insertInventoryUntilMillis = 0L

    fun register() {
        SkyBlockInventoryChanges.onChange(
            "SkyBlock Sack transfers",
            isActive = ::hasActiveListeners,
        ) { change ->
            if (System.currentTimeMillis() > insertInventoryUntilMillis) return@onChange
            val removals = change.changes.filterValues { amount -> amount < 0 }
            removals.forEach { (itemId, amount) ->
                dispatch(SkyBlockSackTransfer(itemId, -amount, SkyBlockSackTransferDirection.TO_SACKS))
            }
            if (removals.isNotEmpty()) insertInventoryUntilMillis = 0L
        }
        ChatEvents.onVisibleMessage("SkyBlock Sack withdrawals", ::hasActiveListeners) { message ->
            parseSackWithdrawal(message.cleanText)?.let { withdrawal ->
                SkyBlockItemNames.itemId(withdrawal.displayName)?.let { itemId ->
                    dispatch(SkyBlockSackTransfer(itemId, withdrawal.amount, SkyBlockSackTransferDirection.FROM_SACKS))
                }
            }
            ChatMessageVisibility.SHOW
        }
        SkysoftClientEvents.onDisconnect("SkyBlock Sack transfers reset") { insertInventoryUntilMillis = 0L }
    }

    fun recordInsertInventory() {
        if (!HypixelLocationState.inSkyBlock || !hasActiveListeners()) return
        insertInventoryUntilMillis = System.currentTimeMillis() + INSERT_INVENTORY_MILLIS
    }

    fun onTransfer(boundary: String, isActive: () -> Boolean, listener: (SkyBlockSackTransfer) -> Unit) {
        listeners += Listener(boundary, isActive, listener)
    }

    private fun dispatch(transfer: SkyBlockSackTransfer) {
        listeners.forEach { registered ->
            if (registered.isActive()) {
                SkysoftErrorBoundary.run(registered.boundary) { registered.listener(transfer) }
            }
        }
    }

    private fun hasActiveListeners(): Boolean = listeners.any { it.isActive() }

    private data class Listener(
        val boundary: String,
        val isActive: () -> Boolean,
        val listener: (SkyBlockSackTransfer) -> Unit,
    )
}

data class SkyBlockSackTransfer(
    val itemId: String,
    val amount: Int,
    val direction: SkyBlockSackTransferDirection,
)

enum class SkyBlockSackTransferDirection {
    TO_SACKS,
    FROM_SACKS,
}

internal data class SkyBlockSackWithdrawal(
    val displayName: String,
    val amount: Int,
)

internal fun parseSackWithdrawal(message: String): SkyBlockSackWithdrawal? {
    val match = SACK_WITHDRAWAL_PATTERN.matchEntire(message) ?: return null
    val displayName = match.groups["item"]?.value?.trim().orEmpty()
    val amount = match.groups["amount"]?.value?.replace(",", "")?.toIntOrNull() ?: return null
    return SkyBlockSackWithdrawal(displayName, amount).takeIf { displayName.isNotEmpty() && amount > 0 }
}

private val SACK_WITHDRAWAL_PATTERN =
    Regex("^Moved (?<amount>[\\d,]+) (?<item>.+) from your Sacks to your inventory\\.$")
private const val INSERT_INVENTORY_MILLIS = 3_000L
