package com.skysoft.features.foraging

import com.skysoft.SkysoftMod
import com.skysoft.config.SkysoftConfigGui
import com.skysoft.data.SkyBlockIsland
import com.skysoft.features.combat.SkyBlockMobEntityMatcher
import com.skysoft.features.combat.SkyBlockMobSignal
import com.skysoft.utils.SkysoftClientEvents
import com.skysoft.utils.WorldVec
import com.skysoft.utils.chat.ChatEvents
import com.skysoft.utils.chat.ChatMessageVisibility
import com.skysoft.utils.render.ScreenAlert
import com.skysoft.utils.render.ScreenAlertRenderer
import com.skysoft.utils.render.ScreenAlertSound
import com.skysoft.utils.render.ScreenTitleLine
import com.skysoft.utils.render.SkysoftRenderContext
import com.skysoft.utils.render.WorldRenderDispatcher
import com.skysoft.utils.toWorldVec
import java.awt.Color
import java.util.UUID
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.sounds.SoundEvent

object QueenAntWarning {
    private val config get() = SkysoftConfigGui.config().foraging.queenAntWarning
    private var pendingSearch: PendingQueenAntSearch? = null
    private var activeQueenId: UUID? = null
    private var activeQueen: SkyBlockMobSignal? = null

    fun register() {
        ChatEvents.onVisibleMessage(
            "Queen Ant Warning chat",
            isActive = ::isEnabled,
        ) { message ->
            if (message.isSystemLike && message.cleanText == ANTHILL_DESTROYED_MESSAGE) beginSearch()
            ChatMessageVisibility.SHOW
        }
        SkysoftClientEvents.onEndTick(
            "Queen Ant Warning tick",
            isActive = { isEnabled() || pendingSearch != null || activeQueenId != null },
        ) { tick() }
        SkysoftClientEvents.onDisconnect("Queen Ant Warning disconnect reset", ::clear)
        WorldRenderDispatcher.registerHandler(
            "Queen Ant Warning line rendering",
            isActive = { isEnabled() && config.details.crosshairLine && activeQueen != null },
            handler = ::renderWorld,
        )
    }

    private fun beginSearch() {
        if (activeQueenId != null) return
        val playerLocation = Minecraft.getInstance().player?.position()?.toWorldVec() ?: return
        pendingSearch = PendingQueenAntSearch(
            playerLocation = playerLocation,
            existingQueenIds = queenAntSignals().mapNotNullTo(mutableSetOf()) { signal -> signal.trackingId() },
            expiresAtMillis = System.currentTimeMillis() + SEARCH_DURATION_MILLIS,
        )
    }

    private fun tick() {
        if (!isEnabled()) {
            clear()
            return
        }

        val signalsById = queenAntSignals().mapNotNull { signal -> signal.trackingId()?.let { it to signal } }.toMap()
        activeQueenId?.let { queenId ->
            activeQueen = signalsById[queenId]
            if (activeQueen != null) return
            activeQueenId = null
        }

        val search = pendingSearch ?: return
        if (System.currentTimeMillis() >= search.expiresAtMillis) {
            pendingSearch = null
            return
        }
        val queenId = selectNewQueenAntId(
            queens = signalsById.mapValues { (_, signal) -> signal.location },
            existingQueenIds = search.existingQueenIds,
            playerLocation = search.playerLocation,
        ) ?: return

        activeQueenId = queenId
        activeQueen = signalsById[queenId]
        pendingSearch = null
        showTitle()
    }

    private fun renderWorld(context: SkysoftRenderContext) {
        val entity = activeQueen?.entity ?: activeQueen?.nameplate ?: return
        context.drawLineToCrosshair(entity.getPosition(context.partialTicks).toWorldVec(), QUEEN_ANT_COLOR)
    }

    private fun showTitle() {
        ScreenAlertRenderer.show(
            ScreenAlert(
                id = ALERT_ID,
                lines = listOf(
                    ScreenTitleLine(
                        Component.literal(TITLE).withStyle(ChatFormatting.RED, ChatFormatting.BOLD),
                        TITLE_SCALE,
                    ),
                ),
                durationMillis = TITLE_DURATION_MILLIS,
                sound = ScreenAlertSound(
                    event = SoundEvent.createVariableRangeEvent(SkysoftMod.id(ALERT_SOUND_ID)),
                    pitch = ALERT_SOUND_PITCH,
                    volume = ALERT_SOUND_VOLUME,
                    plays = ALERT_SOUND_PLAYS,
                    repeatIntervalMillis = ALERT_SOUND_REPEAT_INTERVAL_MILLIS,
                ),
            ),
        )
    }

    private fun queenAntSignals(): List<SkyBlockMobSignal> =
        SkyBlockMobEntityMatcher.visibleSignals(listOf(QUEEN_ANT_NAME))

    private fun SkyBlockMobSignal.trackingId(): UUID? = nameplate?.uuid ?: entity?.uuid

    private fun isEnabled(): Boolean = config.enabled && SkyBlockIsland.TORRHUS_CANYON.isInIsland()

    private fun clear() {
        pendingSearch = null
        activeQueenId = null
        activeQueen = null
        ScreenAlertRenderer.clear(ALERT_ID)
    }

    private const val ANTHILL_DESTROYED_MESSAGE = "You destroyed an anthill, angering the ants inside!"
    private const val QUEEN_ANT_NAME = "Queen Ant"
    private const val TITLE = "Queen Ant Found!"
    private const val ALERT_ID = "queen_ant_warning"
    private const val ALERT_SOUND_ID = "queen_ant.found"
    private const val SEARCH_DURATION_MILLIS = 3_000L
    private const val TITLE_DURATION_MILLIS = 2_500L
    private const val ALERT_SOUND_PLAYS = 3
    private const val ALERT_SOUND_REPEAT_INTERVAL_MILLIS = 450L
    private const val ALERT_SOUND_PITCH = 1.0f
    private const val ALERT_SOUND_VOLUME = 1.0f
    private const val TITLE_SCALE = 2.7f
    private val QUEEN_ANT_COLOR = Color(255, 85, 85, 230)
}

internal fun selectNewQueenAntId(
    queens: Map<UUID, WorldVec>,
    existingQueenIds: Set<UUID>,
    playerLocation: WorldVec,
): UUID? = queens.entries
    .asSequence()
    .filter { (id, location) -> id !in existingQueenIds && location.distanceSq(playerLocation) <= SEARCH_DISTANCE_SQ }
    .minByOrNull { (_, location) -> location.distanceSq(playerLocation) }
    ?.key

private data class PendingQueenAntSearch(
    val playerLocation: WorldVec,
    val existingQueenIds: Set<UUID>,
    val expiresAtMillis: Long,
)

private const val SEARCH_DISTANCE_SQ = 10.0 * 10.0
