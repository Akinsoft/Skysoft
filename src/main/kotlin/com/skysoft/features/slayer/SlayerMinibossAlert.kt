package com.skysoft.features.slayer

import com.skysoft.config.SkysoftConfigGui
import com.skysoft.data.hypixel.HypixelLocationState
import com.skysoft.data.skyblock.SlayerMessageParser
import com.skysoft.utils.chat.ChatEvents
import com.skysoft.utils.chat.ChatMessageVisibility
import com.skysoft.utils.render.ScreenAlert
import com.skysoft.utils.render.ScreenAlertRenderer
import com.skysoft.utils.render.ScreenAlertSound
import com.skysoft.utils.render.ScreenTitleLine
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.sounds.SoundEvents

object SlayerMinibossAlert {
    private val config get() = SkysoftConfigGui.config().slayer

    fun register() {
        ChatEvents.onVisibleMessage(
            "Slayer Miniboss Alert chat",
            isActive = { config.minibossAlert && HypixelLocationState.inSkyBlock },
        ) { message ->
            SlayerMessageParser.parseMinibossSpawn(message.cleanText)?.let(::showAlert)
            ChatMessageVisibility.SHOW
        }
    }

    private fun showAlert(minibossName: String) {
        ScreenAlertRenderer.show(
            ScreenAlert(
                id = ALERT_ID,
                lines = listOf(
                    ScreenTitleLine(
                        Component.literal(minibossName).withStyle(ChatFormatting.RED, ChatFormatting.BOLD),
                        TITLE_SCALE,
                    ),
                    ScreenTitleLine(
                        Component.literal("Miniboss Spawned!").withStyle(ChatFormatting.GRAY),
                        SUBTITLE_SCALE,
                    ),
                ),
                durationMillis = ALERT_DURATION_MILLIS,
                sound = ScreenAlertSound(
                    event = SoundEvents.NOTE_BLOCK_PLING.value(),
                    pitch = ALERT_SOUND_PITCH,
                    volume = ALERT_SOUND_VOLUME,
                    plays = ALERT_SOUND_PLAYS,
                    repeatIntervalMillis = ALERT_SOUND_REPEAT_INTERVAL_MILLIS,
                ),
            ),
        )
    }

    private const val ALERT_ID = "slayer_miniboss_alert"
    private const val ALERT_DURATION_MILLIS = 2_500L
    private const val ALERT_SOUND_PLAYS = 3
    private const val ALERT_SOUND_REPEAT_INTERVAL_MILLIS = 450L
    private const val ALERT_SOUND_PITCH = 1.0f
    private const val ALERT_SOUND_VOLUME = 0.8f
    private const val TITLE_SCALE = 2.7f
    private const val SUBTITLE_SCALE = 1.55f
}
