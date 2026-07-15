package com.skysoft.features.fishing

import com.skysoft.config.SkysoftConfigGui
import com.skysoft.config.FishingHotspotType
import com.skysoft.data.hypixel.HypixelLocationState
import com.skysoft.utils.chat.ChatSenderParser
import com.skysoft.utils.chat.ChatMessageSender
import com.skysoft.utils.chat.ChatEvents
import com.skysoft.utils.chat.ChatMessage
import com.skysoft.utils.chat.ChatMessageVisibility
import com.skysoft.utils.chat.SkysoftPartyShare
import com.skysoft.utils.render.SkysoftRenderContext
import com.skysoft.utils.render.WorldRenderDispatcher
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.world.item.Items

object FishingHotspotSharing {
    private val config get() = SkysoftConfigGui.config().fishing.hotspotSharing
    private val hotspotTracker = FishingHotspotTracker()
    private val sharedHotspots = mutableMapOf<String, TimedHotspot>()
    private val pendingShares = mutableMapOf<FishingHotspotId, PendingHotspot>()
    private val waypointHotspots = mutableMapOf<String, FishingHotspotWaypoint>()
    private var ticks = 0

    fun register() {
        ClientTickEvents.END_CLIENT_TICK.register { onTick() }
        ChatEvents.onPartyMessage { message ->
            if (receivePartyMessage(message) == null) {
                ChatMessageVisibility.SHOW
            } else {
                ChatMessageVisibility.HIDE
            }
        }
        ClientPlayConnectionEvents.DISCONNECT.register { _, _ -> clear() }
        WorldRenderDispatcher.registerHandler(::onRenderWorld)
    }

    private fun onTick() {
        if (!HypixelLocationState.inSkyBlock) {
            clear()
            return
        }
        prune()
        if (!isAllowedIsland()) {
            clear()
            return
        }
        if (!config.shareHotspots) {
            clearLocalTracking()
            return
        }
        if (++ticks % SCAN_INTERVAL_TICKS == 0) scanHotspots()
        sendReadyPendingShares()
    }

    private fun scanHotspots() {
        val now = System.currentTimeMillis()
        val playerLocation = FishingHotspotDetector.playerLocation()
        val nearestHotspotId = playerLocation?.let(hotspotTracker::nearestHotspotId)
        val change = hotspotTracker.reconcile(
            observations = FishingHotspotDetector.detect(),
            isLocationLoaded = FishingHotspotDetector::isLocationLoaded,
            now = now,
        )
        change.closed.forEach { hotspot ->
            pendingShares.remove(hotspot.id)
            sharedHotspots.entries.removeIf { (_, recent) -> recent.share.matches(hotspot.share) }
        }
        if (shouldShowFishingHotspotClosedTitle(nearestHotspotId, change.closed)) {
            FishingHotspotClosedTitle.show()
        }
        hotspotTracker.activeHotspots()
            .filter { hotspot -> hotspot.isVisible && !hotspot.isAnnounced }
            .forEach { hotspot -> queueHotspotShare(hotspot, now) }
    }

    internal fun receivePartyMessage(message: ChatMessage): FishingHotspotShare? {
        if (!isAllowedIsland()) return null
        val share = FishingHotspotShareParser.parse(message.body) ?: return null
        val now = System.currentTimeMillis()
        val sharedBy = ChatSenderParser.senderBefore(message, FishingHotspotShareParser.SHARE_MARKER)
            ?: return null
        rememberRecentSharedHotspot(share, now)
        if (config.shareHotspots) {
            val result = hotspotTracker.recordPartyShare(
                share = share,
                playerLocation = FishingHotspotDetector.playerLocation(),
                now = now,
            )
            result.announcedHotspotId?.let(pendingShares::remove)
        }
        val hotspotType = FishingHotspotType.fromStat(share.cleanStat)
        if (hotspotType == null) {
            return null
        }
        if (hotspotType !in config.settings.receivedHotspots.get()) {
            return null
        }
        rememberSharedHotspotWaypoint(share, sharedBy, now)
        SkysoftPartyShare.showFoundReplacement(
            sender = sharedBy,
            label = Component.literal("Hotspot").withStyle(ChatFormatting.LIGHT_PURPLE),
            location = share.location,
            detail = Component.literal("(${share.cleanStat})").withStyle(ChatFormatting.AQUA),
        )
        return share
    }

    internal fun sharedWaypoints(): List<FishingHotspotWaypoint> =
        waypointHotspots.values.toList()

    private fun onRenderWorld(context: SkysoftRenderContext) {
        if (!config.settings.showSharedWaypoints || !isAllowedIsland()) return
        prune()
        FishingHotspotWaypointRenderer.renderWorld(
            context = context,
            waypoints = sharedWaypoints(),
            drawCrosshairLine = config.details.crosshairLine,
            boldLabel = config.details.boldText,
            labelFormat = config.details.labelFormat,
        )
    }

