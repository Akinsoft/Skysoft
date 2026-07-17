package com.skysoft.data.hypixel

import com.skysoft.data.SkyBlockIsland
import com.skysoft.utils.SkysoftClientEvents
import net.hypixel.data.type.GameType
import net.hypixel.modapi.HypixelModAPI
import net.hypixel.modapi.packet.impl.clientbound.event.ClientboundLocationPacket
import kotlin.jvm.optionals.getOrNull

object HypixelLocationState {
    var onHypixel: Boolean = false
        private set

    var inSkyBlock: Boolean = false
        private set

    var currentIsland: SkyBlockIsland? = null
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
        val newInSkyBlock = packet.serverType.getOrNull() == GameType.SKYBLOCK
        val serverName = packet.serverName?.takeIf { it.isNotBlank() }
        val lobbyName = packet.lobbyName.getOrNull()
        val mode = packet.mode.getOrNull()
        val map = packet.map.getOrNull()
        val newIsland = if (newInSkyBlock) SkyBlockIsland.getByLocation(mode, map) else null
        if (
            !wasOnHypixel ||
            inSkyBlock != newInSkyBlock ||
            currentIsland != newIsland ||
            currentServerName != serverName ||
            currentLobbyName != lobbyName
        ) {
            locationVersion++
        }
        inSkyBlock = newInSkyBlock
        currentIsland = newIsland
        currentServerName = serverName
        currentLobbyName = lobbyName
    }

    private fun reset() {
        if (onHypixel || inSkyBlock || currentIsland != null) locationVersion++
        onHypixel = false
        inSkyBlock = false
        currentIsland = null
        currentServerName = null
        currentLobbyName = null
    }
}
