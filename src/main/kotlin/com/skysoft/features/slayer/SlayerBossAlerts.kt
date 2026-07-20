package com.skysoft.features.slayer

import com.skysoft.config.SkysoftConfigGui
import com.skysoft.data.hypixel.HypixelLocationState
import com.skysoft.data.skyblock.SlayerMessageParser
import com.skysoft.data.skyblock.SlayerQuestState
import com.skysoft.utils.SkysoftClientEvents
import com.skysoft.utils.chat.ChatEvents
import com.skysoft.utils.chat.ChatMessageVisibility
import com.skysoft.utils.render.ChromaTextRendering
import com.skysoft.utils.render.ScreenAlert
import com.skysoft.utils.render.ScreenAlertRenderer
import com.skysoft.utils.render.ScreenAlertSound
import com.skysoft.utils.render.ScreenTitleLine
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import net.minecraft.resources.Identifier
import net.minecraft.sounds.SoundEvent

object SlayerBossAlerts {
    private val config get() = SkysoftConfigGui.config().slayer.bossAlerts
    private var wasBossActive: Boolean? = null

    fun register() {
        ChatEvents.onVisibleMessage(
            "Slayer Boss Cocoon Alert chat",
            isActive = { shouldTrackBossCocoon() },
        ) { message ->
            if (message.isSystemLike && SlayerMessageParser.isBossCocooned(message.cleanText)) {
                showAlert(
                    title = BOSS_COCOONED_TITLE,
                    visible = config.settings.showCocoonAlert,
                    sound = alertSound(
                        config.settings.playCocoonSound,
                        BOSS_COCOON_SOUND_ID,
                        BOSS_COCOON_SOUND_PITCH,
                        BOSS_COCOON_SOUND_VOLUME,
                        ALERT_SOUND_REPEAT_INTERVAL_MILLIS,
                    ),
                )
            }
            ChatMessageVisibility.SHOW
        }
        SkysoftClientEvents.onEndTick(
            "Slayer Boss Spawn Alert scoreboard",
            isActive = { shouldTrackBossSpawn() || wasBossActive != null },
        ) { updateBossState() }
        SkysoftClientEvents.onDisconnect("Slayer Boss Alerts disconnect reset", ::clear)
    }

    private fun updateBossState() {
        if (!shouldTrackBossSpawn()) {
            wasBossActive = null
            return
        }
        val bossActive = SlayerQuestState.isBossActive
        if (didSlayerBossSpawn(wasBossActive, bossActive)) {
            showAlert(
                title = BOSS_SPAWNED_TITLE,
                visible = config.settings.showSpawnAlert,
                sound = alertSound(
                    config.settings.playSpawnSound,
                    BOSS_SPAWN_SOUND_ID,
                    BOSS_SPAWN_SOUND_PITCH,
                    BOSS_SPAWN_SOUND_VOLUME,
                    BOSS_SPAWN_SOUND_REPEAT_INTERVAL_MILLIS,
                ),
            )
        }
        wasBossActive = bossActive
    }

    private fun showAlert(title: String, visible: Boolean, sound: ScreenAlertSound?) {
        val style = ChromaTextRendering.apply(Style.EMPTY, config.details.titleColor.get()).withBold(true)
        ScreenAlertRenderer.show(
            ScreenAlert(
                id = ALERT_ID,
                lines = listOf(ScreenTitleLine(Component.literal(title).withStyle(style), TITLE_SCALE)),
                durationMillis = ALERT_DURATION_MILLIS,
                sound = sound,
                visible = visible,
            ),
        )
    }

    private fun alertSound(
        enabled: Boolean,
        soundId: String,
        pitch: Float,
        volume: Float,
        repeatIntervalMillis: Long,
    ): ScreenAlertSound? =
        if (enabled) {
            ScreenAlertSound(
                event = SoundEvent.createVariableRangeEvent(Identifier.parse(soundId)),
                pitch = pitch,
                volume = volume,
                plays = ALERT_SOUND_PLAYS,
                repeatIntervalMillis = repeatIntervalMillis,
            )
        } else {
            null
        }

    private fun clear() {
        wasBossActive = null
        ScreenAlertRenderer.clear(ALERT_ID)
    }

    private fun isActive(): Boolean = config.enabled && HypixelLocationState.inSkyBlock

    private fun shouldTrackBossSpawn(): Boolean =
        isActive() && (config.settings.showSpawnAlert || config.settings.playSpawnSound)

    private fun shouldTrackBossCocoon(): Boolean =
        isActive() && (config.settings.showCocoonAlert || config.settings.playCocoonSound)

    private const val ALERT_ID = "slayer_boss_alert"
    private const val ALERT_DURATION_MILLIS = 2_500L
    private const val ALERT_SOUND_PLAYS = 4
    private const val ALERT_SOUND_REPEAT_INTERVAL_MILLIS = 450L
    private const val TITLE_SCALE = 2.7f
    private const val BOSS_SPAWNED_TITLE = "Boss Spawned!"
    private const val BOSS_COCOONED_TITLE = "Boss Cocooned!"
    private const val BOSS_SPAWN_SOUND_ID = "minecraft:block.note_block.pling"
    private const val BOSS_SPAWN_SOUND_PITCH = 1.0f
    private const val BOSS_SPAWN_SOUND_VOLUME = 0.8f
    private const val BOSS_SPAWN_SOUND_REPEAT_INTERVAL_MILLIS = 225L
    private const val BOSS_COCOON_SOUND_ID = "minecraft:entity.horse.saddle"
    private const val BOSS_COCOON_SOUND_PITCH = 1.0f
    private const val BOSS_COCOON_SOUND_VOLUME = 1.75f
}

internal fun didSlayerBossSpawn(wasBossActive: Boolean?, bossActive: Boolean): Boolean =
    wasBossActive == false && bossActive
