package com.skysoft.features.profit

import com.skysoft.data.skyblock.parseGardenPestKill

internal fun parseFarmingChatDrop(message: String): ParsedItemAmount? {
    parseGardenPestKill(message)?.let { return ParsedItemAmount(it.itemName, it.itemAmount) }
    val match = FARMING_DROP_PATTERNS.firstNotNullOfOrNull { it.matchEntire(message) } ?: return null
    return match.parsedItemAmount()
}

internal fun parseMiningChatDrop(message: String): ParsedItemAmount? {
    val match = MINING_DROP_PATTERNS.firstNotNullOfOrNull { it.matchEntire(message) } ?: return null
    return match.parsedItemAmount()
}

internal fun parseForagingChatDrop(message: String): ParsedItemAmount? =
    FORAGING_BONUS_GIFT_PATTERN.matchEntire(message.trim())?.parsedItemAmount()

private fun MatchResult.parsedItemAmount(): ParsedItemAmount? {
    val itemName = groups["item"]?.value ?: return null
    val amount = runCatching { groups["amount"]?.value }.getOrNull()
        ?.replace(",", "")
        ?.toIntOrNull()
        ?: 1
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
private val FORAGING_BONUS_GIFT_PATTERN = Regex(
    "^(?<item>.+?) \\(\\d+(?:\\.\\d+)?%(?: - \\d+(?:\\.\\d+)?%)?\\)(?: \\(\\d+\\))?$",
)
