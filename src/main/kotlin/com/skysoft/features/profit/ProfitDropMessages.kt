package com.skysoft.features.profit

internal fun parseFarmingChatDrop(message: String): ParsedItemAmount? {
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

internal fun parseCountedPestKill(message: String): String? {
    val match = PEST_KILL_PATTERN.matchEntire(message) ?: return null
    val pest = match.groups["pest"]?.value ?: return null
    val counted = when (pest) {
        "Field Mouse" -> match.groups["item"]?.value == "Dung"
        "Lunar Moth" -> match.groups["item"]?.value == "Enchanted Sunflower"
        else -> match.groups["item"]?.value != "Overclocker 3000"
    }
    return pest.takeIf { counted }
}

private val PEST_KILL_PATTERN = Regex(
    "^You received (?<amount>\\d+)x (?<item>.+) for killing an? (?<pest>.+)!$",
)
private val FARMING_DROP_PATTERNS = listOf(
    Regex("^BLESSED! You found an? (?<item>.+)!$"),
    Regex("^(?:VERY )?RARE CROP! (?<item>.+?)(?: \\(.*)?$"),
    Regex("^[\\w ]+! You dropped (?<amount>[\\d,]+)x (?<item>[\\w ]+)!$"),
    PEST_KILL_PATTERN,
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
