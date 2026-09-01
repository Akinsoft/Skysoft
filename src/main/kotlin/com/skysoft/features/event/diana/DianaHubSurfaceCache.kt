package com.skysoft.features.event.diana

import com.google.gson.GsonBuilder
import com.google.gson.annotations.Expose
import com.skysoft.SkysoftMod
import com.skysoft.config.SkysoftConfigFiles
import com.skysoft.data.BackgroundSave
import com.skysoft.utils.WorldVec
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.core.BlockPos
import java.nio.file.Files
import kotlin.math.floor

internal enum class DianaCachedSurfaceStatus {
    VALID,
    INVALID,
    UNKNOWN,
}

internal data class DianaCachedSurface(
    val status: DianaCachedSurfaceStatus,
    val location: WorldVec? = null,
)

internal object DianaHubSurfaceCache {
    private val gson = GsonBuilder()
        .excludeFieldsWithoutExposeAnnotation()
        .create()
    private val cachePath = SkysoftConfigFiles.dianaHubSurfaceCache
    private val data = load()
    private val saves = BackgroundSave(
        name = "Diana Hub surface cache",
        prepare = data::snapshot,
        write = { snapshot -> SkysoftConfigFiles.writeStringSafely(cachePath, gson.toJson(snapshot)) },
    )
    private val pendingChunks = ArrayDeque<ChunkKey>()
    private val queuedChunks = mutableSetOf<ChunkKey>()
    private var activeScan: ChunkScan? = null
    private var lastEnqueuedAround: ChunkKey? = null

    fun onTick() {
        val level = Minecraft.getInstance().level ?: return
        val player = Minecraft.getInstance().player ?: return
        val playerChunk = ChunkKey.fromBlock(floor(player.x).toInt(), floor(player.z).toInt())
        if (playerChunk != lastEnqueuedAround) {
            enqueueAround(playerChunk)
            lastEnqueuedAround = playerChunk
        }
        scanColumns(level)
        saves.saveIfDue()
    }

    fun cachedSurface(location: WorldVec): DianaCachedSurface {
        val block = location.roundToBlock()
        val x = block.x.toInt()
        val z = block.z.toInt()
        val y = data.validColumns[columnKey(x, z)]
        if (y != null) return DianaCachedSurface(DianaCachedSurfaceStatus.VALID, WorldVec(x.toDouble(), y.toDouble(), z.toDouble()))
        val chunk = ChunkKey.fromBlock(x, z)
        return if (chunk.key in data.observedChunks) {
            DianaCachedSurface(DianaCachedSurfaceStatus.INVALID)
        } else {
            DianaCachedSurface(DianaCachedSurfaceStatus.UNKNOWN)
        }
    }

    fun saveInBackground() {
        saves.saveInBackground()
    }

    fun flush() {
        saves.flush()
    }

    private fun enqueueAround(center: ChunkKey) {
        for (dx in -CACHE_CHUNK_RADIUS..CACHE_CHUNK_RADIUS) {
            for (dz in -CACHE_CHUNK_RADIUS..CACHE_CHUNK_RADIUS) {
                val chunk = ChunkKey(center.x + dx, center.z + dz)
                enqueue(chunk)
            }
        }
    }

    private fun scanColumns(level: ClientLevel) {
        var remaining = COLUMN_SCAN_BUDGET
        while (remaining > 0) {
            val scan = activeScan ?: ChunkScan(nextLoadedChunk(level) ?: return).also { activeScan = it }
            if (!isChunkLoaded(level, scan.chunk)) {
                activeScan = null
                enqueue(scan.chunk)
                continue
            }
            remaining -= scanNextColumns(level, scan, remaining)
            if (scan.nextColumnIndex >= COLUMNS_PER_CHUNK) {
                val changed = data.observedChunks.add(scan.chunk.key) || scan.changed
                if (changed) saves.markDirty()
                activeScan = null
            }
        }
    }

