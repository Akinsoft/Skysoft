package com.skysoft.features.farming

import com.skysoft.SkysoftMod
import com.skysoft.config.SkysoftConfigGui
import com.skysoft.data.SkyBlockIsland
import com.skysoft.data.hypixel.TabListApi
import com.skysoft.utils.SkysoftClientEvents
import com.skysoft.utils.TextUtilities.cleanSkyBlockText
import com.skysoft.utils.render.ScreenAlert
import com.skysoft.utils.render.ScreenAlertRenderer
import com.skysoft.utils.render.ScreenAlertSound
import com.skysoft.utils.render.ScreenTitleLine
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.sounds.SoundEvent

object PestSpawnCooldownWarning {
    private val config get() = SkysoftConfigGui.config().farming.pestSpawnCooldownWarning
    private var cooldownReadyAtNanos: Long? = null
    private var earlyWarningShown = false
    private var readyWarningShown = false

    fun register() {
        TabListApi.onChange(
            "Pest Spawn Cooldown Warning",
            isActive = ::isEnabled,
            listener = ::update,
        )
        SkysoftClientEvents.onEndTick(
            "Pest Spawn Cooldown Warning timer",
            isActive = { isEnabled() && cooldownReadyAtNanos != null && !readyWarningShown },
        ) { checkTimer() }
        SkysoftClientEvents.onEndTick(
            "Pest Spawn Cooldown Warning reset",
            isActive = { hasState() && !isEnabled() },
        ) { clear() }
        SkysoftClientEvents.onDisconnect("Pest Spawn Cooldown Warning disconnect reset", ::clear)
    }

    private fun update() {
        val cooldown = TabListApi.skyBlockLines.asSequence()
            .map { component -> component.cleanSkyBlockText().trim() }
            .firstOrNull { line -> line.startsWith(COOLDOWN_PREFIX) }
            ?.removePrefix(COOLDOWN_PREFIX)
            ?: return
        if (cooldown == MAX_PESTS) {
            cooldownReadyAtNanos = null
            earlyWarningShown = true
            readyWarningShown = true
            return
        }

        val remainingSeconds = parseCooldownSeconds(cooldown) ?: return
        if (remainingSeconds == 0) {
            if (cooldownReadyAtNanos != null && !readyWarningShown) showWarning(config.details.readyText)
            cooldownReadyAtNanos = null
            earlyWarningShown = true
            readyWarningShown = true
            return
        }

        updateDeadline(remainingSeconds)
    }

    private fun updateDeadline(remainingSeconds: Int, now: Long = System.nanoTime()) {
        val candidate = now + remainingSeconds * NANOS_PER_SECOND
        val current = cooldownReadyAtNanos
        when {
            current == null || candidate > current + LATER_DEADLINE_TOLERANCE_NANOS -> {
                cooldownReadyAtNanos = candidate
                earlyWarningShown = false
                readyWarningShown = false
            }
            candidate + EARLIER_DEADLINE_TOLERANCE_NANOS < current -> cooldownReadyAtNanos = candidate
        }
        checkTimer(now)
    }

    private fun checkTimer(now: Long = System.nanoTime()) {
        val readyAt = cooldownReadyAtNanos ?: return
        if (!readyWarningShown && now >= readyAt) {
            showWarning(config.details.readyText)
            readyWarningShown = true
            return
        }

        val warningSeconds = config.settings.warningSeconds.coerceIn(MIN_WARNING_SECONDS, MAX_WARNING_SECONDS)
        if (!earlyWarningShown && warningSeconds > 0 && now >= readyAt - warningSeconds * NANOS_PER_SECOND) {
            showWarning(config.details.earlyText)
            earlyWarningShown = true
        }
    }

    private fun showWarning(text: String) {
        ScreenAlertRenderer.show(
            ScreenAlert(
                id = ALERT_ID,
                lines = listOf(
                    ScreenTitleLine(
                        Component.literal(text).withStyle(ChatFormatting.RED, ChatFormatting.BOLD),
                        TITLE_SCALE,
                    ),
                ),
                durationMillis = TITLE_DURATION_MILLIS,
                sound = ScreenAlertSound(
                    events = alertSounds,
                    pitch = ALERT_SOUND_PITCH,
                    volume = ALERT_SOUND_VOLUME,
                    repeatIntervalMillis = ALERT_SOUND_REPEAT_INTERVAL_MILLIS,
                ),
            ),
        )
    }

    private fun clear() {
        cooldownReadyAtNanos = null
        earlyWarningShown = false
        readyWarningShown = false
        ScreenAlertRenderer.clear(ALERT_ID)
    }

    private fun hasState(): Boolean = cooldownReadyAtNanos != null || earlyWarningShown || readyWarningShown

    private fun isEnabled(): Boolean = config.enabled && SkyBlockIsland.GARDEN.isInIsland()

    private fun parseCooldownSeconds(cooldown: String): Int? {
        if (cooldown == READY) return 0
        val match = cooldownTimePattern.matchEntire(cooldown) ?: return null
        val minutes = match.groups["minutes"]?.value?.toInt() ?: 0
        val seconds = match.groups["minuteSeconds"]?.value?.toInt()
            ?: match.groups["seconds"]?.value?.toInt()
            ?: 0
        return minutes * SECONDS_PER_MINUTE + seconds
    }

    private val cooldownTimePattern = Regex(
        """(?:(?<minutes>\d{1,2})m(?: (?<minuteSeconds>\d{1,2})s)?|(?<seconds>\d{1,2})s)""",
    )
    private val alertSounds = listOf(
        SoundEvent.createVariableRangeEvent(SkysoftMod.id("pest_cooldown.lead_knot_place1")),
        SoundEvent.createVariableRangeEvent(SkysoftMod.id("pest_cooldown.lead_knot_place2")),
        SoundEvent.createVariableRangeEvent(SkysoftMod.id("pest_cooldown.lead_knot_place1")),
    )
    private const val COOLDOWN_PREFIX = "Cooldown: "
    private const val READY = "READY"
    private const val MAX_PESTS = "MAX PESTS"
    private const val ALERT_ID = "pest_spawn_cooldown_warning"
    private const val TITLE_DURATION_MILLIS = 3_000L
    private const val ALERT_SOUND_REPEAT_INTERVAL_MILLIS = 225L
    private const val ALERT_SOUND_PITCH = 1.0f
    private const val ALERT_SOUND_VOLUME = 1.0f
    private const val TITLE_SCALE = 2.7f
    private const val MIN_WARNING_SECONDS = 0
    private const val MAX_WARNING_SECONDS = 20
    private const val SECONDS_PER_MINUTE = 60
    private const val NANOS_PER_SECOND = 1_000_000_000L
    private const val EARLIER_DEADLINE_TOLERANCE_NANOS = NANOS_PER_SECOND
    private const val LATER_DEADLINE_TOLERANCE_NANOS = 6 * NANOS_PER_SECOND
}
