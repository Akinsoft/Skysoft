package com.skysoft.gui.tooltip

import net.minecraft.client.gui.screens.Screen
import kotlin.math.abs

internal class TooltipPanSession(
    val screen: Screen?,
    private var identity: Int,
    private var anchorX: Int,
    private var anchorY: Int,
    private var frame: TooltipPanFrame,
    observedAt: Long,
    startOnTop: Boolean,
) {
    var lastObservedNanos = observedAt
        private set
    private var targetX = 0.0
    private var targetY = 0.0
    private var displayedX = 0.0
    private var displayedY = 0.0

    init {
        clampMotion()
        alignTallTooltipToTop(startOnTop)
    }

    fun isDifferentTarget(nextIdentity: Int, nextAnchorX: Int, nextAnchorY: Int): Boolean =
        identity != nextIdentity || abs(anchorX - nextAnchorX) > ANCHOR_TOLERANCE ||
            abs(anchorY - nextAnchorY) > ANCHOR_TOLERANCE

    fun observe(
        nextIdentity: Int,
        nextAnchorX: Int,
        nextAnchorY: Int,
        nextFrame: TooltipPanFrame,
        observedAt: Long,
    ) {
        identity = nextIdentity
        anchorX = nextAnchorX
        anchorY = nextAnchorY
        frame = nextFrame
        lastObservedNanos = observedAt
        clampMotion()
    }

    fun panBy(x: Double, y: Double) {
        targetX += x
        targetY += y
        clampMotion()
    }

    fun center() {
        targetX = 0.0
        targetY = 0.0
        displayedX = 0.0
        displayedY = 0.0
    }

    fun alignTallTooltipToTop(startOnTop: Boolean) {
        if (
            !startOnTop ||
            frame.height <= frame.viewportHeight - EDGE_GAP * 2 ||
            frame.y >= EDGE_GAP
        ) return
        targetY = EDGE_GAP - frame.y.toDouble()
        displayedY = targetY
        clampMotion()
    }

    fun advance(amount: Double) {
        if (amount >= 1.0) {
            displayedX = targetX
            displayedY = targetY
            return
        }
        displayedX = settle(displayedX + (targetX - displayedX) * amount, targetX)
        displayedY = settle(displayedY + (targetY - displayedY) * amount, targetY)
    }

    private fun clampMotion() {
        targetX = frame.clampX(targetX)
        targetY = frame.clampY(targetY)
        displayedX = frame.clampX(displayedX)
        displayedY = frame.clampY(displayedY)
    }

    fun roundedX(): Int = Math.round(displayedX).toInt()

    fun roundedY(): Int = Math.round(displayedY).toInt()

    private fun settle(value: Double, target: Double): Double =
        if (abs(target - value) < SETTLE_TOLERANCE) target else value
}

internal data class TooltipPanFrame(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    val viewportWidth: Int,
    val viewportHeight: Int,
) {
    private val minX = EDGE_GAP - width - x
    private val maxX = viewportWidth - EDGE_GAP - x
    private val minY = EDGE_GAP - height - y
    private val maxY = viewportHeight - EDGE_GAP - y

    fun clampX(value: Double): Double = clampPan(value, minX, maxX)

    fun clampY(value: Double): Double = clampPan(value, minY, maxY)
}

private fun clampPan(value: Double, minimum: Int, maximum: Int): Double =
    if (minimum > maximum) 0.0 else value.coerceIn(minimum.toDouble(), maximum.toDouble())

private const val EDGE_GAP = 4
private const val ANCHOR_TOLERANCE = 12
private const val SETTLE_TOLERANCE = 0.05