    private fun scanNextColumns(level: ClientLevel, scan: ChunkScan, budget: Int): Int {
        val minX = scan.chunk.x * CHUNK_SIZE
        val minZ = scan.chunk.z * CHUNK_SIZE
        val end = minOf(scan.nextColumnIndex + budget, COLUMNS_PER_CHUNK)
        for (index in scan.nextColumnIndex until end) {
            val x = minX + index % CHUNK_SIZE
            val z = minZ + index / CHUNK_SIZE
            val key = columnKey(x, z)
            val surfaceY = findValidSurfaceY(level, x, z)
            if (surfaceY == null) {
                scan.changed = data.validColumns.remove(key) != null || scan.changed
            } else {
                scan.changed = data.validColumns.put(key, surfaceY) != surfaceY || scan.changed
            }
        }
        val scanned = end - scan.nextColumnIndex
        scan.nextColumnIndex = end
        return scanned
    }

    private fun nextLoadedChunk(level: ClientLevel): ChunkKey? {
        val unloadedChunks = mutableListOf<ChunkKey>()
        while (pendingChunks.isNotEmpty()) {
            val chunk = pendingChunks.removeFirst()
            queuedChunks.remove(chunk)
            if (chunk.key in data.observedChunks) continue
            if (isChunkLoaded(level, chunk)) return chunk
            unloadedChunks += chunk
        }
        unloadedChunks.forEach(::enqueue)
        return null
    }

    private fun isChunkLoaded(level: ClientLevel, chunk: ChunkKey): Boolean =
        level.isLoaded(BlockPos(chunk.x * CHUNK_SIZE, CHUNK_LOAD_CHECK_Y, chunk.z * CHUNK_SIZE))

    private fun enqueue(chunk: ChunkKey) {
        if (chunk.key in data.observedChunks || !queuedChunks.add(chunk)) return
        pendingChunks += chunk
    }

    private fun findValidSurfaceY(level: ClientLevel, x: Int, z: Int): Int? {
        for (y in HUB_MAX_Y downTo HUB_MIN_Y) {
            val blockPos = BlockPos(x, y, z)
            if (DianaBurrowSurfaceValidator.isValid(level, blockPos)) return y
        }
        return null
    }

    private fun load(): SurfaceCacheData {
        if (!SkysoftConfigFiles.hasFileOrBackup(cachePath)) return SurfaceCacheData()
        return try {
            SkysoftConfigFiles.readWithBackup(cachePath) { source ->
                Files.newBufferedReader(source).use { reader ->
                    val loaded = gson.fromJson(reader, SurfaceCacheData::class.java)
                        ?: error("Diana Hub surface cache is empty: $source")
                    loaded.takeIf { it.schemaVersion == SCHEMA_VERSION } ?: SurfaceCacheData()
                }
            }
        } catch (e: Exception) {
            SkysoftMod.LOGGER.warn("Failed to load Diana Hub surface cache from {}", cachePath, e)
            SurfaceCacheData()
        }
    }

    private data class ChunkKey(
        val x: Int,
        val z: Int,
    ) {
        val key: String get() = "$x:$z"

        companion object {
            fun fromBlock(x: Int, z: Int): ChunkKey = ChunkKey(Math.floorDiv(x, CHUNK_SIZE), Math.floorDiv(z, CHUNK_SIZE))
        }
    }

    private data class ChunkScan(
        val chunk: ChunkKey,
        var nextColumnIndex: Int = 0,
        var changed: Boolean = false,
    )

    private data class SurfaceCacheData(
        @Expose val schemaVersion: Int = SCHEMA_VERSION,
        @Expose val observedChunks: MutableSet<String> = mutableSetOf(),
        @Expose val validColumns: MutableMap<String, Int> = mutableMapOf(),
    ) {
        fun snapshot(): SurfaceCacheData = copy(
            observedChunks = observedChunks.toMutableSet(),
            validColumns = validColumns.toMutableMap(),
        )
    }

    private const val SCHEMA_VERSION = 1
    private const val CHUNK_SIZE = 16
    private const val CHUNK_LOAD_CHECK_Y = 64
    private const val COLUMNS_PER_CHUNK = CHUNK_SIZE * CHUNK_SIZE
    private const val COLUMN_SCAN_BUDGET = 64
    private const val CACHE_CHUNK_RADIUS = 8
    private const val HUB_MIN_Y = 0
    private const val HUB_MAX_Y = 254
}

private fun columnKey(x: Int, z: Int): String = "$x:$z"
