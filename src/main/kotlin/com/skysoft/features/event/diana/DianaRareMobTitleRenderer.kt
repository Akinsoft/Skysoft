package com.skysoft.features.event.diana

import com.skysoft.config.DianaRareMobOption
import com.skysoft.utils.chat.ChatMessageSender
import com.skysoft.utils.render.ScreenAlert
import com.skysoft.utils.render.ScreenAlertRenderer
import com.skysoft.utils.render.ScreenAlertSound
import com.skysoft.utils.render.ScreenTitleLine
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.sounds.SoundEvents

internal object DianaRareMobTitleRenderer {
    fun show(mob: DianaRareMobOption, sender: ChatMessageSender) {
        show(mob, sender, "Found by ")
    }

    fun showCocoon(mob: DianaRareMobOption, sender: ChatMessageSender) {
        show(mob, sender, "Cocooned by ")
    }

    fun showOwn(mob: DianaRareMobOption) {
        showAlert(mob, null)
    }

    fun showOwnCocoon(mob: DianaRareMobOption) {
        showAlert(mob, Component.literal("Cocooned!").withStyle(ChatFormatting.GRAY))
    }

    private fun show(mob: DianaRareMobOption, sender: ChatMessageSender, subtitlePrefix: String) {
        showAlert(
            mob,
            Component.literal(subtitlePrefix)
                .withStyle(ChatFormatting.GRAY)
                .append(sender.nameComponent()),
        )
    }

    private fun showAlert(mob: DianaRareMobOption, subtitle: Component?) {
        ScreenAlertRenderer.show(
            ScreenAlert(
                id = ALERT_ID,
                lines = listOfNotNull(
                    ScreenTitleLine(
                        Component.literal(mob.label).withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD),
                        TITLE_SCALE,
                    ),
                    subtitle?.let { ScreenTitleLine(it, SUBTITLE_SCALE) },
                ),
                durationMillis = ALERT_DURATION_MILLIS,
                sound = ScreenAlertSound(
                    event = SoundEvents.NOTE_BLOCK_PLING.value(),
                    pitch = ALERT_SOUND_PITCH,
                    volume = ALERT_SOUND_VOLUME,
                    plays = ALERT_SOUND_PLAYS,
                    repeatIntervalMillis = ALERT_SOUND_REPEAT_INTERVAL_MILLIS,
                ),
                preferredYOffset = TITLE_Y_OFFSET,
                priority = ALERT_PRIORITY,
            ),
        )
    }

    fun clear() {
        ScreenAlertRenderer.clear(ALERT_ID)
    }

    val hasActiveAlert: Boolean
        get() = ScreenAlertRenderer.hasActiveAlert(ALERT_ID)

    private const val ALERT_ID = "diana_rare_mob_alert"
    private const val ALERT_DURATION_MILLIS = 2_500L
    private const val ALERT_SOUND_PLAYS = 3
    private const val ALERT_SOUND_REPEAT_INTERVAL_MILLIS = 450L
    private const val ALERT_SOUND_PITCH = 1.6f
    private const val ALERT_SOUND_VOLUME = 0.8f
    private const val ALERT_PRIORITY = 0
    private const val TITLE_SCALE = 2.7f
    private const val SUBTITLE_SCALE = 1.55f
    private const val TITLE_Y_OFFSET = -82f
}
