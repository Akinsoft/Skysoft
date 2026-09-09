package com.skysoft.features.profit

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
        return parseForagingChatDrop(cleanMessage)
    }

    fun clear() {
        isBonusGiftPending = false
    }
}

private val FORAGING_BONUS_GIFT_HEADER = Regex("^BONUS GIFT(?: \\(\\d+\\))?$")
