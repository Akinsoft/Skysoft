package com.skysoft.data.hypixel

import com.skysoft.data.SkyBlockIsland
import com.skysoft.utils.SkysoftClientEvents
import net.hypixel.data.type.GameType
import net.hypixel.data.type.ServerType
import net.hypixel.modapi.HypixelModAPI
import net.hypixel.modapi.packet.impl.clientbound.event.ClientboundLocationPacket
import kotlin.jvm.optionals.getOrNull

object HypixelLocationState {
    var onHypixel: Boolean = false
        private set

    var game: SkysoftGame? = null
        private set

    val inSkyBlock: Boolean
        get() = game == SkysoftGame.SKYBLOCK

    val inRavengard: Boolean
        get() = game == SkysoftGame.RAVENGARD

    var currentIsland: SkyBlockIsland? = null
        private set

    var currentMode: String? = null
        private set

    var currentServerName: String? = null
        private set

    var currentLobbyName: String? = null
        private set

    var locationVersion: Long = 0
        private set

    private var registered = false

    fun register() {
        if (registered) return
        registered = true

        val modApi = HypixelModAPI.getInstance()
        modApi.subscribeToEventPacket(ClientboundLocationPacket::class.java)
        modApi.createHandler(ClientboundLocationPacket::class.java, ::onLocationPacket)

        SkysoftClientEvents.onDisconnect("Hypixel Location reset", ::reset)
    }

    private fun onLocationPacket(packet: ClientboundLocationPacket) {
        acceptLocation(packet)
    }

    internal fun acceptLocation(packet: ClientboundLocationPacket) {
        val wasOnHypixel = onHypixel
        onHypixel = true
        val mode = packet.mode.getOrNull()
        val newGame = skysoftGame(packet.serverType.getOrNull(), mode)
        val serverName = packet.serverName?.takeIf { it.isNotBlank() }
        val lobbyName = packet.lobbyName.getOrNull()
        val map = packet.map.getOrNull()
        val newIsland = if (newGame == SkysoftGame.SKYBLOCK) SkyBlockIsland.getByLocation(mode, map) else null
        if (
            !wasOnHypixel ||
            game != newGame ||
            currentIsland != newIsland ||
            currentMode != mode ||
            currentServerName != serverName ||
            currentLobbyName != lobbyName
        ) {
            locationVersion++
        }
        game = newGame
        currentIsland = newIsland
        currentMode = mode
        currentServerName = serverName
        currentLobbyName = lobbyName
    }

    private fun reset() {
        if (onHypixel || game != null || currentIsland != null) locationVersion++
        onHypixel = false
        game = null
        currentIsland = null
        currentMode = null
        currentServerName = null
        currentLobbyName = null
    }
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
