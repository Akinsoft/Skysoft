package com.skysoft.features.bazaar

internal object BazaarSessionState {
    var knownProfit = 0.0
        private set

    fun recordProfit(value: Double) {
        knownProfit += value
    }

    fun reset() {
        knownProfit = 0.0
    }
}