    private fun queueHotspotShare(hotspot: TrackedFishingHotspot, now: Long) {
        if (!canShare(hotspot.share)) {
            discardFilteredShare(hotspot)
            return
        }
        if (hasRecentShare(hotspot.share)) {
            hotspotTracker.markAnnounced(hotspot.id)
            return
        }
        if (hotspot.id in pendingShares) return
        pendingShares[hotspot.id] = PendingHotspot(
            hotspotId = hotspot.id,
            readyAtMillis = now + PENDING_SHARE_DELAY_MILLIS,
            expiresAtMillis = now + PENDING_SHARE_EXPIRE_MILLIS,
        )
    }

    private fun sendReadyPendingShares() {
        val now = System.currentTimeMillis()
        val ready = pendingShares.values.filter { it.readyAtMillis <= now }
        if (ready.isNotEmpty() && !holdingFishingRod()) return
        ready.forEach { pending ->
            val hotspot = hotspotTracker.activeHotspot(pending.hotspotId)
            if (hotspot == null || hotspot.isAnnounced) {
                pendingShares.remove(pending.hotspotId)
                return@forEach
            }
            if (!hotspot.isVisible) return@forEach
            if (!canShare(hotspot.share)) {
                discardFilteredShare(hotspot)
                return@forEach
            }
            if (hasRecentShare(hotspot.share)) {
                pendingShares.remove(pending.hotspotId)
                hotspotTracker.markAnnounced(hotspot.id)
                return@forEach
            }
            if (shareHotspot(hotspot) == HotspotShareResult.SHARED) pendingShares.remove(pending.hotspotId)
        }
    }

    private fun shareHotspot(hotspot: TrackedFishingHotspot): HotspotShareResult {
        val minecraft: Minecraft? = Minecraft.getInstance()
        val playerName = minecraft?.player?.gameProfile?.name
            ?: return HotspotShareResult.PLAYER_UNAVAILABLE
        hotspotTracker.markAnnounced(hotspot.id)
        val now = System.currentTimeMillis()
        val sharedBy = ChatMessageSender(playerName, null)
        rememberRecentSharedHotspot(hotspot.share, now)
        rememberSharedHotspotWaypoint(hotspot.share, sharedBy, now)
        val message = FishingHotspotShareParser.format(hotspot.share)
        SkysoftPartyShare.sendParty(message)
        return HotspotShareResult.SHARED
    }

    private fun rememberRecentSharedHotspot(share: FishingHotspotShare, now: Long) {
        sharedHotspots[share.key] = TimedHotspot(share, now + SHARE_SUPPRESS_MILLIS)
    }

    private fun rememberSharedHotspotWaypoint(
        share: FishingHotspotShare,
        sharedBy: ChatMessageSender,
        now: Long,
    ) {
        waypointHotspots.entries.removeIf { it.value.share.matches(share) }
        waypointHotspots[share.key] = FishingHotspotWaypoint(
            share = share,
            sharedBy = sharedBy,
            expiresAtMillis = now + FISHING_HOTSPOT_WAYPOINT_LIFETIME_MILLIS,
        )
    }

    private fun canShare(share: FishingHotspotShare): Boolean =
        FishingHotspotType.fromStat(share.cleanStat) in config.settings.sharedHotspots.get()

    private fun discardFilteredShare(hotspot: TrackedFishingHotspot) {
        pendingShares.remove(hotspot.id)
        hotspotTracker.markAnnounced(hotspot.id)
    }

    private fun hasRecentShare(share: FishingHotspotShare): Boolean =
        sharedHotspots.values.any { it.share.matches(share) }

    private fun holdingFishingRod(): Boolean {
        val player = Minecraft.getInstance().player ?: return false
        return player.mainHandItem.item == Items.FISHING_ROD ||
            player.offhandItem.item == Items.FISHING_ROD
    }

    private fun prune() {
        val now = System.currentTimeMillis()
        sharedHotspots.entries.removeIf { it.value.expiresAtMillis <= now }
        pendingShares.entries.removeIf { it.value.expiresAtMillis <= now }
        val playerLocation = FishingHotspotDetector.playerLocation()
        waypointHotspots.entries.removeIf { (_, waypoint) ->
            shouldRemoveFishingHotspotWaypoint(waypoint, playerLocation, now)
        }
    }

    private fun clear() {
        clearLocalTracking()
        sharedHotspots.clear()
        waypointHotspots.clear()
    }

    private fun clearLocalTracking() {
        hotspotTracker.reset()
        pendingShares.clear()
        FishingHotspotClosedTitle.clear()
        ticks = 0
    }

    private fun isAllowedIsland(): Boolean =
        HypixelLocationState.inSkyBlock &&
            HypixelLocationState.currentIsland in FISHING_HOTSPOT_ISLANDS

    private data class TimedHotspot(
        val share: FishingHotspotShare,
        val expiresAtMillis: Long,
    )

    private data class PendingHotspot(
        val hotspotId: FishingHotspotId,
        val readyAtMillis: Long,
        val expiresAtMillis: Long,
    )

    private enum class HotspotShareResult {
        SHARED,
        PLAYER_UNAVAILABLE,
    }

    private const val SCAN_INTERVAL_TICKS = 20
    private const val SHARE_SUPPRESS_MILLIS = 10 * 60 * 1000L
    private const val PENDING_SHARE_DELAY_MILLIS = 2_000L
    private const val PENDING_SHARE_EXPIRE_MILLIS = 60_000L
}
