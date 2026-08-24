package com.skysoft.features.misc

import com.skysoft.config.SkysoftConfigGui
import com.skysoft.data.hypixel.HypixelLocationState
import com.skysoft.utils.SkysoftClientEvents
import com.skysoft.utils.SoundUtilities
import com.skysoft.utils.chat.ChatEvents
import com.skysoft.utils.chat.ChatMessageVisibility

object ForIntrests {
    private val config get() = SkysoftConfigGui.config().settings
    private val recentDeaths = ArrayDeque<Long>()

    fun register() {
        ChatEvents.onVisibleMessage(
            "For Intrests death sound",
            { config.forIntrests && HypixelLocationState.inSkyBlock },
        ) { message ->
            if (message.isSystemLike && message.body.startsWith(DEATH_MESSAGE_PREFIX)) playDeathSound()
            ChatMessageVisibility.SHOW
        }
        SkysoftClientEvents.onDisconnect("For Intrests death reset", recentDeaths::clear)
    }

    private fun playDeathSound() {
        val now = System.nanoTime()
        while (recentDeaths.firstOrNull()?.let { now - it > DEATH_WINDOW_NANOS } == true) recentDeaths.removeFirst()
        recentDeaths.addLast(now)
        val sound = if (recentDeaths.size == IDIOT_DEATH_COUNT) {
            recentDeaths.clear()
            IDIOT_DEATH_SOUND_ID
        } else {
            DEATH_SOUND_ID
        }
        SoundUtilities.playUiSound(sound, 1f, config.forIntrestsVolume / MAX_VOLUME_PERCENT)
    }

    private const val DEATH_MESSAGE_PREFIX = "☠ You "
    private const val DEATH_SOUND_ID = "skysoft:for_intrests.death"
    private const val IDIOT_DEATH_SOUND_ID = "skysoft:for_intrests.death_idiot"
    private const val IDIOT_DEATH_COUNT = 10
    private const val MAX_VOLUME_PERCENT = 100f
    private const val DEATH_WINDOW_NANOS = 5L * 60 * 1_000_000_000
}
