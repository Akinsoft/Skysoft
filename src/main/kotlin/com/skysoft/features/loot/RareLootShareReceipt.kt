package com.skysoft.features.loot

internal object RareLootShareReceipt {
    fun assistedPlayer(message: String): String? =
        receiptPattern.matchEntire(message.trim())?.groups?.get("player")?.value

    fun isReceipt(message: String): Boolean =
        assistedPlayer(message) != null

    fun isWithinWindow(lastReceiptAtMillis: Long, now: Long): Boolean =
        lastReceiptAtMillis > 0L && now - lastReceiptAtMillis <= RECEIPT_WINDOW_MILLIS

    private val receiptPattern = Regex(
        """^LOOT SHARE You received(?: .+?)? for assisting (?<player>[A-Z0-9_]{1,16})!(?: \(\d+\))?$""",
        RegexOption.IGNORE_CASE,
    )
    private const val RECEIPT_WINDOW_MILLIS = 2_000L
}
