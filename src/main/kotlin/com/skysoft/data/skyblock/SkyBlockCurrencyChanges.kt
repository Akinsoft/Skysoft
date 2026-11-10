package com.skysoft.data.skyblock

import com.skysoft.data.hypixel.HypixelLocationState
import com.skysoft.data.hypixel.SkyBlockProfileApi
import com.skysoft.data.hypixel.SkyBlockSessionContext
import com.skysoft.utils.ActiveListenerRegistry
import com.skysoft.utils.NumberUtilities.formatDoubleOrNull
import com.skysoft.utils.SidebarScoreboardState
import com.skysoft.utils.SkysoftClientEvents
import net.minecraft.client.Minecraft

object SkyBlockCurrencyChanges {
    private val listeners = ActiveListenerRegistry<(SkyBlockCurrencyChange) -> Unit>()
    private var previousBalances = emptyMap<String, Double>()
    private var previousContext: SkyBlockSessionContext? = null

    fun register() {
        SidebarScoreboardState.onChange(
            "SkyBlock currency changes",
            isActive = { HypixelLocationState.inSkyBlock },
            listener = ::update,
        )
        SkysoftClientEvents.onDisconnect("SkyBlock currency changes reset", ::reset)
        SkyBlockProfileApi.onProfileChange(
            "SkyBlock currency changes profile reset",
            isActive = { previousBalances.isNotEmpty() },
            listener = { reset() },
        )
    }

    fun onChange(boundary: String, isActive: () -> Boolean, listener: (SkyBlockCurrencyChange) -> Unit) {
        listeners.register(boundary, isActive, listener)
    }

    private fun update(lines: List<String>) {
        val minecraft = Minecraft.getInstance()
        val player = minecraft.player ?: return reset()
        val context = SkyBlockSessionContext.of(minecraft, player)
        val balances = lines
            .mapNotNull(::parseSkyBlockCurrency)
            .associate { it.currency to it.amount }
        if (balances.isEmpty()) return
        if (context != previousContext) {
            previousContext = context
            previousBalances = balances
            return
        }
        val oldBalances = previousBalances
        previousBalances = balances
        balances.forEach { (currency, balance) ->
            val change = balance - (oldBalances[currency] ?: return@forEach)
            if (change == 0.0) return@forEach
            val observation = SkyBlockCurrencyChange(currency, change)
            listeners.forEachActive { listener -> listener(observation) }
        }
    }

    private fun reset() {
        previousBalances = emptyMap()
        previousContext = null
    }
}

data class SkyBlockCurrencyChange(
    val currency: String,
    val amount: Double,
)

internal data class SkyBlockCurrencySnapshot(val currency: String, val amount: Double)

private fun parseSkyBlockCurrency(line: String): SkyBlockCurrencySnapshot? {
    val match = CURRENCY_PATTERN.matchEntire(line.trim()) ?: return null
    val currency = if (match.groups["label"]?.value == "Motes") SKYBLOCK_MOTES else SKYBLOCK_COINS
    val amount = match.groups["amount"]?.value?.formatDoubleOrNull() ?: return null
    return SkyBlockCurrencySnapshot(currency, amount)
}

const val SKYBLOCK_COINS = "Coins"
const val SKYBLOCK_MOTES = "Motes"
private val CURRENCY_PATTERN = Regex(
    "^(?<label>Piggy|Purse|Motes): (?<amount>[\\d,.]+)(?: \\([+-][\\d,.]+\\))?.*$",
)
