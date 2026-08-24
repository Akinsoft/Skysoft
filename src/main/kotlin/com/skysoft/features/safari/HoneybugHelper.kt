package com.skysoft.features.safari

import com.skysoft.config.SkysoftConfigGui
import com.skysoft.data.SkyBlockIsland
import com.skysoft.data.hypixel.HypixelLocationState
import com.skysoft.events.input.BlockInteractionEvents
import com.skysoft.utils.EntityUtilities.cleanName
import com.skysoft.utils.SkysoftClientEvents
import com.skysoft.utils.render.BlockHighlightRenderer
import com.skysoft.utils.render.SkysoftRenderContext
import com.skysoft.utils.render.WorldRenderDispatcher
import com.skysoft.utils.toWorldVec
import java.awt.Color
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.core.SectionPos
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.level.ClipContext
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.chunk.LevelChunk
import net.minecraft.world.level.chunk.status.ChunkStatus
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3

object HoneybugHelper {
    private val config get() = SkysoftConfigGui.config().safari.honeybugHelper
    private val visibleHives = mutableSetOf<BlockPos>()
    private val searchedHives = mutableSetOf<BlockPos>()
    private val seenHives = mutableSetOf<BlockPos>()
    private var locationVersion = Long.MIN_VALUE
    private var wasEnabled = false

    fun register() {
        BlockInteractionEvents.register(
            "Honeybug Helper block interaction",
            isActive = ::isEnabled,
        ) { event ->
            markSearched(BlockPos.containing(event.position.x, event.position.y, event.position.z))
            false
        }
        ClientChunkEvents.CHUNK_LOAD.register { _, chunk ->
            if (isEnabled()) {
                resetForLocationChange()
                scanChunkHives(chunk, visibleHives)
            }
        }
        ClientChunkEvents.CHUNK_UNLOAD.register { _, chunk ->
            visibleHives.removeIf { position -> chunk.pos.contains(position) }
        }
        SkysoftClientEvents.onEndTick(
            "Honeybug Helper tick",
            isActive = { config.enabled || wasEnabled || visibleHives.isNotEmpty() },
        ) { tick() }
        SkysoftClientEvents.onDisconnect("Honeybug Helper disconnect reset", ::clear)
        WorldRenderDispatcher.registerHandler("Honeybug Helper rendering", ::isEnabled, ::render)
    }

    private fun tick() {
        resetForLocationChange()
        val enabled = isEnabled()
        if (enabled && !wasEnabled) scanLoadedHives()
        if (!enabled) visibleHives.clear()
        wasEnabled = enabled
    }

    private fun scanLoadedHives() {
        val minecraft = Minecraft.getInstance()
        val level = minecraft.level ?: return
        val center = minecraft.player?.chunkPosition() ?: return
        val radius = minecraft.options.effectiveRenderDistance
        val found = mutableSetOf<BlockPos>()
        for (chunkX in center.x - radius..center.x + radius) {
            for (chunkZ in center.z - radius..center.z + radius) {
                val chunk = level.chunkSource.getChunk(chunkX, chunkZ, ChunkStatus.FULL, false) ?: continue
                scanChunkHives(chunk, found)
            }
        }
        visibleHives.clear()
        visibleHives += found
    }

    private fun scanChunkHives(chunk: LevelChunk, found: MutableSet<BlockPos>) {
        val minX = chunk.pos.minBlockX
        val minZ = chunk.pos.minBlockZ
        chunk.sections.forEachIndexed { sectionIndex, section ->
            if (!section.maybeHas { state -> state.block == Blocks.BEE_NEST }) return@forEachIndexed
            val minY = SectionPos.sectionToBlockCoord(chunk.getSectionYFromSectionIndex(sectionIndex))
            for (x in 0 until SectionPos.SECTION_SIZE) {
                for (y in 0 until SectionPos.SECTION_SIZE) {
                    for (z in 0 until SectionPos.SECTION_SIZE) {
                        if (section.getBlockState(x, y, z).block != Blocks.BEE_NEST) continue
                        val position = BlockPos(minX + x, minY + y, minZ + z)
                        if (position !in searchedHives) found += position
                    }
                }
            }
        }
    }

    private fun markSearched(position: BlockPos) {
        resetForLocationChange()
        val level = Minecraft.getInstance().level ?: return
        if (level.getBlockState(position).block != Blocks.BEE_NEST) return
        searchedHives += position
        visibleHives -= position
        seenHives -= position
    }

    private fun render(context: SkysoftRenderContext) {
        markSeenHives(context)
        visibleHives.forEach { position ->
            BlockHighlightRenderer.drawBlock(
                context,
                position.toWorldVec(),
                HIVE_COLOR,
                HIVE_FILL_COLOR,
                depth = position !in seenHives,
            )
        }
        if (!config.details.crosshairLine) return
        val player = Minecraft.getInstance().player ?: return
        val honeybug = Minecraft.getInstance().level
            ?.entitiesForRendering()
            ?.asSequence()
            ?.filterIsInstance<ArmorStand>()
            ?.filter { stand -> stand.isAlive && stand.cleanName().contains(HONEYBUG_NAME) }
            ?.minByOrNull { stand -> stand.distanceToSqr(player) }
            ?: return
        context.drawLineToCrosshair(
            honeybug.getPosition(context.partialTicks).toWorldVec(),
            HIVE_COLOR,
            depth = true,
        )
    }

    private fun markSeenHives(context: SkysoftRenderContext) {
        val level = Minecraft.getInstance().level ?: return
        val cameraEntity = context.camera.entity() ?: return
        val cameraPosition = context.camera.position()
        visibleHives
            .asSequence()
            .filter { position -> position !in seenHives && context.camera.cullFrustum.isVisible(AABB(position)) }
            .filter { position ->
                HIVE_SIGHT_POINTS.any { point ->
                    val hit = level.clip(
                        ClipContext(
                            cameraPosition,
                            Vec3(position.x + point.x, position.y + point.y, position.z + point.z),
                            ClipContext.Block.VISUAL,
                            ClipContext.Fluid.NONE,
                            cameraEntity,
                        ),
                    )
                    hit.type == HitResult.Type.BLOCK && hit.blockPos == position
                }
            }
            .forEach(seenHives::add)
    }

    private fun isEnabled(): Boolean = config.enabled && SkyBlockIsland.SAFARI.isInIsland()

    private fun resetForLocationChange() {
        if (locationVersion == HypixelLocationState.locationVersion) return
        clear()
        locationVersion = HypixelLocationState.locationVersion
    }

    private fun clear() {
        visibleHives.clear()
        searchedHives.clear()
        seenHives.clear()
        wasEnabled = false
    }

    private const val HONEYBUG_NAME = "Honeybug"
    private val HIVE_SIGHT_POINTS = listOf(
        Vec3(0.5, 0.5, 0.5),
        Vec3(0.05, 0.5, 0.5),
        Vec3(0.95, 0.5, 0.5),
        Vec3(0.5, 0.05, 0.5),
        Vec3(0.5, 0.95, 0.5),
        Vec3(0.5, 0.5, 0.05),
        Vec3(0.5, 0.5, 0.95),
    )
    private val HIVE_COLOR = Color(255, 170, 0, 230)
    private val HIVE_FILL_COLOR = Color(255, 170, 0, 70)
}
