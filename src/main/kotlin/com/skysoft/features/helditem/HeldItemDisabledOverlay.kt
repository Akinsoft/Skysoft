package com.skysoft.features.helditem

import com.skysoft.utils.ColorUtilities.withScaledAlpha
import com.skysoft.utils.gui.OverlayPanelStyle
import com.skysoft.utils.gui.Rect
import kotlin.math.roundToInt
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.resources.Identifier

internal class HeldItemDisabledOverlay(
    private val nanoTime: () -> Long = System::nanoTime,
) {
    private var phase = DisabledOverlayPhase.HIDDEN
    private var fadeStartedAtNanos = 0L
    private var isInitialized = false

    fun initialize(isFeatureEnabled: Boolean) {
        if (isInitialized) {
            synchronize(isFeatureEnabled)
            return
        }
        phase = if (isFeatureEnabled) DisabledOverlayPhase.HIDDEN else DisabledOverlayPhase.SHOWN
        fadeStartedAtNanos = 0L
        isInitialized = true
    }

    fun beginEnableTransition() {
        if (phase == DisabledOverlayPhase.HIDDEN) return
        phase = DisabledOverlayPhase.FADING
        fadeStartedAtNanos = nanoTime()
    }

    fun isEditingBlocked(isFeatureEnabled: Boolean): Boolean {
        synchronize(isFeatureEnabled)
        completeFadeIfNeeded()
        return phase != DisabledOverlayPhase.HIDDEN
    }

    fun visuals(isFeatureEnabled: Boolean): HeldItemDisabledOverlayVisuals {
        synchronize(isFeatureEnabled)
        if (phase == DisabledOverlayPhase.HIDDEN) return HeldItemDisabledOverlayVisuals.HIDDEN
        if (phase == DisabledOverlayPhase.SHOWN) return HeldItemDisabledOverlayVisuals.DISABLED

        val elapsed = nanoTime() - fadeStartedAtNanos
        val fadeProgress = progress(elapsed, DisabledOverlayAnimation.FADE_DURATION_NANOS)
        val toggleProgress = smoothStep(progress(elapsed, DisabledOverlayAnimation.TOGGLE_DURATION_NANOS))
        if (fadeProgress >= 1f) {
            phase = DisabledOverlayPhase.HIDDEN
            return HeldItemDisabledOverlayVisuals.HIDDEN
        }
        return HeldItemDisabledOverlayVisuals(
            opacity = 1f - smoothStep(fadeProgress),
            toggleProgress = toggleProgress,
        )
    }

    private fun synchronize(isFeatureEnabled: Boolean) {
        if (!isFeatureEnabled) {
            phase = DisabledOverlayPhase.SHOWN
            fadeStartedAtNanos = 0L
        } else if (phase == DisabledOverlayPhase.SHOWN) {
            beginEnableTransition()
        }
    }

    private fun completeFadeIfNeeded() {
        if (
            phase == DisabledOverlayPhase.FADING &&
            nanoTime() - fadeStartedAtNanos >= DisabledOverlayAnimation.FADE_DURATION_NANOS
        ) {
            phase = DisabledOverlayPhase.HIDDEN
        }
    }
}

internal data class HeldItemDisabledOverlayVisuals(
    val opacity: Float,
    val toggleProgress: Float,
) {
    companion object {
        val HIDDEN = HeldItemDisabledOverlayVisuals(0f, 1f)
        val DISABLED = HeldItemDisabledOverlayVisuals(1f, 0f)
    }
}

internal object HeldItemDisabledOverlayRenderer {
    fun toggleBounds(screenWidth: Int, screenHeight: Int): Rect {
        val card = cardBounds(screenWidth, screenHeight)
        return Rect(
            card.x + card.width - DisabledOverlayLayout.INSET - DisabledOverlayLayout.TOGGLE_WIDTH,
            card.y + (card.height - DisabledOverlayLayout.TOGGLE_HEIGHT) / 2,
            DisabledOverlayLayout.TOGGLE_WIDTH,
            DisabledOverlayLayout.TOGGLE_HEIGHT,
        )
    }

