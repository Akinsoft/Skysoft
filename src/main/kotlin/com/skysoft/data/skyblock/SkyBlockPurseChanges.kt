package com.skysoft.data.skyblock

import com.skysoft.data.hypixel.HypixelLocationState
import com.skysoft.data.hypixel.SkyBlockProfileApi
import com.skysoft.utils.SidebarScoreboard
import com.skysoft.utils.SkysoftClientEvents
import com.skysoft.utils.SkysoftErrorBoundary
import net.minecraft.client.Minecraft

object SkyBlockPurseChanges {
    private var listeners: List<Listener> = emptyList()
    private var previousPurse: Double? = null
    private var previousContext: PurseContext? = null

    fun register() {
        SkysoftClientEvents.onEndTick(
            "SkyBlock purse changes",
            isActive = { HypixelLocationState.inSkyBlock && listeners.any { it.isActive() } },
            action = ::update,
        )
        SkysoftClientEvents.onDisconnect("SkyBlock purse changes reset", ::reset)
        SkyBlockProfileApi.onProfileChange(
            "SkyBlock purse changes profile reset",
            isActive = { previousPurse != null },
            listener = { reset() },
        )
    }

    fun onChange(boundary: String, isActive: () -> Boolean, listener: (SkyBlockPurseChange) -> Unit) {
        listeners += Listener(boundary, isActive, listener)
    }

    private fun update(minecraft: Minecraft) {
        val player = minecraft.player ?: return reset()
        val context = PurseContext(
            HypixelLocationState.locationVersion,
            player.uuid.toString(),
            SkyBlockProfileApi.currentProfileKey,
            minecraft.level,
        )
        val purse = SidebarScoreboard.currentLines().firstNotNullOfOrNull(::parseSkyBlockPurse) ?: return
        if (context != previousContext) {
            previousContext = context
            previousPurse = purse.amount
            return
        }
        val change = purse.amount - (previousPurse ?: purse.amount)
        previousPurse = purse.amount
        if (change == 0.0) return
        val observation = SkyBlockPurseChange(change, purse.amount)
        listeners.forEach { registered ->
            if (registered.isActive()) {
                SkysoftErrorBoundary.run(registered.boundary) { registered.listener(observation) }
            }
        }
    }

    private fun reset() {
        previousPurse = null
        previousContext = null
    }

    private data class Listener(
        val boundary: String,
        val isActive: () -> Boolean,
        val listener: (SkyBlockPurseChange) -> Unit,
    )

    private data class PurseContext(
        val locationVersion: Long,
        val playerId: String,
        val profileId: String?,
        val level: Any?,
    )
}

data class SkyBlockPurseChange(
    val amount: Double,
    val balance: Double,
)

internal data class SkyBlockPurseSnapshot(val amount: Double)

internal fun parseSkyBlockPurse(line: String): SkyBlockPurseSnapshot? {
    val amount = PURSE_PATTERN.matchEntire(line.trim())
        ?.groups
        ?.get("amount")
        ?.value
        ?.replace(",", "")
        ?.toDoubleOrNull()
        ?: return null
    return SkyBlockPurseSnapshot(amount)
}

private val PURSE_PATTERN = Regex("^(?:Piggy|Purse): (?<amount>[\\d,.]+)(?: \\([+-][\\d,.]+\\))?.*$")
