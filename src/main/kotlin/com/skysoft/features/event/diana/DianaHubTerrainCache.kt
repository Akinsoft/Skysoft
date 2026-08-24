package com.skysoft.features.event.diana

import com.skysoft.config.SkysoftConfigGui
import com.skysoft.data.hypixel.HypixelLocationState
import com.skysoft.mixin.ClientPacketListenerAccessor
import com.skysoft.utils.SkysoftClientEvents
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.core.SectionPos
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.LightLayer
import net.minecraft.world.level.chunk.status.ChunkStatus

internal object DianaHubTerrainCache {
    private val config get() = SkysoftConfigGui.config().events.diana
    private val retainedChunks = linkedSetOf<ChunkPos>()
    private val bobbyInstalled = FabricLoader.getInstance().isModLoaded("bobby")
    private var expandedLevel: ClientLevel? = null
    private var activeSession: HubSession? = null
    private var restoringServerDistance = false

    fun register() {
        SkysoftClientEvents.onEndTick(
            "Diana Hub terrain cache tick",
            isActive = { expandedLevel != null || isConfigured() },
        ) { minecraft -> onTick(minecraft) }
        SkysoftClientEvents.onDisconnect("Diana Hub terrain cache disconnect reset", ::reset)
    }

    @JvmStatic
    fun storageViewDistance(viewDistance: Int): Int =
        if (!restoringServerDistance && currentSession() != null) {
            maxOf(viewDistance, HUB_CACHE_DISTANCE)
        } else {
            viewDistance
        }

    @JvmStatic
    fun didRetain(level: ClientLevel, position: ChunkPos): Boolean {
        val session = currentSession() ?: return false
        if (expandedLevel !== level || activeSession != session) return false
        val chunk = level.chunkSource.getChunk(position.x, position.z, ChunkStatus.FULL, false) ?: return false
        chunk.clearAllBlockEntities()
        retainedChunks += position
        return true
    }

    @JvmStatic
    fun markServerLoaded(level: ClientLevel, position: ChunkPos) {
        if (expandedLevel === level) retainedChunks.remove(position)
    }

    private fun onTick(minecraft: Minecraft) {
        val level = minecraft.level
        val session = currentSession()
        if (level == null || session == null) {
            deactivate(minecraft)
            return
        }
        if (expandedLevel === level && activeSession == session) return
        if (expandedLevel === level) {
            releaseRetained(level)
            resizeToServerDistance(minecraft, level)
        } else {
            retainedChunks.clear()
        }
        val serverDistance = serverViewDistance(minecraft) ?: return
        expandedLevel = level
        activeSession = session
        level.chunkSource.updateViewRadius(serverDistance.coerceAtLeast(HUB_CACHE_DISTANCE))
    }

    private fun deactivate(minecraft: Minecraft) {
        val level = expandedLevel
        if (level != null && minecraft.level === level) {
            releaseRetained(level)
            resizeToServerDistance(minecraft, level)
        }
        reset()
    }

    private fun releaseRetained(level: ClientLevel) {
        retainedChunks.forEach { position ->
            level.chunkSource.drop(position)
            removeLight(level, position)
        }
        retainedChunks.clear()
    }

    private fun removeLight(level: ClientLevel, position: ChunkPos) {
        level.queueLightUpdate {
            if (level.chunkSource.getChunk(position.x, position.z, ChunkStatus.FULL, false) != null) {
                return@queueLightUpdate
            }
            val lightEngine = level.lightEngine
            lightEngine.setLightEnabled(position, false)
            for (sectionY in lightEngine.minLightSection until lightEngine.maxLightSection) {
                val section = SectionPos.of(position, sectionY)
                lightEngine.queueSectionData(LightLayer.BLOCK, section, null)
                lightEngine.queueSectionData(LightLayer.SKY, section, null)
            }
            for (sectionY in level.minSectionY..level.maxSectionY) {
                lightEngine.updateSectionStatus(SectionPos.of(position, sectionY), true)
            }
        }
    }

    private fun resizeToServerDistance(minecraft: Minecraft, level: ClientLevel) {
        val serverDistance = serverViewDistance(minecraft) ?: return
        restoringServerDistance = true
        try {
            level.chunkSource.updateViewRadius(serverDistance)
        } finally {
            restoringServerDistance = false
        }
    }

    private fun serverViewDistance(minecraft: Minecraft): Int? =
        (minecraft.connection as? ClientPacketListenerAccessor)?.skysoftServerChunkRadius()

    private fun currentSession(): HubSession? {
        if (!isConfigured() || !DianaEventState.isOnHub()) return null
        val serverName = HypixelLocationState.currentServerName ?: return null
        return HubSession(serverName, HypixelLocationState.currentLobbyName)
    }

    private fun isConfigured(): Boolean =
        !bobbyInstalled && config.enabled && config.settings.keepHubTerrainLoaded

    private fun reset() {
        retainedChunks.clear()
        expandedLevel = null
        activeSession = null
        restoringServerDistance = false
    }

    private data class HubSession(
        val serverName: String,
        val lobbyName: String?,
    )

    private const val HUB_CACHE_DISTANCE = 48
}
