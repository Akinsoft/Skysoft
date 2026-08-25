package com.skysoft.features.misc

import com.skysoft.SkysoftMod
import com.skysoft.config.SkysoftConfigFiles
import com.skysoft.config.SkysoftConfigGui
import com.skysoft.data.SkyBlockIsland
import com.skysoft.data.hypixel.HypixelLocationState
import com.skysoft.mixin.ClientPacketListenerAccessor
import com.skysoft.utils.SkysoftClientEvents
import io.netty.buffer.Unpooled
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.SharedConstants
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.core.RegistryAccess
import net.minecraft.core.SectionPos
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.Level
import net.minecraft.world.level.LightLayer
import net.minecraft.world.level.chunk.status.ChunkStatus
import net.minecraft.world.level.chunk.storage.IOWorker
import net.minecraft.world.level.chunk.storage.RegionStorageInfo
import java.nio.file.Path
import java.util.Locale
import java.util.concurrent.CompletableFuture
import kotlin.math.abs

internal object KeepTerrainLoaded {
    private val config get() = SkysoftConfigGui.config().misc.keepTerrainLoaded
    private val retainedChunks = linkedSetOf<ChunkPos>()
    private val loadingChunks = mutableSetOf<LoadingChunk>()
    private val pendingSaves = linkedMapOf<LoadingChunk, PendingSave>()
    private val storages = mutableMapOf<Path, TerrainStorage>()
    private val bobbyInstalled = FabricLoader.getInstance().isModLoaded("bobby")
    private var expandedLevel: ClientLevel? = null
    private var activeSession: TerrainSession? = null
    private var activeStorage: TerrainStorage? = null
    private var restoringServerDistance = false
    private var applyingCachedPacket = false
    private var scanCenter: ChunkPos? = null
    private var scanDistance = -1
    private var scanOffsets = emptyList<TerrainChunkOffset>()
    private var scanIndex = 0

    fun register() {
        SkysoftClientEvents.onEndTick(
            "Keep Terrain Loaded tick",
            isActive = { expandedLevel != null || pendingSaves.isNotEmpty() || currentSession() != null },
        ) { minecraft -> onTick(minecraft) }
        SkysoftClientEvents.onDisconnect("Keep Terrain Loaded disconnect reset", ::close)
    }

