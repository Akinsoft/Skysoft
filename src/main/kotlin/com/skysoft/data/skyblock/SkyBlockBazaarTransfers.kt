package com.skysoft.data.skyblock

import com.skysoft.utils.SkysoftErrorBoundary
import com.skysoft.utils.chat.ChatEvents
import com.skysoft.utils.chat.ChatMessageVisibility

object SkyBlockBazaarTransfers {
    private var listeners: List<Listener> = emptyList()
    private var registered = false

    fun onTransfer(
        boundary: String,
        isActive: () -> Boolean,
        listener: (SkyBlockBazaarTransfer) -> Unit,
    ) {
        if (!registered) register()
        listeners += Listener(boundary, isActive, listener)
    }

    private fun register() {
        registered = true
        ChatEvents.onVisibleMessage("SkyBlock Bazaar transfers", { listeners.any { it.isActive() } }) { message ->
            parseSkyBlockBazaarTransfer(message.cleanText)?.let { transfer ->
                listeners.forEach { registered ->
                    if (registered.isActive()) {
                        SkysoftErrorBoundary.run(registered.boundary) { registered.listener(transfer) }
                    }
                }
            }
            ChatMessageVisibility.SHOW
        }
    }

    private data class Listener(
        val boundary: String,
        val isActive: () -> Boolean,
        val listener: (SkyBlockBazaarTransfer) -> Unit,
    )
}

data class SkyBlockBazaarTransfer(
    val displayName: String,
    val amount: Int,
    val direction: SkyBlockBazaarTransferDirection,
)

enum class SkyBlockBazaarTransferDirection {
    TO_PLAYER,
    FROM_PLAYER,
}

internal fun parseSkyBlockBazaarTransfer(message: String): SkyBlockBazaarTransfer? {
    val (definition, match) = bazaarTransferPatterns.firstNotNullOfOrNull { definition ->
        definition.pattern.matchEntire(message)?.let { match -> definition to match }
    } ?: return null
    val amount = match.groupValues[1].replace(",", "").toIntOrNull() ?: return null
    return SkyBlockBazaarTransfer(match.groupValues[2].trim(), amount, definition.direction)
}

private data class BazaarTransferPattern(
    val pattern: Regex,
    val direction: SkyBlockBazaarTransferDirection,
)

internal val sellSetupPattern =
    Regex("""^\[Bazaar] Sell Offer Setup! ([\d,]+)x (.+) for ([\d,.]+) coins\.$""")
internal val sellCancelPattern =
    Regex(
        """^(?:\[Bazaar] )?Cancelled! Refunded ([\d,]+)x (.+) from cancelling sell offer!$""",
        RegexOption.IGNORE_CASE,
    )
internal val buyClaimPattern =
    Regex("""^\[Bazaar] Claimed ([\d,]+)x (.+) worth ([\d,.]+) coins bought for ([\d,.]+) each!$""")
internal val instantBuyPattern = Regex("""^\[Bazaar] Bought ([\d,]+)x (.+) for ([\d,.]+) coins!$""")
internal val instantSellPattern = Regex("""^\[Bazaar] Sold ([\d,]+)x (.+) for ([\d,.]+) coins!$""")

private val bazaarTransferPatterns = listOf(
    BazaarTransferPattern(instantBuyPattern, SkyBlockBazaarTransferDirection.TO_PLAYER),
    BazaarTransferPattern(buyClaimPattern, SkyBlockBazaarTransferDirection.TO_PLAYER),
    BazaarTransferPattern(sellCancelPattern, SkyBlockBazaarTransferDirection.TO_PLAYER),
    BazaarTransferPattern(instantSellPattern, SkyBlockBazaarTransferDirection.FROM_PLAYER),
    BazaarTransferPattern(sellSetupPattern, SkyBlockBazaarTransferDirection.FROM_PLAYER),
)
