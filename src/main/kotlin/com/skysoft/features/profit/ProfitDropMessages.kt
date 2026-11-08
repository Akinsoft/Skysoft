package com.skysoft.features.profit

import com.skysoft.data.skyblock.parseGardenPestKill
import com.skysoft.utils.NumberUtilities.formatIntOrNull
import com.skysoft.utils.RegexUtilities.groupOrNull

internal data class ParsedItemAmount(
    val displayName: String,
    val amount: Int,
)

internal fun parseFarmingChatDrop(message: String): ParsedItemAmount? {
    parseGardenPestKill(message)?.let { return ParsedItemAmount(it.itemName, it.itemAmount) }
    val match = FARMING_DROP_PATTERNS.firstNotNullOfOrNull { it.matchEntire(message) } ?: return null
    return match.parsedItemAmount()
}

internal fun parseMiningChatDrop(message: String): ParsedItemAmount? {
    val match = MINING_DROP_PATTERNS.firstNotNullOfOrNull { it.matchEntire(message) } ?: return null
    return match.parsedItemAmount()
}

internal fun parseDyeChatDrop(message: String, playerName: String): ParsedItemAmount? {
    val match = DYE_DROP_PATTERN.matchEntire(message) ?: return null
    val recipient = match.groups["player"]?.value?.substringAfterLast(' ') ?: return null
    val item = match.groups["item"]?.value ?: return null
    return ParsedItemAmount(item, 1).takeIf { recipient.equals(playerName, ignoreCase = true) }
}

internal class ForagingTreeGiftParser {
    private var isBonusGiftPending = false

    fun parse(message: String): ParsedItemAmount? {
        val cleanMessage = message.trim()
        if (FORAGING_BONUS_GIFT_HEADER.matches(cleanMessage)) {
            isBonusGiftPending = true
            return null
        }
        if (!isBonusGiftPending) return null
        isBonusGiftPending = false
        return FORAGING_BONUS_GIFT_PATTERN.matchEntire(cleanMessage)?.parsedItemAmount()
    }

    fun clear() {
        isBonusGiftPending = false
    }
}

private fun MatchResult.parsedItemAmount(): ParsedItemAmount? {
    val itemName = groups["item"]?.value ?: return null
    val amount = groupOrNull("amount")?.formatIntOrNull() ?: 1
    return ParsedItemAmount(itemName, amount)
}

private val FARMING_DROP_PATTERNS = listOf(
    Regex("^BLESSED! You found an? (?<item>.+)!$"),
    Regex("^(?:VERY )?RARE CROP! (?<item>.+?)(?: \\(.*)?$"),
    Regex("^[\\w ]+! You dropped (?<amount>[\\d,]+)x (?<item>[\\w ]+)!$"),
    Regex("^ABOUT TIME! You find an? (?<item>.+?) \\(.*\\)!$"),
    Regex("^OVERFLOW! Your .+ has just dropped an? (?<item>Tool Exp Capsule)!$"),
    Regex("^(?:RARE|PET) DROP! (?<item>.+?)(?: x(?<amount>\\d+))? \\(.*\\)!?$"),
)
private val MINING_DROP_PATTERNS = listOf(
    Regex("^PRISTINE! You found (?<item>\\S Flawed [\\w ]+ Gemstone) x(?<amount>[\\d,]+)!$"),
    Regex("^COMPACT! You found an? (?<item>.+)!$"),
)
private val FORAGING_BONUS_GIFT_HEADER = Regex("^BONUS GIFT(?: \\(\\d+\\))?$")
private val FORAGING_BONUS_GIFT_PATTERN = Regex(
    "^(?<item>.+?) \\(\\d+(?:\\.\\d+)?%(?: - \\d+(?:\\.\\d+)?%)?\\)(?: \\(\\d+\\))?$",
)
private val DYE_DROP_PATTERN = Regex("^WOW! (?<player>.+) found an? (?<item>.+ Dye)!$")