    @JvmStatic
    fun storageViewDistance(viewDistance: Int): Int =
        if (!restoringServerDistance && currentSession() != null) {
            maxOf(viewDistance, terrainCacheDistance(Minecraft.getInstance()))
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
    fun onServerChunk(level: ClientLevel, packet: ClientboundLevelChunkWithLightPacket) {
        if (applyingCachedPacket || expandedLevel !== level) return
        val session = currentSession() ?: return
        val storage = activeStorage ?: return
        if (activeSession != session) return
        val position = ChunkPos(packet.x, packet.z)
        retainedChunks.remove(position)
        val key = LoadingChunk(storage, position.pack())
        pendingSaves[key] = PendingSave(storage, level.registryAccess(), level.sectionsCount, packet)
        while (pendingSaves.size > MAX_PENDING_SAVES) {
            pendingSaves.entries.iterator().also { iterator ->
                iterator.next()
                iterator.remove()
            }
        }
    }

    private fun onTick(minecraft: Minecraft) {
        processPendingSaves()
        val level = minecraft.level
        val session = currentSession()
        if (level == null || session == null) {
            deactivate(minecraft)
            return
        }
        if (expandedLevel !== level || activeSession != session) activate(minecraft, level, session)
        if (expandedLevel !== level || activeSession != session) return

        val serverDistance = serverViewDistance(minecraft) ?: return
        val distance = terrainCacheDistance(minecraft)
        level.chunkSource.updateViewRadius(maxOf(serverDistance, distance))
        val center = minecraft.player?.chunkPosition() ?: return
        if (scanCenter != center || scanDistance != distance) resetScan(center, distance)
        processCachedChunkLoads(minecraft, level, session)
    }

    private fun activate(minecraft: Minecraft, level: ClientLevel, session: TerrainSession) {
        if (expandedLevel === level) {
            releaseRetained(level)
            resizeToServerDistance(minecraft, level)
        } else {
            retainedChunks.clear()
        }
        val serverDistance = serverViewDistance(minecraft) ?: return
        expandedLevel = level
        activeSession = session
        activeStorage = storageFor(session)
        val distance = terrainCacheDistance(minecraft)
        level.chunkSource.updateViewRadius(maxOf(serverDistance, distance))
        minecraft.player?.chunkPosition()?.let { center -> resetScan(center, distance) }
    }

    private fun processPendingSaves() {
        repeat(MAX_SAVES_PER_TICK) {
            val iterator = pendingSaves.entries.iterator()
            if (!iterator.hasNext()) return
            val save = iterator.next().value
            iterator.remove()
            runCatching {
                save.storage.save(
                    ChunkPos(save.packet.x, save.packet.z),
                    encodePacket(save.packet, save.registryAccess),
                    save.sectionCount,
                )
            }.onFailure { error ->
                SkysoftMod.LOGGER.warn("Could not cache SkyBlock terrain chunk", error)
            }
        }
    }

    private fun processCachedChunkLoads(minecraft: Minecraft, level: ClientLevel, session: TerrainSession) {
        val storage = activeStorage ?: return
        var checks = 0
        while (
            scanIndex < scanOffsets.size &&
            loadingChunks.count { it.storage === storage } < MAX_CONCURRENT_LOADS &&
            checks++ < MAX_SCAN_CHECKS_PER_TICK
        ) {
            val offset = scanOffsets[scanIndex++]
            val center = scanCenter ?: return
            val position = ChunkPos(center.x + offset.x, center.z + offset.z)
            if (level.chunkSource.getChunk(position.x, position.z, ChunkStatus.FULL, false) != null) continue
            val key = LoadingChunk(storage, position.pack())
            if (!loadingChunks.add(key)) continue
            storage.load(position, level.sectionsCount).whenComplete { packetBytes, error ->
                minecraft.execute {
                    loadingChunks.remove(key)
                    if (error != null) {
                        SkysoftMod.LOGGER.warn("Could not load cached SkyBlock terrain chunk", error)
                    } else if (packetBytes != null) {
                        applyCachedPacket(minecraft, level, session, position, packetBytes)
                    }
                }
            }
        }
    }

    private fun applyCachedPacket(
        minecraft: Minecraft,
        level: ClientLevel,
        session: TerrainSession,
        position: ChunkPos,
        packetBytes: ByteArray,
    ) {
        if (expandedLevel !== level || activeSession != session || !isWithinScanDistance(position)) return
        if (level.chunkSource.getChunk(position.x, position.z, ChunkStatus.FULL, false) != null) return
        val connection = minecraft.connection ?: return
        runCatching {
            val packet = decodePacket(packetBytes, level.registryAccess())
            require(packet.x == position.x && packet.z == position.z)
            applyingCachedPacket = true
            try {
                packet.handle(connection)
            } finally {
                applyingCachedPacket = false
            }
            level.chunkSource.getChunk(position.x, position.z, ChunkStatus.FULL, false)?.clearAllBlockEntities()
            retainedChunks += position
        }.onFailure { error ->
            applyingCachedPacket = false
            SkysoftMod.LOGGER.warn("Could not apply cached SkyBlock terrain chunk", error)
        }
    }

    private fun resetScan(center: ChunkPos, distance: Int) {
        scanCenter = center
        scanIndex = 0
        if (scanDistance != distance) {
            scanDistance = distance
            scanOffsets = chunkOffsets(distance)
        }
    }

    private fun isWithinScanDistance(position: ChunkPos): Boolean {
        val center = scanCenter ?: return false
        return abs(position.x - center.x) <= scanDistance && abs(position.z - center.z) <= scanDistance
    }

    private fun deactivate(minecraft: Minecraft) {
        val level = expandedLevel
        if (level != null && minecraft.level === level) {
            releaseRetained(level)
            resizeToServerDistance(minecraft, level)
        }
        expandedLevel = null
        activeSession = null
        activeStorage = null
        scanCenter = null
        scanDistance = -1
        scanOffsets = emptyList()
        scanIndex = 0
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

    private fun storageFor(session: TerrainSession): TerrainStorage {
        val directory = SkysoftConfigFiles.terrainCache
            .resolve(session.island.name.lowercase(Locale.US))
        return storages.getOrPut(directory) { TerrainStorage(directory) }
    }

    private fun serverViewDistance(minecraft: Minecraft): Int? =
        (minecraft.connection as? ClientPacketListenerAccessor)?.skysoftServerChunkRadius()

    private fun currentSession(): TerrainSession? {
        if (!isConfigured()) return null
        val island = HypixelLocationState.currentIsland ?: return null
        if (config.settings.islands.get().none { selected -> selected.island == island }) return null
        val serverName = HypixelLocationState.currentServerName?.takeIf { it.isNotBlank() } ?: return null
        return TerrainSession(island, serverName, HypixelLocationState.currentLobbyName)
    }

    private fun isConfigured(): Boolean = !bobbyInstalled && config.enabled

    private fun close() {
        pendingSaves.clear()
        loadingChunks.clear()
        storages.values.forEach { storage ->
            runCatching(storage::close).onFailure { error ->
                SkysoftMod.LOGGER.warn("Could not close cached SkyBlock terrain storage", error)
            }
        }
        storages.clear()
        retainedChunks.clear()
        expandedLevel = null
        activeSession = null
        activeStorage = null
        restoringServerDistance = false
        applyingCachedPacket = false
        scanCenter = null
        scanDistance = -1
        scanOffsets = emptyList()
        scanIndex = 0
    }

    private data class TerrainSession(
        val island: SkyBlockIsland,
        val serverName: String,
        val lobbyName: String?,
    )

    private data class LoadingChunk(val storage: TerrainStorage, val position: Long)

    private data class PendingSave(
        val storage: TerrainStorage,
        val registryAccess: RegistryAccess,
        val sectionCount: Int,
        val packet: ClientboundLevelChunkWithLightPacket,
    )

    private const val MAX_CONCURRENT_LOADS = 8
    private const val MAX_SCAN_CHECKS_PER_TICK = 64
    private const val MAX_SAVES_PER_TICK = 8
    private const val MAX_PENDING_SAVES = 512
}

private class TerrainStorage(directory: Path) : IOWorker(
    RegionStorageInfo("skysoft", Level.OVERWORLD, "terrain"),
    directory,
    false,
) {
    fun save(position: ChunkPos, packet: ByteArray, sectionCount: Int) {
        val tag = CompoundTag().apply {
            putInt(PROTOCOL_TAG, SharedConstants.getProtocolVersion())
            putInt(SECTIONS_TAG, sectionCount)
            putByteArray(PACKET_TAG, packet)
        }
        store(position, tag).exceptionally { error ->
            SkysoftMod.LOGGER.warn("Could not write cached SkyBlock terrain chunk", error)
            null
        }
    }

    fun load(position: ChunkPos, sectionCount: Int): CompletableFuture<ByteArray?> =
        loadAsync(position).thenApply { stored ->
            val tag = stored.orElse(null) ?: return@thenApply null
            if (tag.getIntOr(PROTOCOL_TAG, -1) != SharedConstants.getProtocolVersion()) return@thenApply null
            if (tag.getIntOr(SECTIONS_TAG, -1) != sectionCount) return@thenApply null
            tag.getByteArray(PACKET_TAG).orElse(null)?.takeIf { it.size <= MAX_TERRAIN_PACKET_BYTES }
        }

    private companion object {
        const val PROTOCOL_TAG = "protocol"
        const val SECTIONS_TAG = "sections"
        const val PACKET_TAG = "packet"
    }
}

private fun encodePacket(packet: ClientboundLevelChunkWithLightPacket, registryAccess: RegistryAccess): ByteArray {
    val source = Unpooled.buffer()
    return try {
        val buffer = RegistryFriendlyByteBuf(source, registryAccess)
        ClientboundLevelChunkWithLightPacket.STREAM_CODEC.encode(buffer, packet)
        ByteArray(buffer.readableBytes()).also(buffer::readBytes)
    } finally {
        source.release()
    }
}

private fun decodePacket(packet: ByteArray, registryAccess: RegistryAccess): ClientboundLevelChunkWithLightPacket {
    require(packet.size <= MAX_TERRAIN_PACKET_BYTES)
    val source = Unpooled.wrappedBuffer(packet)
    return try {
        ClientboundLevelChunkWithLightPacket.STREAM_CODEC.decode(RegistryFriendlyByteBuf(source, registryAccess))
    } finally {
        source.release()
    }
}

private fun terrainCacheDistance(minecraft: Minecraft): Int =
    minecraft.options.renderDistance().get().coerceAtMost(MAX_TERRAIN_CACHE_DISTANCE)

private data class TerrainChunkOffset(val x: Int, val z: Int)

private fun chunkOffsets(distance: Int): List<TerrainChunkOffset> = buildList {
    add(TerrainChunkOffset(0, 0))
    for (radius in 1..distance) {
        for (x in -radius..radius) {
            add(TerrainChunkOffset(x, -radius))
            add(TerrainChunkOffset(x, radius))
        }
        for (z in -radius + 1 until radius) {
            add(TerrainChunkOffset(-radius, z))
            add(TerrainChunkOffset(radius, z))
        }
    }
}

private const val MAX_TERRAIN_CACHE_DISTANCE = 32
private const val MAX_TERRAIN_PACKET_BYTES = 4 * 1024 * 1024
