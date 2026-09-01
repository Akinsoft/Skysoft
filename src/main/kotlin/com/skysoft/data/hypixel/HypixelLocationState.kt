package com.skysoft.data.hypixel

import com.skysoft.data.SkyBlockIsland
import com.skysoft.utils.ActiveStatePublisher
import com.skysoft.utils.SkysoftClientEvents
import net.hypixel.data.type.GameType
import net.hypixel.data.type.ServerType
import net.hypixel.modapi.HypixelModAPI
import net.hypixel.modapi.packet.impl.clientbound.event.ClientboundLocationPacket
import kotlin.jvm.optionals.getOrNull

object HypixelLocationState {
    private val publisher = ActiveStatePublisher("Hypixel Location State", HypixelLocationSnapshot())

    val onHypixel: Boolean
        get() = publisher.state.onHypixel

    val game: SkysoftGame?
        get() = publisher.state.game

    val inSkyBlock: Boolean
        get() = game == SkysoftGame.SKYBLOCK

    val inRavengard: Boolean
        get() = game == SkysoftGame.RAVENGARD

    val currentIsland: SkyBlockIsland?
        get() = publisher.state.currentIsland

    val currentMode: String?
        get() = publisher.state.currentMode

    val currentServerName: String?
        get() = publisher.state.currentServerName

    val currentLobbyName: String?
        get() = publisher.state.currentLobbyName

    val locationVersion: Long
        get() = publisher.version

    private var registered = false

    fun register() {
        if (registered) return
        registered = true
        publisher.register()

        val modApi = HypixelModAPI.getInstance()
        modApi.subscribeToEventPacket(ClientboundLocationPacket::class.java)
        modApi.createHandler(ClientboundLocationPacket::class.java, ::onLocationPacket)

        SkysoftClientEvents.onDisconnect("Hypixel Location reset", ::reset)
    }

    private fun onLocationPacket(packet: ClientboundLocationPacket) {
        acceptLocation(packet)
    }

    fun onChange(
        boundary: String,
        isActive: () -> Boolean,
        listener: (HypixelLocationSnapshot) -> Unit,
    ) {
        publisher.onChange(boundary, isActive, listener)
    }

    internal fun acceptLocation(packet: ClientboundLocationPacket) {
        val mode = packet.mode.getOrNull()
        val game = skysoftGame(packet.serverType.getOrNull(), mode)
        val map = packet.map.getOrNull()
        publisher.update(
            HypixelLocationSnapshot(
                onHypixel = true,
                game = game,
                currentIsland = if (game == SkysoftGame.SKYBLOCK) SkyBlockIsland.getByLocation(mode, map) else null,
                currentMode = mode,
                currentServerName = packet.serverName?.takeIf { it.isNotBlank() },
                currentLobbyName = packet.lobbyName.getOrNull(),
            ),
        )
    }

    private fun reset() {
        publisher.update(HypixelLocationSnapshot())
    }
}

data class HypixelLocationSnapshot(
    val onHypixel: Boolean = false,
    val game: SkysoftGame? = null,
    val currentIsland: SkyBlockIsland? = null,
    val currentMode: String? = null,
    val currentServerName: String? = null,
    val currentLobbyName: String? = null,
) {
    val inSkyBlock: Boolean
        get() = game == SkysoftGame.SKYBLOCK
}

enum class SkysoftGame {
    SKYBLOCK,
    RAVENGARD,
}

internal fun skysoftGame(serverType: ServerType?, mode: String?): SkysoftGame? = when {
    serverType == GameType.SKYBLOCK -> SkysoftGame.SKYBLOCK
    serverType == GameType.PROTOTYPE && mode?.startsWith(RAVENGARD_MODE_PREFIX, ignoreCase = true) == true ->
        SkysoftGame.RAVENGARD
    else -> null
}

private const val RAVENGARD_MODE_PREFIX = "RAVENGARD_"
