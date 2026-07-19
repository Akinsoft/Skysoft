package com.skysoft.features.screenshot

import com.skysoft.utils.EasingUtilities
import com.skysoft.utils.gui.Rect
import kotlin.math.roundToInt

internal data class ScreenshotFocusVisuals(
    val imageBounds: Rect,
    val chromeAlpha: Double,
    val isInteractive: Boolean,
    val shouldClipImage: Boolean,
)

internal class ScreenshotFocusTransition {
    private var kind = ScreenshotFocusTransitionKind.NONE
    private var sourceBounds: Rect? = null
    private var navigationDirection = 0
    private var startedAtNanos = 0L

    fun startExpansion(source: Rect) {
        kind = ScreenshotFocusTransitionKind.EXPANSION
        sourceBounds = source
        navigationDirection = 0
        startedAtNanos = System.nanoTime()
    }

    fun startNavigation(direction: Int) {
        kind = ScreenshotFocusTransitionKind.NAVIGATION
        sourceBounds = null
        navigationDirection = direction.coerceIn(-1, 1)
        startedAtNanos = System.nanoTime()
    }

    fun visuals(target: Rect): ScreenshotFocusVisuals {
        val progress = progress()
        val eased = EasingUtilities.easeOutCubic(progress)
        return when (kind) {
            ScreenshotFocusTransitionKind.NONE -> ScreenshotFocusVisuals(target, 1.0, true, false)
            ScreenshotFocusTransitionKind.EXPANSION -> ScreenshotFocusVisuals(
                imageBounds = interpolate(requireNotNull(sourceBounds), target, eased),
                chromeAlpha = EasingUtilities.smoothStep(
                    ((progress - CHROME_DELAY) / (1.0 - CHROME_DELAY)).coerceIn(0.0, 1.0),
                ),
                isInteractive = progress >= 1.0,
                shouldClipImage = false,
            )
            ScreenshotFocusTransitionKind.NAVIGATION -> {
                val maximumOffset = (target.width * NAVIGATION_OFFSET_RATIO).roundToInt()
                    .coerceIn(MINIMUM_NAVIGATION_OFFSET, MAXIMUM_NAVIGATION_OFFSET)
                ScreenshotFocusVisuals(
                    imageBounds = target.copy(
                        x = target.x + (maximumOffset * navigationDirection * (1.0 - eased)).roundToInt(),
                    ),
                    chromeAlpha = 1.0,
                    isInteractive = progress >= 1.0,
                    shouldClipImage = true,
                )
            }
        }
    }

    fun isComplete(): Boolean = progress() >= 1.0

    fun reset() {
        kind = ScreenshotFocusTransitionKind.NONE
        sourceBounds = null
        navigationDirection = 0
        startedAtNanos = 0L
    }

    private fun progress(): Double {
        if (kind == ScreenshotFocusTransitionKind.NONE) return 1.0
        val duration = when (kind) {
            ScreenshotFocusTransitionKind.EXPANSION -> EXPANSION_DURATION_NANOS
            ScreenshotFocusTransitionKind.NAVIGATION -> NAVIGATION_DURATION_NANOS
            ScreenshotFocusTransitionKind.NONE -> return 1.0
        }
        return ((System.nanoTime() - startedAtNanos) / duration.toDouble()).coerceIn(0.0, 1.0)
    }

    private fun interpolate(from: Rect, to: Rect, progress: Double): Rect = Rect(
        x = interpolate(from.x, to.x, progress),
        y = interpolate(from.y, to.y, progress),
        width = interpolate(from.width, to.width, progress),
        height = interpolate(from.height, to.height, progress),
    )

    private fun interpolate(from: Int, to: Int, progress: Double): Int =
        (from + (to - from) * progress).roundToInt()

    private companion object {
        const val EXPANSION_DURATION_NANOS = 260_000_000L
        const val NAVIGATION_DURATION_NANOS = 190_000_000L
        const val CHROME_DELAY = 0.12
        const val NAVIGATION_OFFSET_RATIO = 0.12
        const val MINIMUM_NAVIGATION_OFFSET = 28
        const val MAXIMUM_NAVIGATION_OFFSET = 72
    }
}

private enum class ScreenshotFocusTransitionKind {
    NONE,
    EXPANSION,
    NAVIGATION,
}
