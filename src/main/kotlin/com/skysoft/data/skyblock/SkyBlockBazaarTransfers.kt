package com.skysoft.data.skyblock

import com.skysoft.utils.NumberUtilities.formatIntOrNull

object SkyBlockBazaarTransfers {
    private val listeners = ParsedChatListeners("SkyBlock Bazaar transfers", ::parseSkyBlockBazaarTransfer)

    fun onTransfer(
        boundary: String,
        isActive: () -> Boolean,
        listener: (SkyBlockBazaarTransfer) -> Unit,
    ) {
        listeners.register(boundary, isActive, listener)
    }
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

private fun parseSkyBlockBazaarTransfer(message: String): SkyBlockBazaarTransfer? {
    val (definition, match) = bazaarTransferPatterns.firstNotNullOfOrNull { definition ->
        definition.pattern.matchEntire(message)?.let { match -> definition to match }
    } ?: return null
    val amount = match.groupValues[1].formatIntOrNull() ?: return null
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
