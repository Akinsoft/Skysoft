package com.skysoft.data.skyblock

import com.skysoft.data.hypixel.HypixelLocationState
import com.skysoft.utils.ActiveListenerRegistry
import com.skysoft.utils.ElapsedTimeMark
import com.skysoft.utils.SkysoftClientEvents
import com.skysoft.utils.chat.ChatEvents
import com.skysoft.utils.chat.ChatMessageVisibility
import kotlin.time.Duration.Companion.seconds

object SkyBlockSackTransfers {
    private val listeners = ActiveListenerRegistry<(SkyBlockSackTransfer) -> Unit>()
    private var insertInventoryStarted = ElapsedTimeMark.farPast()

    fun register() {
        SkyBlockInventoryChanges.onChange(
            "SkyBlock Sack transfers",
            isActive = ::hasActiveListeners,
        ) { change ->
            if (insertInventoryStarted.passedSince() > INSERT_INVENTORY_WINDOW) return@onChange
            val removals = change.changes.filterValues { amount -> amount < 0 }
            removals.forEach { (itemId, amount) ->
                dispatch(SkyBlockSackTransfer(itemId, -amount, SkyBlockSackTransferDirection.TO_SACKS))
            }
            if (removals.isNotEmpty()) insertInventoryStarted = ElapsedTimeMark.farPast()
        }
        ChatEvents.onVisibleMessage("SkyBlock Sack withdrawals", ::hasActiveListeners) { message ->
            parseSackWithdrawal(message.cleanText)?.let { withdrawal ->
                SkyBlockItemNames.itemId(withdrawal.displayName)?.let { itemId ->
                    dispatch(SkyBlockSackTransfer(itemId, withdrawal.amount, SkyBlockSackTransferDirection.FROM_SACKS))
                }
            }
            ChatMessageVisibility.SHOW
        }
        SkysoftClientEvents.onDisconnect("SkyBlock Sack transfers reset") { insertInventoryStarted = ElapsedTimeMark.farPast() }
    }

    fun recordInsertInventory() {
        if (!HypixelLocationState.inSkyBlock || !hasActiveListeners()) return
        insertInventoryStarted = ElapsedTimeMark.now()
    }

    fun onTransfer(boundary: String, isActive: () -> Boolean, listener: (SkyBlockSackTransfer) -> Unit) {
        listeners.register(boundary, isActive, listener)
    }

    private fun dispatch(transfer: SkyBlockSackTransfer) {
        listeners.forEachActive { listener -> listener(transfer) }
    }

    private fun hasActiveListeners(): Boolean = listeners.hasActiveListeners
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
private val INSERT_INVENTORY_WINDOW = 3.seconds
