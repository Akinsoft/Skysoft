package com.skysoft.data.skyblock

import com.skysoft.data.hypixel.HypixelLocationState
import com.skysoft.data.hypixel.SkyBlockProfileApi
import com.skysoft.utils.SidebarScoreboard
import com.skysoft.utils.SkysoftClientEvents
import com.skysoft.utils.SkysoftErrorBoundary
import net.minecraft.client.Minecraft

object SkyBlockCurrencyChanges {
    private var listeners: List<Listener> = emptyList()
    private var previousBalances = emptyMap<String, Double>()
    private var previousContext: CurrencyContext? = null

    fun register() {
        SkysoftClientEvents.onEndTick(
            "SkyBlock currency changes",
            isActive = { HypixelLocationState.inSkyBlock && listeners.any { it.isActive() } },
            action = ::update,
        )
        SkysoftClientEvents.onDisconnect("SkyBlock currency changes reset", ::reset)
        SkyBlockProfileApi.onProfileChange(
            "SkyBlock currency changes profile reset",
            isActive = { previousBalances.isNotEmpty() },
            listener = { reset() },
        )
    }

    fun onChange(boundary: String, isActive: () -> Boolean, listener: (SkyBlockCurrencyChange) -> Unit) {
        listeners += Listener(boundary, isActive, listener)
    }

    private fun update(minecraft: Minecraft) {
        val player = minecraft.player ?: return reset()
        val context = CurrencyContext(
            HypixelLocationState.locationVersion,
            player.uuid.toString(),
            SkyBlockProfileApi.currentProfileKey,
            minecraft.level,
        )
        val balances = SidebarScoreboard.currentLines()
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
            val observation = SkyBlockCurrencyChange(currency, change, balance)
            listeners.forEach { registered ->
                if (registered.isActive()) {
                    SkysoftErrorBoundary.run(registered.boundary) { registered.listener(observation) }
                }
            }
        }
    }

    private fun reset() {
        previousBalances = emptyMap()
        previousContext = null
    }

    private data class Listener(
        val boundary: String,
        val isActive: () -> Boolean,
        val listener: (SkyBlockCurrencyChange) -> Unit,
    )

    private data class CurrencyContext(
        val locationVersion: Long,
        val playerId: String,
        val profileId: String?,
        val level: Any?,
    )
}

data class SkyBlockCurrencyChange(
    val currency: String,
    val amount: Double,
    val balance: Double,
)

internal data class SkyBlockCurrencySnapshot(val currency: String, val amount: Double)

internal fun parseSkyBlockCurrency(line: String): SkyBlockCurrencySnapshot? {
    val match = CURRENCY_PATTERN.matchEntire(line.trim()) ?: return null
    val currency = if (match.groups["label"]?.value == "Motes") SKYBLOCK_MOTES else SKYBLOCK_COINS
    val amount = match.groups["amount"]
        ?.value
        ?.replace(",", "")
        ?.toDoubleOrNull()
        ?: return null
    return SkyBlockCurrencySnapshot(currency, amount)
}

const val SKYBLOCK_COINS = "Coins"
const val SKYBLOCK_MOTES = "Motes"
private val CURRENCY_PATTERN = Regex(
    "^(?<label>Piggy|Purse|Motes): (?<amount>[\\d,.]+)(?: \\([+-][\\d,.]+\\))?.*$",
)
