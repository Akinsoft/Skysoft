package com.skysoft.utils.render

import com.skysoft.utils.MinecraftClient
import com.skysoft.utils.SkysoftClientEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.sounds.SoundEvent

object ScreenAlertRenderer {
    private val activeAlerts = mutableMapOf<String, ActiveScreenAlert>()
    private var registered = false

    fun register() {
        if (registered) return
        registered = true
        ScreenTitleRenderer.registerPositionEditor()
        SkysoftClientEvents.onEndTick("Screen Alert tick", { activeAlerts.isNotEmpty() }) { tick() }
        SkysoftClientEvents.onDisconnect("Screen Alert disconnect reset", ::clearAll)
        ScreenTitleRenderer.registerTitleOverlay(
            id = "screen_alerts",
            visible = ::isVisible,
            render = ::render,
        )
    }

    fun show(alert: ScreenAlert, now: Long = System.currentTimeMillis()) {
        require(alert.lines.isNotEmpty()) { "Screen alert ${alert.id} must have at least one line." }
        activeAlerts[alert.id] = ActiveScreenAlert(
            alert = alert,
            createdAtMillis = now,
            expiresAtMillis = now + alert.durationMillis,
            nextSoundIndex = 0,
            nextSoundAtMillis = now,
        )
        playDueSounds(now)
    }

    fun clear(id: String) {
        activeAlerts.remove(id)
    }

    private fun clearAll() {
        activeAlerts.clear()
    }

    fun hasActiveAlert(id: String, now: Long = System.currentTimeMillis()): Boolean =
        activeAlerts[id]?.isActive(now) == true

    fun hasActiveAlerts(now: Long = System.currentTimeMillis()): Boolean =
        activeAlerts.values.any { alert -> alert.isActive(now) && alert.alert.visible }

    internal fun tick(now: Long = System.currentTimeMillis()) {
        activeAlerts.entries.removeIf { (_, alert) -> !alert.isActive(now) }
        playDueSounds(now)
    }

    private fun isVisible(): Boolean =
        hasActiveAlerts() && !MinecraftClient.isGuiHidden(Minecraft.getInstance())

    private fun render(context: GuiGraphicsExtractor) {
        val now = System.currentTimeMillis()
        val active = activeAlerts.values
            .filter { alert -> alert.isActive(now) && alert.alert.visible }
            .sortedWith(compareBy<ActiveScreenAlert> { it.alert.priority }.thenBy { it.createdAtMillis })
        val placements = layoutAlerts(active.map { alert -> alert.alert })
        placements.forEach { placement ->
            ScreenTitleRenderer.drawLines(context, placement.lines, placement.yOffset)
        }
    }

    internal fun layoutAlerts(alerts: List<ScreenAlert>): List<ScreenAlertPlacement> {
        val occupiedRanges = mutableListOf<ScreenAlertVerticalRange>()
        return alerts
            .map { alert ->
                val height = alert.lines.totalHeight().toFloat()
                val yOffset = yOffsetFor(alert, height, occupiedRanges)
                val range = ScreenAlertVerticalRange.centered(yOffset, height)
                occupiedRanges += range
                ScreenAlertPlacement(alert.lines, yOffset)
            }
    }

    private fun yOffsetFor(
        alert: ScreenAlert,
        height: Float,
        occupiedRanges: List<ScreenAlertVerticalRange>,
    ): Float {
        var yOffset = alert.preferredYOffset
        while (true) {
            val currentRange = ScreenAlertVerticalRange.centered(yOffset, height)
            val collision = occupiedRanges.firstOrNull { range -> currentRange.overlaps(range, alert.collisionPadding) }
                ?: return yOffset
            yOffset = collision.top - alert.collisionPadding - height / 2
        }
    }

    private fun playDueSounds(now: Long) {
        activeAlerts.values
            .filter { alert -> alert.isActive(now) }
            .forEach { alert -> alert.playDueSound(now) }
    }

    private fun ActiveScreenAlert.playDueSound(now: Long) {
        val sound = alert.sound ?: return
        if (nextSoundIndex >= sound.events.size || now < nextSoundAtMillis) return
        Minecraft.getInstance().soundManager.play(
            SimpleSoundInstance.forUI(sound.events[nextSoundIndex], sound.pitch, sound.volume),
        )
        nextSoundIndex += 1
        nextSoundAtMillis = now + sound.repeatIntervalMillis
    }

    private fun ActiveScreenAlert.isActive(now: Long): Boolean =
        now < expiresAtMillis
}

data class ScreenAlert(
    val id: String,
    val lines: List<ScreenTitleLine>,
    val durationMillis: Long,
    val sound: ScreenAlertSound? = null,
    val visible: Boolean = true,
    val preferredYOffset: Float = DEFAULT_TITLE_Y_OFFSET,
    val priority: Int = 0,
    val collisionPadding: Float = DEFAULT_ALERT_COLLISION_PADDING,
)

data class ScreenAlertSound(
    val events: List<SoundEvent>,
    val pitch: Float,
    val volume: Float,
    val repeatIntervalMillis: Long = 0L,
) {
    init {
        require(events.isNotEmpty()) { "A screen alert sound must have at least one event." }
    }

    constructor(
        event: SoundEvent,
        pitch: Float,
        volume: Float,
        plays: Int = 1,
        repeatIntervalMillis: Long = 0L,
    ) : this(List(plays) { event }, pitch, volume, repeatIntervalMillis)
}

private data class ActiveScreenAlert(
    val alert: ScreenAlert,
    val createdAtMillis: Long,
    val expiresAtMillis: Long,
    var nextSoundIndex: Int,
    var nextSoundAtMillis: Long,
)

internal data class ScreenAlertPlacement(
    val lines: List<ScreenTitleLine>,
    val yOffset: Float,
)

private data class ScreenAlertVerticalRange(
    val top: Float,
    val bottom: Float,
) {
    fun overlaps(other: ScreenAlertVerticalRange, padding: Float): Boolean =
        top < other.bottom + padding && bottom > other.top - padding

    companion object {
        fun centered(yOffset: Float, height: Float): ScreenAlertVerticalRange =
            ScreenAlertVerticalRange(
                top = yOffset - height / 2,
                bottom = yOffset + height / 2,
            )
    }
}

private const val DEFAULT_ALERT_COLLISION_PADDING = 6f
