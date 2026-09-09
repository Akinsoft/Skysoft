package com.skysoft.config

internal enum class BazaarGraphMode(
    val label: String,
    val buyFilterLabel: String,
    val sellFilterLabel: String,
) {
    PRICE_HISTORY("Price History", "Buy Prices", "Sell Prices"),
    ORDER_BOOK("Order Book Depth", "Buy Orders", "Sell Orders"),
    TRADE_VOLUME("Trade Volume", "Buy Volume", "Sell Volume"),
    ;

    companion object {
        fun fromStoredName(name: String?): BazaarGraphMode = when (name) {
            "PRICE" -> ORDER_BOOK
            "ACTIVITY" -> TRADE_VOLUME
            else -> entries.firstOrNull { it.name == name } ?: PRICE_HISTORY
        }
    }
}

internal enum class BazaarGraphWindow(val label: String, val durationMillis: Long) {
    FIFTEEN_MINUTES("15m", 15 * 60_000L),
    THIRTY_MINUTES("30m", 30 * 60_000L),
    ONE_HOUR("1h", 60 * 60_000L),
    SIX_HOURS("6h", 6 * 60 * 60_000L),
    TWENTY_FOUR_HOURS("24h", 24 * 60 * 60_000L),
    SEVEN_DAYS("7d", 7 * 24 * 60 * 60_000L),
    THIRTY_DAYS("30d", 30 * 24 * 60 * 60_000L),
    ;

    companion object {
        fun fromStoredName(name: String?): BazaarGraphWindow =
            entries.firstOrNull { it.name == name } ?: ONE_HOUR
    }
}
