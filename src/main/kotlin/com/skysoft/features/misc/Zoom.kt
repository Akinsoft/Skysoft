package com.skysoft.features.misc

import com.skysoft.config.MAX_ZOOM_AMOUNT
import com.skysoft.config.MIN_ZOOM_AMOUNT
import com.skysoft.config.SkysoftConfigGui
import com.skysoft.config.ZoomActivation
import com.skysoft.utils.EasingUtilities
import com.skysoft.utils.MinecraftClient
import com.skysoft.utils.SkysoftClientEvents
import com.skysoft.utils.input.InputUtilities
import kotlin.math.pow
import kotlin.math.sign
import net.minecraft.client.Minecraft
import org.lwjgl.glfw.GLFW

object Zoom {
    private var active = false
    private var toggled = false
    private var keyWasDown = false
    private var adjustedAmount: Double? = null
    private var transitionStart = 1.0
    private var transitionTarget = 1.0
    private var transitionStartedAt = 0L

    private val config
        get() = SkysoftConfigGui.config().misc.zoom

    fun register() {
        SkysoftClientEvents.onEndTick("Zoom input", ::hasInputWork) { updateInput(it) }
        SkysoftClientEvents.onDisconnect("Zoom reset", ::reset)
    }

    @JvmStatic
    fun applyFov(fov: Double): Double = zoomedFov(fov, currentAmount())

    @JvmStatic
    fun shouldHideHand(): Boolean = active && config.enabled && config.details.hideHand

    @JvmStatic
    fun applyMouseMovement(movement: Double): Double {
        if (!config.settings.relativeSensitivity) return movement
        return movement / currentAmount()
    }

    @JvmStatic
    fun didHandleScroll(verticalAmount: Double): Boolean {
        val settings = config.settings
        if (!active || !settings.scrollToAdjust || verticalAmount == 0.0 || MinecraftClient.screen() != null) {
            return false
        }
        val current = adjustedAmount ?: settings.zoomAmount.toDouble()
        adjustedAmount = scrollAmount(current, verticalAmount)
        return true
    }

    internal fun zoomedFov(fov: Double, amount: Double): Double = fov / amount.coerceAtLeast(1.0)

    internal fun scrollAmount(current: Double, verticalAmount: Double): Double =
        (current * SCROLL_ZOOM_STEP.pow(verticalAmount.sign))
            .coerceIn(MIN_ZOOM_AMOUNT.toDouble(), MAX_ZOOM_AMOUNT.toDouble())

    private fun hasInputWork(): Boolean = config.enabled || active || keyWasDown || toggled

    private fun updateInput(minecraft: Minecraft) {
        val settings = config.settings
        val canZoom = config.enabled && minecraft.player != null && MinecraftClient.screen(minecraft) == null &&
            settings.key != GLFW.GLFW_KEY_UNKNOWN
        val keyDown = canZoom && InputUtilities.isActionBindingDown(settings.key)

        if (!canZoom) {
            toggled = false
        } else if (settings.activation == ZoomActivation.TOGGLE && keyDown && !keyWasDown) {
            toggled = !toggled
        }

        val wasActive = active
        active = canZoom && if (settings.activation == ZoomActivation.HOLD) keyDown else toggled
        keyWasDown = keyDown
        if (wasActive && !active && !settings.rememberAdjustment) adjustedAmount = null
    }

    private fun currentAmount(nowNanos: Long = System.nanoTime()): Double {
        val target = if (active && config.enabled) adjustedAmount ?: config.settings.zoomAmount.toDouble() else 1.0
        val details = config.details
        if (!details.smoothTransition) {
            transitionStart = target
            transitionTarget = target
            transitionStartedAt = 0L
            return target
        }
        val current = transitionValue(nowNanos, details.transitionMillis)
        if (target != transitionTarget) {
            transitionStart = current
            transitionTarget = target
            transitionStartedAt = nowNanos
        }
        return transitionValue(nowNanos, details.transitionMillis)
    }

    private fun transitionValue(nowNanos: Long, durationMillis: Int): Double {
        if (transitionStart == transitionTarget || transitionStartedAt == 0L) return transitionTarget
        val progress = ((nowNanos - transitionStartedAt) / (durationMillis * NANOS_PER_MILLI).toDouble()).coerceIn(0.0, 1.0)
        val eased = EasingUtilities.smoothStep(progress).coerceIn(0.0, 1.0)
        return transitionStart + (transitionTarget - transitionStart) * eased
    }

    private fun reset() {
        active = false
        toggled = false
        keyWasDown = false
        adjustedAmount = null
        transitionStart = 1.0
        transitionTarget = 1.0
        transitionStartedAt = 0L
    }

    private const val SCROLL_ZOOM_STEP = 1.25
    private const val NANOS_PER_MILLI = 1_000_000L
}
