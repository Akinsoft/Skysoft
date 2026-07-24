package com.skysoft.features.profit

internal fun parseDyeChatDrop(message: String, playerName: String): ParsedItemAmount? {
    val match = DYE_DROP_PATTERN.matchEntire(message) ?: return null
    val recipient = match.groups["player"]?.value?.substringAfterLast(' ') ?: return null
    val item = match.groups["item"]?.value ?: return null
    return ParsedItemAmount(item, 1).takeIf { recipient.equals(playerName, ignoreCase = true) }
}

private val DYE_DROP_PATTERN = Regex("^WOW! (?<player>.+) found an? (?<item>.+ Dye)!$")
