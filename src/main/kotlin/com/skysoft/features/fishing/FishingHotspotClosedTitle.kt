package com.skysoft.features.fishing

import com.skysoft.utils.render.ScreenAlert
import com.skysoft.utils.render.ScreenAlertRenderer
import com.skysoft.utils.render.ScreenAlertSound
import com.skysoft.utils.render.ScreenTitleLine
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.sounds.SoundEvents

internal object FishingHotspotClosedTitle {
    fun show() {
        ScreenAlertRenderer.show(
            ScreenAlert(
                id = ALERT_ID,
                lines = listOf(
                    ScreenTitleLine(
                        Component.literal("Hotspot closed").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD),
                        TITLE_SCALE,
                    ),
                ),
                durationMillis = ALERT_DURATION_MILLIS,
                sound = ScreenAlertSound(
                    event = SoundEvents.NOTE_BLOCK_BELL.value(),
                    pitch = ALERT_SOUND_PITCH,
                    volume = ALERT_SOUND_VOLUME,
                ),
                priority = ALERT_PRIORITY,
            ),
        )
    }

    fun clear() {
        ScreenAlertRenderer.clear(ALERT_ID)
    }

    private const val ALERT_ID = "fishing_hotspot_closed_alert"
    private const val ALERT_DURATION_MILLIS = 2_500L
    private const val ALERT_SOUND_PITCH = 0.9f
    private const val ALERT_SOUND_VOLUME = 0.8f
    private const val ALERT_PRIORITY = 5
    private const val TITLE_SCALE = 2.65f
}
