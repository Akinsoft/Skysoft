package com.skysoft.features.event.diana

import com.skysoft.utils.render.ScreenAlert
import com.skysoft.utils.render.ScreenAlertRenderer
import com.skysoft.utils.render.ScreenAlertSound
import com.skysoft.utils.render.ScreenTitleLine
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.sounds.SoundEvents

internal object DianaLobbyCompromisedTitleRenderer {
    fun show() {
        ScreenAlertRenderer.show(
            ScreenAlert(
                id = ALERT_ID,
                lines = listOf(
                    ScreenTitleLine(
                        Component.literal(DianaLobbyCompromisedWatcher.MESSAGE)
                            .withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD),
                        TITLE_SCALE,
                    ),
                ),
                durationMillis = ALERT_DURATION_MILLIS,
                sound = ScreenAlertSound(
                    event = SoundEvents.NOTE_BLOCK_BELL.value(),
                    pitch = ALERT_SOUND_PITCH,
                    volume = ALERT_SOUND_VOLUME,
                    plays = ALERT_SOUND_PLAYS,
                    repeatIntervalMillis = ALERT_SOUND_REPEAT_INTERVAL_MILLIS,
                ),
                priority = ALERT_PRIORITY,
            ),
        )
    }

    fun clear() {
        ScreenAlertRenderer.clear(ALERT_ID)
    }

    val hasActiveAlert: Boolean
        get() = ScreenAlertRenderer.hasActiveAlert(ALERT_ID)

    private const val ALERT_ID = "diana_lobby_compromised_alert"
    private const val ALERT_DURATION_MILLIS = 5_000L
    private const val ALERT_SOUND_PLAYS = 3
    private const val ALERT_SOUND_REPEAT_INTERVAL_MILLIS = 450L
    private const val ALERT_SOUND_PITCH = 1.15f
    private const val ALERT_SOUND_VOLUME = 0.9f
    private const val ALERT_PRIORITY = 10
    private const val TITLE_SCALE = 2.65f
}
