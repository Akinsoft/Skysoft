package com.skysoft.features.bazaar

internal object BazaarSessionState {
    var knownProfit = 0.0
        private set
    var buySetupValue = 0.0
        private set
    var sellSetupValue = 0.0
        private set

    fun recordBuySetup(value: Double) {
        buySetupValue += value
    }

    fun recordSellSetup(value: Double) {
        sellSetupValue += value
    }

    fun recordProfit(value: Double) {
        knownProfit += value
    }

    fun reset() {
        knownProfit = 0.0
        buySetupValue = 0.0
        sellSetupValue = 0.0
    }
}
