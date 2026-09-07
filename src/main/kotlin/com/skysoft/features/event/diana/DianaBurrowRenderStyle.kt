package com.skysoft.features.event.diana

import com.skysoft.config.DianaBurrowBoxColorMode
import com.skysoft.config.DianaBurrowDetailsConfig
import com.skysoft.config.DianaBurrowDistanceFormat
import com.skysoft.config.DianaBurrowDistancePosition
import com.skysoft.config.DianaClickCounterPosition
import com.skysoft.config.WaypointLabelFormat
import com.skysoft.utils.ColorUtilities.COLOR_CHANNEL_MAX
import com.skysoft.utils.ColorUtilities.COLOR_CHANNEL_MIN
import com.skysoft.utils.ColorUtilities.toColor
import java.awt.Color
import kotlin.math.roundToInt

internal data class DianaBurrowRenderStyle(
    val drawCrosshairLine: Boolean,
    val boldLabels: Boolean,
    val labelFormat: WaypointLabelFormat,
    val labelColors: Map<DianaBurrowType, Color>,
    val beamColors: Map<DianaBurrowType, Color>?,
    val boxStyle: DianaBurrowBoxStyle,
    val distanceStyle: DianaBurrowDistanceStyle?,
    val showClickCounter: Boolean,
    val clickCounterPosition: DianaClickCounterPosition,
    val visualAlphaScale: Double = 1.0,
)

internal class DianaBurrowBoxStyle(
    private val labelColors: Map<DianaBurrowType, Color>,
    private val customColor: Color?,
) {
    fun colorsFor(type: DianaBurrowType, visualAlphaScale: Double = 1.0): DianaBurrowBoxColors {
        customColor?.let { color ->
            return DianaBurrowBoxColors(
                color.withScaledAlpha(visualAlphaScale),
                color.withScaledAlpha(CUSTOM_FILL_ALPHA_SCALE * visualAlphaScale),
            )
        }
        val color = labelColors.getValue(type)
        return DianaBurrowBoxColors(
            Color(color.red, color.green, color.blue, type.outlineColor.alpha).withScaledAlpha(visualAlphaScale),
            Color(color.red, color.green, color.blue, type.fillColor.alpha).withScaledAlpha(visualAlphaScale),
        )
    }

    private fun Color.withScaledAlpha(scale: Double): Color =
        Color(red, green, blue, (alpha * scale).roundToInt().coerceIn(COLOR_CHANNEL_MIN, COLOR_CHANNEL_MAX))

    private companion object {
        const val CUSTOM_FILL_ALPHA_SCALE = 0.25
    }
}

internal data class DianaBurrowBoxColors(
    val outline: Color,
    val fill: Color,
)

internal data class DianaBurrowDistanceStyle(
    val hideWithin: Int?,
    val format: DianaBurrowDistanceFormat,
    val color: Color,
    val bold: Boolean,
    val position: DianaBurrowDistancePosition,
)

internal fun DianaBurrowDetailsConfig.burrowDistanceStyle(): DianaBurrowDistanceStyle =
    DianaBurrowDistanceStyle(
        distanceHideRadius.takeIf { hideDistanceWithin },
        distanceFormat,
        distanceColor.get().toColor(),
        distanceBold,
        distancePosition,
    )

internal fun DianaBurrowDetailsConfig.burrowLabelColors(): Map<DianaBurrowType, Color> = mapOf(
    DianaBurrowType.START to startTextColor.get().toColor(),
    DianaBurrowType.MOB to mobTextColor.get().toColor(),
    DianaBurrowType.TREASURE to treasureTextColor.get().toColor(),
    DianaBurrowType.GUESS to guessTextColor.get().toColor(),
)

internal fun DianaBurrowDetailsConfig.burrowBeamColors(): Map<DianaBurrowType, Color> = mapOf(
    DianaBurrowType.START to startBeamColor.get().toColor(),
    DianaBurrowType.MOB to mobBeamColor.get().toColor(),
    DianaBurrowType.TREASURE to treasureBeamColor.get().toColor(),
    DianaBurrowType.GUESS to guessBeamColor.get().toColor(),
)

internal fun DianaBurrowDetailsConfig.burrowBoxStyle(
    labelColors: Map<DianaBurrowType, Color> = burrowLabelColors(),
): DianaBurrowBoxStyle =
    DianaBurrowBoxStyle(
        labelColors = labelColors,
        customColor = if (burrowBoxColorMode == DianaBurrowBoxColorMode.CUSTOM) {
            burrowBoxColor.get().toColor()
        } else {
            null
        },
    )
