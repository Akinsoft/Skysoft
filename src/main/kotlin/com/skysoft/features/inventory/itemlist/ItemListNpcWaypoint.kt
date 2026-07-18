package com.skysoft.features.inventory.itemlist

import com.skysoft.data.SkyBlockIsland
import com.skysoft.data.hypixel.HypixelLocationState
import com.skysoft.data.skyblock.SkyBlockDataRepository
import com.skysoft.data.skyblock.SkyBlockEntityInfo
import com.skysoft.data.skyblock.SkyBlockEntityStacks
import com.skysoft.data.skyblock.SkyBlockEventAvailability
import com.skysoft.data.skyblock.SkyBlockEventState
import com.skysoft.data.skyblock.SkyBlockWarpPoint
import com.skysoft.utils.WorldVec
import com.skysoft.utils.toWorldVec
import com.skysoft.utils.render.SkysoftRenderContext
import com.skysoft.utils.render.WorldLabelRenderer
import com.skysoft.utils.render.WorldLabelStyle
import com.skysoft.utils.render.WorldRenderDispatcher
import com.skysoft.utils.chat.ChatEvents
import com.skysoft.utils.chat.ChatMessageVisibility
import com.skysoft.utils.SkysoftClientEvents
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component

internal object ItemListNpcWaypoint {
    private var pendingWarp: PendingNpcWarp? = null
    private var activeWaypoint: NpcWaypoint? = null
    private val warpFailures = mutableMapOf<String, Long>()

    fun register() {
        SkyBlockEventState.registerConsumer("Item List NPC Waypoint", ::hasActiveState)
        SkysoftClientEvents.onEndTick("Item List NPC Waypoint tick", ::hasActiveState) { tick() }
        SkysoftClientEvents.onDisconnect("Item List NPC Waypoint disconnect reset", ::clear)
        ChatEvents.onVisibleMessage("Item List waypoint chat", ::hasPendingWarp) { message ->
            recordWarpFailure(message.body)
            ChatMessageVisibility.SHOW
        }
        WorldRenderDispatcher.registerHandler(
            "Item List waypoint rendering",
            isActive = { activeWaypoint != null },
            handler = ::renderWorld,
        )
    }

    private fun hasPendingWarp(): Boolean = pendingWarp != null

    private fun hasActiveState(): Boolean = pendingWarp != null || activeWaypoint != null

    fun requestWarp(entityId: String): NpcWarpRequestResult {
        if (!HypixelLocationState.inSkyBlock) return NpcWarpRequestResult.REJECTED
        if (!isNpcAvailable(entityId)) return NpcWarpRequestResult.REJECTED
        val minecraft = Minecraft.getInstance()
        val connection = minecraft.connection ?: return NpcWarpRequestResult.REJECTED
        val player = minecraft.player ?: return NpcWarpRequestResult.REJECTED
        val target = resolveNpcWarp(entityId) ?: return NpcWarpRequestResult.REJECTED
        val now = System.currentTimeMillis()
        if (HypixelLocationState.currentIsland == target.island) {
            return target.position?.let { position ->
                pendingWarp = null
                warpFailures.remove(entityId)
                activeWaypoint = NpcWaypoint(entityId, target.island, position, now)
                NpcWarpRequestResult.WAYPOINT_ACTIVATED
            } ?: NpcWarpRequestResult.REJECTED
        }
        connection.sendCommand("warp ${target.warp.command}")
        pendingWarp = PendingNpcWarp(
            entityId = entityId,
            island = target.island,
            target = target.position,
            warp = target.warp,
            startingIsland = HypixelLocationState.currentIsland,
            startingLocationVersion = HypixelLocationState.locationVersion,
            startingPosition = player.position().toWorldVec(),
            sentAtMillis = now,
        )
        activeWaypoint = null
        return NpcWarpRequestResult.WARP_SENT
    }

    private fun resolveNpcWarp(entityId: String): ResolvedNpcWarp? {
        val entity = SkyBlockDataRepository.entity(entityId)?.takeIf(SkyBlockEntityInfo::canNavigateToEntity) ?: return null
        val island = entity.island ?: return null
        val warp = SkyBlockDataRepository.ViewerData.bestWarpFor(entityId) ?: return null
        return ResolvedNpcWarp(island, entity.position, warp)
    }

    private fun tick() {
        val minecraft = Minecraft.getInstance()
        val playerPosition = minecraft.player?.position()?.toWorldVec()
        val now = System.currentTimeMillis()
        warpFailures.entries.removeIf { it.value <= now }
        pendingWarp?.let { pending ->
            when {
                now - pending.sentAtMillis > WARP_CONFIRMATION_TIMEOUT_MILLIS -> {
                    pendingWarp = null
                }
                isNpcWarpConfirmed(
                    pending.island,
                    HypixelLocationState.currentIsland,
                    pending.startingIsland,
                    pending.startingLocationVersion,
                    HypixelLocationState.locationVersion,
                    pending.startingPosition,
                    playerPosition,
                    pending.warp.position,
                ) -> {
                    activeWaypoint = pending.target?.let { target ->
                        NpcWaypoint(
                            entityId = pending.entityId,
                            island = pending.island,
                            target = target,
                            createdAtMillis = now,
                        )
                    }
                    pendingWarp = null
                }
            }
        }
        activeWaypoint?.let { waypoint ->
            if (shouldRemoveNpcWaypoint(
                    waypoint.island,
                    HypixelLocationState.currentIsland,
                    waypoint.target,
                    playerPosition,
                    waypoint.createdAtMillis,
                    now,
                )
            ) {
                activeWaypoint = null
            }
        }
    }