    fun render(
        context: GuiGraphicsExtractor,
        font: Font,
        screenWidth: Int,
        screenHeight: Int,
        visuals: HeldItemDisabledOverlayVisuals,
    ) {
        if (visuals.opacity <= 0f) return
        val opacity = visuals.opacity.toDouble()
        val card = cardBounds(screenWidth, screenHeight)
        context.fill(0, 0, screenWidth, screenHeight, DisabledOverlayColors.SCREEN_SHADE.withScaledAlpha(opacity))
        context.fill(
            card.x,
            card.y,
            card.x + card.width,
            card.y + card.height,
            OverlayPanelStyle.BACKGROUND.withScaledAlpha(opacity),
        )
        context.outline(
            card.x,
            card.y,
            card.width,
            card.height,
            OverlayPanelStyle.OUTLINE.withScaledAlpha(opacity),
        )
        context.text(
            font,
            DisabledOverlayText.TITLE,
            card.x + DisabledOverlayLayout.INSET,
            card.y + DisabledOverlayLayout.TITLE_Y,
            DisabledOverlayColors.TITLE.withScaledAlpha(opacity),
            false,
        )
        context.text(
            font,
            DisabledOverlayText.STATUS,
            card.x + DisabledOverlayLayout.INSET,
            card.y + DisabledOverlayLayout.STATUS_Y,
            DisabledOverlayColors.STATUS.withScaledAlpha(opacity),
            false,
        )
        drawToggle(context, toggleBounds(screenWidth, screenHeight), visuals, opacity)
    }

    private fun drawToggle(
        context: GuiGraphicsExtractor,
        bounds: Rect,
        visuals: HeldItemDisabledOverlayVisuals,
        opacity: Double,
    ) {
        val color = DisabledOverlayColors.TITLE.withScaledAlpha(opacity)
        context.blit(
            RenderPipelines.GUI_TEXTURED,
            DisabledOverlayTextures.BAR,
            bounds.x,
            bounds.y,
            0f,
            0f,
            bounds.width,
            bounds.height,
            bounds.width,
            bounds.height,
            color,
        )
        val knobX = bounds.x + (
            visuals.toggleProgress * (bounds.width - DisabledOverlayLayout.KNOB_WIDTH)
            ).roundToInt()
        val knobTexture = if (visuals.toggleProgress < DisabledOverlayAnimation.KNOB_TEXTURE_THRESHOLD) {
            DisabledOverlayTextures.OFF
        } else {
            DisabledOverlayTextures.ON
        }
        context.blit(
            RenderPipelines.GUI_TEXTURED,
            knobTexture,
            knobX,
            bounds.y,
            0f,
            0f,
            DisabledOverlayLayout.KNOB_WIDTH,
            bounds.height,
            DisabledOverlayLayout.KNOB_WIDTH,
            bounds.height,
            color,
        )
    }

    private fun cardBounds(screenWidth: Int, screenHeight: Int): Rect = Rect(
        (screenWidth - DisabledOverlayLayout.CARD_WIDTH) / 2,
        (screenHeight - DisabledOverlayLayout.CARD_HEIGHT) / 2,
        DisabledOverlayLayout.CARD_WIDTH,
        DisabledOverlayLayout.CARD_HEIGHT,
    )
}

private enum class DisabledOverlayPhase {
    HIDDEN,
    SHOWN,
    FADING,
}

private fun progress(elapsedNanos: Long, durationNanos: Long): Float =
    (elapsedNanos / durationNanos.toFloat()).coerceIn(0f, 1f)

private fun smoothStep(progress: Float): Float {
    val remainingCoefficient = DisabledOverlayAnimation.SMOOTH_STEP_LINEAR_COEFFICIENT -
        DisabledOverlayAnimation.SMOOTH_STEP_CUBIC_COEFFICIENT * progress
    return progress * progress * remainingCoefficient
}

private object DisabledOverlayAnimation {
    const val FADE_DURATION_NANOS = 300_000_000L
    const val TOGGLE_DURATION_NANOS = 200_000_000L
    const val KNOB_TEXTURE_THRESHOLD = 0.5f
    const val SMOOTH_STEP_LINEAR_COEFFICIENT = 3f
    const val SMOOTH_STEP_CUBIC_COEFFICIENT = 2f
}

private object DisabledOverlayLayout {
    const val CARD_WIDTH = 196
    const val CARD_HEIGHT = 58
    const val INSET = 12
    const val TITLE_Y = 12
    const val STATUS_Y = 32
    const val TOGGLE_WIDTH = 48
    const val TOGGLE_HEIGHT = 14
    const val KNOB_WIDTH = 12
}

private object DisabledOverlayColors {
    val SCREEN_SHADE = 0xB0000000.toInt()
    val TITLE = 0xFFFFFFFF.toInt()
    val STATUS = 0xFFFF5555.toInt()
}

private object DisabledOverlayText {
    const val TITLE = "Held Item"
    const val STATUS = "Currently disabled"
}

private object DisabledOverlayTextures {
    val BAR: Identifier = Identifier.fromNamespaceAndPath("moulconfig", "bar.png")
    val OFF: Identifier = Identifier.fromNamespaceAndPath("moulconfig", "toggle_off.png")
    val ON: Identifier = Identifier.fromNamespaceAndPath("moulconfig", "toggle_on.png")
}
