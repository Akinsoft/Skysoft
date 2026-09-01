package com.skysoft.features.foraging

import com.skysoft.config.SkysoftConfigGui
import com.skysoft.data.ClientEntitySnapshot
import com.skysoft.data.ProfileStorage
import com.skysoft.data.ProfileStorageApi
import com.skysoft.data.SkyBlockIsland
import com.skysoft.utils.EntityUtilities.cleanName
import com.skysoft.utils.SkysoftClientEvents
import com.skysoft.utils.SoundUtilities
import com.skysoft.utils.WorldVec
import com.skysoft.utils.render.BlockHighlightRenderer
import com.skysoft.utils.render.SkysoftRenderContext
import com.skysoft.utils.render.WorldLabelRenderer
import com.skysoft.utils.render.WorldLabelStyle
import com.skysoft.utils.render.WorldRenderDispatcher
import com.skysoft.utils.toWorldVec
import java.awt.Color
import kotlin.math.abs
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.level.block.Blocks

object HoneyhiveHelper {
    private val config get() = SkysoftConfigGui.config().foraging
    private var activeData: ProfileStorage.HoneyhiveTrackerData? = null
    private val alertedReadyHives = mutableSetOf<String>()
    private var ticks = 0
    private var soundsRemaining = 0
    private var soundDelayTicks = 0

    fun register() {
        ProfileStorageApi.registerConsumer("Honeyhive Helper") { config.honeyhiveHelper }
        SkysoftClientEvents.onEndTick(
            "Honeyhive Helper tick",
            isActive = { config.honeyhiveHelper || activeData != null },
        ) { tick() }
        SkysoftClientEvents.onDisconnect("Honeyhive Helper disconnect reset", ::clear)
        WorldRenderDispatcher.registerHandler("Honeyhive Helper rendering", ::isEnabled, ::render)
    }

    private fun tick() {
        if (!config.honeyhiveHelper) {
            clear()
            return
        }
        if (!SkyBlockIsland.TORRHUS_CANYON.isInIsland()) {
            soundsRemaining = 0
            soundDelayTicks = 0
            ticks = 0
            return
        }

        val data = ProfileStorageApi.storage.honeyhiveTracker
        if (activeData !== data) {
            activeData = data
            alertedReadyHives.clear()
        }
        var changed = didInitializeKnownHives(data)
        val now = System.currentTimeMillis()
        if (ticks++ % SCAN_INTERVAL_TICKS == 0) changed = didReconcileVisibleHives(data, now) || changed
        if (changed) ProfileStorageApi.markDirty()
        alertForNewlyReadyHives(data, now)
        playQueuedSound()
    }

    private fun didInitializeKnownHives(data: ProfileStorage.HoneyhiveTrackerData): Boolean {
        if (data.initialized) return false
        val existing = data.hives.mapTo(mutableSetOf(), ProfileStorage.HoneyhiveData::locationKey)
        KNOWN_HONEYHIVES
            .filter { it.locationKey() !in existing }
            .forEach { position ->
                data.hives += ProfileStorage.HoneyhiveData(position.x, position.y, position.z)
            }
        data.initialized = true
        return true
    }

    private fun didReconcileVisibleHives(data: ProfileStorage.HoneyhiveTrackerData, now: Long): Boolean {
        val level = Minecraft.getInstance().level ?: return false
        val armorStands = ClientEntitySnapshot.entities().filterIsInstance<ArmorStand>().filter { it.isAlive }
        val statuses = armorStands.mapNotNull { stand ->
            stand.cleanName().takeIf(String::isHoneyhiveStatus)?.let { status -> stand to status }
        }
        var changed = data.hives.removeIf { hive ->
            val position = BlockPos(hive.x, hive.y, hive.z)
            level.isLoaded(position) && level.getBlockState(position).block != Blocks.BEE_NEST
        }

        armorStands
            .filter { it.cleanName() == HONEYHIVE_NAME }
            .forEach { hiveTag ->
                val status = statuses
                    .filter { (stand, _) -> stand.distanceToSqr(hiveTag) <= STATUS_PAIR_DISTANCE_SQ }
                    .minByOrNull { (stand, _) -> stand.distanceToSqr(hiveTag) }
                    ?.second
                    ?: return@forEach
                val readyAtMillis = status.readyAtMillis(now) ?: return@forEach
                val position = hiveTag.blockPosition().above()
                val hive = data.hives.firstOrNull { it.matches(position) }
                if (hive == null) {
                    data.hives += ProfileStorage.HoneyhiveData(position.x, position.y, position.z, readyAtMillis)
                    changed = true
                } else if (shouldUpdateReadyTime(hive.readyAtMillis, readyAtMillis, now)) {
                    hive.readyAtMillis = readyAtMillis
                    changed = true
                }
            }
        return changed
    }

    private fun alertForNewlyReadyHives(data: ProfileStorage.HoneyhiveTrackerData, now: Long) {
        val ready = data.hives.filterTo(mutableSetOf()) { it.readyAtMillis <= now }.mapTo(mutableSetOf()) { it.locationKey() }
        alertedReadyHives.retainAll(ready)
        val newlyReady = ready - alertedReadyHives
        alertedReadyHives += ready
        if (newlyReady.isNotEmpty() && soundsRemaining == 0) {
            soundsRemaining = READY_SOUND_REPEATS
            soundDelayTicks = 0
        }
    }