    private fun renderWorld(context: SkysoftRenderContext) {
        val waypoint = activeWaypoint ?: return
        if (HypixelLocationState.currentIsland != waypoint.island) return
        val entity = SkyBlockDataRepository.entity(waypoint.entityId) ?: return
        val texture = SkyBlockEntityStacks.skinTexture(waypoint.entityId) ?: return
        WorldLabelRenderer.drawHeadLabel(
            context,
            waypoint.target.up(WAYPOINT_HEIGHT),
            texture,
            Component.literal(entity.name).withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD),
            WAYPOINT_STYLE,
        )
    }

    private fun clear() {
        pendingWarp = null
        activeWaypoint = null
        warpFailures.clear()
    }

    fun warpActionLabel(entityId: String, now: Long = System.currentTimeMillis()): String {
        val entity = SkyBlockDataRepository.entity(entityId)
        if (entity?.availability?.let { SkyBlockEventState.availability(it, now) } == SkyBlockEventAvailability.INACTIVE) {
            return "§c§lON VACATION"
        }
        return if ((warpFailures[entityId] ?: 0L) > now) "§c§lWARP NOT UNLOCKED" else "§e§lWARP"
    }

    private fun isNpcAvailable(entityId: String, now: Long = System.currentTimeMillis()): Boolean =
        SkyBlockDataRepository.entity(entityId)?.availability?.let {
            SkyBlockEventState.availability(it, now) != SkyBlockEventAvailability.INACTIVE
        } != false

    private fun recordWarpFailure(message: String) {
        if (!message.contains(WARP_NOT_UNLOCKED_MESSAGE, ignoreCase = true)) return
        val pending = pendingWarp ?: return
        if (System.currentTimeMillis() - pending.sentAtMillis > WARP_FAILURE_WINDOW_MILLIS) return
        warpFailures[pending.entityId] = System.currentTimeMillis() + WARP_FAILURE_LABEL_MILLIS
        pendingWarp = null
        activeWaypoint = null
    }

    private data class PendingNpcWarp(
        val entityId: String,
        val island: SkyBlockIsland,
        val target: WorldVec?,
        val warp: SkyBlockWarpPoint,
        val startingIsland: SkyBlockIsland?,
        val startingLocationVersion: Long,
        val startingPosition: WorldVec,
        val sentAtMillis: Long,
    )

    private data class ResolvedNpcWarp(
        val island: SkyBlockIsland,
        val position: WorldVec?,
        val warp: SkyBlockWarpPoint,
    )

    private data class NpcWaypoint(
        val entityId: String,
        val island: SkyBlockIsland,
        val target: WorldVec,
        val createdAtMillis: Long,
    )

    private const val WARP_CONFIRMATION_TIMEOUT_MILLIS = 8_000L
    private const val WAYPOINT_HEIGHT = 2.3
    private const val WAYPOINT_RENDER_DISTANCE = 160.0
    private const val WARP_FAILURE_WINDOW_MILLIS = 5_000L
    private const val WARP_FAILURE_LABEL_MILLIS = 5_000L
    private const val WARP_NOT_UNLOCKED_MESSAGE = "haven't unlocked this fast travel destination"
    private val WAYPOINT_STYLE = WorldLabelStyle(maxRenderDistance = WAYPOINT_RENDER_DISTANCE, maxScale = 7.0)
}

internal enum class NpcWarpRequestResult {
    WARP_SENT,
    WAYPOINT_ACTIVATED,
    REJECTED,
}

internal fun isNpcWarpConfirmed(
    targetIsland: SkyBlockIsland,
    currentIsland: SkyBlockIsland?,
    startingIsland: SkyBlockIsland?,
    startingLocationVersion: Long,
    currentLocationVersion: Long,
    startingPosition: WorldVec,
    currentPosition: WorldVec?,
    warpPosition: WorldVec,
): Boolean {
    if (currentIsland != targetIsland) return false
    if (currentLocationVersion > startingLocationVersion) return true
    if (startingIsland != targetIsland || currentPosition == null) return false
    val movedDistanceSq = currentPosition.distanceSq(startingPosition)
    val warpDistanceSq = currentPosition.distanceSq(warpPosition)
    return movedDistanceSq >= MINIMUM_WARP_MOVEMENT_SQ && warpDistanceSq <= WARP_ARRIVAL_RANGE_SQ
}

internal fun shouldRemoveNpcWaypoint(
    targetIsland: SkyBlockIsland,
    currentIsland: SkyBlockIsland?,
    target: WorldVec,
    currentPosition: WorldVec?,
    createdAtMillis: Long,
    now: Long,
): Boolean {
    if (currentIsland != targetIsland) return true
    if (now - createdAtMillis >= WAYPOINT_LIFETIME_MILLIS) return true
    return currentPosition?.distanceSq(target)?.let { it <= NPC_ARRIVAL_RANGE_SQ } == true
}

private const val MINIMUM_WARP_MOVEMENT_SQ = 12.0 * 12.0
private const val WARP_ARRIVAL_RANGE_SQ = 40.0 * 40.0
private const val NPC_ARRIVAL_RANGE_SQ = 6.0 * 6.0
private const val WAYPOINT_LIFETIME_MILLIS = 120_000L