    private fun playQueuedSound() {
        if (soundsRemaining == 0 || soundDelayTicks-- > 0) return
        SoundUtilities.playUiSound(READY_SOUND_ID, READY_SOUND_PITCH, READY_SOUND_VOLUME)
        soundsRemaining--
        soundDelayTicks = READY_SOUND_INTERVAL_TICKS
    }

    private fun render(context: SkysoftRenderContext) {
        val now = System.currentTimeMillis()
        activeData?.hives
            ?.asSequence()
            ?.filter { it.readyAtMillis <= now }
            ?.sortedByDescending { hive -> context.camera.position().toWorldVec().distanceSq(hive.location()) }
            ?.forEach { hive ->
                val location = hive.location()
                BlockHighlightRenderer.drawBlock(context, location, WAYPOINT_COLOR, WAYPOINT_FILL_COLOR)
                WorldLabelRenderer.draw(
                    context,
                    location + LABEL_OFFSET,
                    HONEYHIVE_LABEL,
                    WAYPOINT_LABEL_STYLE,
                )
            }
    }

    private fun isEnabled(): Boolean = config.honeyhiveHelper && SkyBlockIsland.TORRHUS_CANYON.isInIsland()

    private fun clear() {
        activeData = null
        alertedReadyHives.clear()
        soundsRemaining = 0
        soundDelayTicks = 0
        ticks = 0
    }

    private fun ProfileStorage.HoneyhiveData.location(): WorldVec = WorldVec(x.toDouble(), y.toDouble(), z.toDouble())
    private fun ProfileStorage.HoneyhiveData.matches(position: BlockPos): Boolean =
        x == position.x && y == position.y && z == position.z

    private fun KnownHoneyhive.locationKey(): String = "$x:$y:$z"

    private const val HONEYHIVE_NAME = "Honeyhive"
    private const val READY_STATUS = "Click to loot!"
    private const val SCAN_INTERVAL_TICKS = 5
    private const val STATUS_PAIR_DISTANCE_SQ = 0.5 * 0.5
    private const val READY_SOUND_ID = "skysoft:honeyhive.ready"
    private const val READY_SOUND_REPEATS = 3
    private const val READY_SOUND_INTERVAL_TICKS = 4
    private const val READY_SOUND_PITCH = 1f
    private const val READY_SOUND_VOLUME = 1f
    private val WAYPOINT_COLOR = Color(255, 170, 0, 230)
    private val WAYPOINT_FILL_COLOR = Color(255, 170, 0, 70)
    private val LABEL_OFFSET = WorldVec(0.5, 1.8, 0.5)
    private val WAYPOINT_LABEL_STYLE = WorldLabelStyle(maxRenderDistance = 100.0, maxScale = 7.0)
    private val HONEYHIVE_LABEL = listOf(
        Component.literal(HONEYHIVE_NAME).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
        Component.literal(READY_STATUS).withStyle(ChatFormatting.GREEN),
    )
    private val KNOWN_HONEYHIVES = listOf(
        KnownHoneyhive(-696, 93, 150),
        KnownHoneyhive(-693, 94, 147),
        KnownHoneyhive(-693, 93, 153),
        KnownHoneyhive(-664, 96, 170),
        KnownHoneyhive(-665, 97, 167),
        KnownHoneyhive(-724, 93, 211),
        KnownHoneyhive(-721, 92, 207),
        KnownHoneyhive(-724, 92, 204),
        KnownHoneyhive(-707, 92, 220),
        KnownHoneyhive(-705, 92, 225),
        KnownHoneyhive(-611, 98, 274),
        KnownHoneyhive(-606, 98, 275),
        KnownHoneyhive(-572, 101, 206),
        KnownHoneyhive(-577, 102, 205),
        KnownHoneyhive(-588, 150, 257),
        KnownHoneyhive(-581, 152, 258),
        KnownHoneyhive(-578, 151, 256),
    )
}

internal fun parseHoneyhiveRefillMillis(text: String): Long? {
    val duration = text.removePrefix("Refill in: ").takeIf { it != text && it.isNotBlank() } ?: return null
    var totalSeconds = 0L
    duration.split(' ').forEach { part ->
        val match = HONEYHIVE_DURATION_PART.matchEntire(part) ?: return null
        val amount = match.groupValues[1].toLongOrNull() ?: return null
        totalSeconds += amount * when (match.groupValues[2]) {
            "h" -> SECONDS_PER_HOUR
            "m" -> SECONDS_PER_MINUTE
            else -> 1L
        }
    }
    return totalSeconds * MILLIS_PER_SECOND
}

private fun String.isHoneyhiveStatus(): Boolean = this == "Click to loot!" || startsWith("Refill in: ")

private fun String.readyAtMillis(now: Long): Long? = when (this) {
    "Click to loot!" -> 0L
    else -> parseHoneyhiveRefillMillis(this)?.let { now + it }
}

private fun shouldUpdateReadyTime(current: Long, observed: Long, now: Long): Boolean =
    (current <= now) != (observed <= now) ||
        observed > now && abs(current - observed) > READY_TIME_TOLERANCE_MILLIS

private const val SECONDS_PER_MINUTE = 60L
private const val SECONDS_PER_HOUR = 60L * SECONDS_PER_MINUTE
private const val MILLIS_PER_SECOND = 1_000L
private const val READY_TIME_TOLERANCE_MILLIS = 2_000L
private val HONEYHIVE_DURATION_PART = Regex("""(\d+)([hms])""")

private data class KnownHoneyhive(val x: Int, val y: Int, val z: Int)
