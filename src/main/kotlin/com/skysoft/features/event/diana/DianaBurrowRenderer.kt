package com.skysoft.features.event.diana

import com.skysoft.config.DianaBurrowBoxColorMode
import com.skysoft.config.DianaClickCounterPosition
import com.skysoft.config.WaypointLabelFormat
import com.skysoft.config.DianaBurrowDetailsConfig
import com.skysoft.utils.ColorUtilities.COLOR_CHANNEL_MAX
import com.skysoft.utils.ColorUtilities.COLOR_CHANNEL_MIN
import com.skysoft.utils.ColorUtilities.RGB_MASK
import com.skysoft.utils.ColorUtilities.toColor
import com.skysoft.utils.ColorUtilities.withAlpha
import com.skysoft.utils.WorldVec
import com.skysoft.utils.render.BlockHighlightRenderer
import com.skysoft.utils.render.SkysoftRenderContext
import com.skysoft.utils.render.WorldLabelPart
import com.skysoft.utils.render.WorldLabelRenderer
import com.skysoft.utils.render.WorldLabelStyle
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.OrderedSubmitNodeCollector
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.blockentity.BeaconRenderer
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.TextColor
import java.awt.Color
import kotlin.math.roundToInt

internal object DianaBurrowRenderer {
    fun renderWorld(
        context: SkysoftRenderContext,
        targets: Collection<DianaBurrowTarget>,
        currentTarget: DianaBurrowTarget,
        drawCrosshairLine: Boolean,
        boldLabels: Boolean,
        labelFormat: WaypointLabelFormat,
        labelColors: Map<DianaBurrowType, Color>,
        beamColors: Map<DianaBurrowType, Color>?,
        boxStyle: DianaBurrowBoxStyle,
        showClickCounter: Boolean,
        clickCounterPosition: DianaClickCounterPosition,
        visualAlphaScale: Double = 1.0,
    ) {
        targets.forEach { target ->
            if (target != currentTarget) {
                renderTarget(
                    context,
                    target,
                    boldLabels,
                    labelFormat,
                    labelColors,
                    beamColors,
                    boxStyle,
                    showClickCounter,
                    clickCounterPosition,
                    visualAlphaScale,
                )
            }
        }
        renderTarget(
            context,
            currentTarget,
            boldLabels,
            labelFormat,
            labelColors,
            beamColors,
            boxStyle,
            showClickCounter,
            clickCounterPosition,
            visualAlphaScale,
        )
        if (drawCrosshairLine) {
            val currentTargetType = DianaBurrowInteractions.clickProgress(currentTarget)?.displayType
                ?: currentTarget.type
            context.drawLineToCrosshair(
                currentTarget.location.blockCenter(),
                currentTargetType.outlineColor,
                currentTargetType.lineWidth,
            )
        }
    }

    private fun renderTarget(
        context: SkysoftRenderContext,
        target: DianaBurrowTarget,
        boldLabels: Boolean,
        labelFormat: WaypointLabelFormat,
        labelColors: Map<DianaBurrowType, Color>,
        beamColors: Map<DianaBurrowType, Color>?,
        boxStyle: DianaBurrowBoxStyle,
        showClickCounter: Boolean,
        clickCounterPosition: DianaClickCounterPosition,
        visualAlphaScale: Double,
    ) {
        val clickProgress = DianaBurrowInteractions.clickProgress(target)
        val displayType = clickProgress?.displayType ?: target.type
        val boxColors = boxStyle.colorsFor(displayType, visualAlphaScale)
        BlockHighlightRenderer.drawBlock(
            context,
            target.location,
            boxColors.outline,
            boxColors.fill,
            displayType.lineWidth,
        )
        beamColors?.let { renderBeaconBeam(context, target.location, it.getValue(displayType)) }
        renderLabel(
            context,
            target,
            displayType,
            boldLabels,
            labelFormat,
            labelColors.getValue(displayType),
            clickProgress.takeIf { showClickCounter },
            clickCounterPosition,
            visualAlphaScale,
        )
    }

    private fun renderBeaconBeam(context: SkysoftRenderContext, location: WorldVec, color: Color) {
        val cameraPosition = context.camera.position()
        val radiusScale = maxOf(
            1.0,
            Math.hypot(
                location.x + BEACON_CENTER_OFFSET - cameraPosition.x,
                location.z + BEACON_CENTER_OFFSET - cameraPosition.z,
            ) / BEACON_SCALE_DISTANCE,
        ).toFloat()
        val parentCollector = context.submitNodeCollector
        val beamCollector = object :
            SubmitNodeCollector,
            OrderedSubmitNodeCollector by parentCollector.order(BEACON_RENDER_ORDER) {
            override fun order(order: Int): OrderedSubmitNodeCollector = parentCollector.order(order)
        }
        context.matrices.pushPose()
        context.matrices.translate(
            location.x - cameraPosition.x,
            location.y - cameraPosition.y,
            location.z - cameraPosition.z,
        )
        BeaconRenderer.submitBeaconBeam(
            context.matrices,
            beamCollector,
            BeaconRenderer.BEAM_LOCATION,
            1f,
            Math.floorMod(Minecraft.getInstance().level?.gameTime ?: 0L, BEACON_ANIMATION_TICKS).toFloat() +
                context.partialTicks,
            0,
            BeaconRenderer.MAX_RENDER_Y,
            color.rgb,
            BeaconRenderer.SOLID_BEAM_RADIUS * radiusScale,
            BeaconRenderer.BEAM_GLOW_RADIUS * radiusScale,
        )
        context.matrices.popPose()
    }

    private fun renderLabel(
        context: SkysoftRenderContext,
        target: DianaBurrowTarget,
        displayType: DianaBurrowType,
        boldLabels: Boolean,
        labelFormat: WaypointLabelFormat,
        labelColor: Color,
        clickProgress: DianaBurrowClickProgress?,
        clickCounterPosition: DianaClickCounterPosition,
        visualAlphaScale: Double,
    ) {
        val label = displayType.labelComponent(boldLabels, labelFormat, labelColor, visualAlphaScale)
        val anchor = target.location + LABEL_OFFSET
        val style = LABEL_STYLE.withAlpha(visualAlphaScale)
        if (clickProgress == null) {
            WorldLabelRenderer.draw(context, anchor, listOf(label), style)
            return
        }

        val progress = progressComponent(clickProgress, visualAlphaScale)
        WorldLabelRenderer.drawParts(context, anchor, labelParts(label, progress, clickCounterPosition), style)
    }

    private fun DianaBurrowType.labelComponent(
        boldLabels: Boolean,
        labelFormat: WaypointLabelFormat,
        labelColor: Color,
        visualAlphaScale: Double,
    ): Component {
        val textAlpha = textAlpha(visualAlphaScale)
        val rgb = labelColor.rgb and RGB_MASK
        val color = if (textAlpha == FULL_TEXT_ALPHA) rgb else rgb.withAlpha(textAlpha)
        val key = LabelKey(boldLabels, labelFormat, color)
        LABEL_CACHE[this]?.takeIf { it.first == key }?.second?.let { return it }
        return Component.literal(labelFormat.format(label)).withStyle { style ->
            val colored = style.withColor(TextColor.fromRgb(color))
            if (boldLabels) colored.withBold(true) else colored
        }.also { LABEL_CACHE[this] = key to it }
    }

    private fun progressComponent(clickProgress: DianaBurrowClickProgress, visualAlphaScale: Double): Component {
        val textAlpha = textAlpha(visualAlphaScale)
        return PROGRESS_CACHE.getOrPut(ProgressKey(clickProgress.label, textAlpha)) {
            if (textAlpha == FULL_TEXT_ALPHA) {
                Component.literal(clickProgress.label).withStyle(ChatFormatting.AQUA)
            } else {
                Component.literal(clickProgress.label).withStyle { style ->
                    style.withColor(TextColor.fromRgb(ChatFormatting.AQUA.withAlpha(textAlpha)))
                }
            }
        }
    }

    private fun labelParts(
        label: Component,
        progress: Component,
        clickCounterPosition: DianaClickCounterPosition,
    ): List<WorldLabelPart> =
        when (clickCounterPosition) {
            DianaClickCounterPosition.RIGHT -> rightProgressParts(label, progress)
            DianaClickCounterPosition.BELOW -> belowProgressParts(label, progress)
        }

    private fun rightProgressParts(label: Component, progress: Component): List<WorldLabelPart> {
        val font = Minecraft.getInstance().font
        val labelWidth = font.width(label).toFloat()
        val progressWidth = font.width(progress).toFloat()
        val totalWidth = labelWidth + PROGRESS_GAP + progressWidth
        val labelX = -totalWidth / 2
        val labelY = -LABEL_STYLE.lineHeight.toFloat() / 2
        return listOf(
            WorldLabelPart(label, labelX, labelY),
            WorldLabelPart(
                progress,
                labelX + labelWidth + PROGRESS_GAP,
                labelY,
            ),
        )
    }

    private fun belowProgressParts(label: Component, progress: Component): List<WorldLabelPart> {
        val font = Minecraft.getInstance().font
        val labelWidth = font.width(label).toFloat()
        val progressWidth = font.width(progress).toFloat()
        val lineHeight = LABEL_STYLE.lineHeight.toFloat()
        val totalHeight = lineHeight * 2
        val labelY = -totalHeight / 2
        return listOf(
            WorldLabelPart(label, -labelWidth / 2, labelY),
            WorldLabelPart(progress, -progressWidth / 2, labelY + lineHeight),
        )
    }

    private fun WorldLabelStyle.withAlpha(visualAlphaScale: Double): WorldLabelStyle =
        copy(textColor = WHITE_RGB.withAlpha(textAlpha(visualAlphaScale)))

    private fun ChatFormatting.withAlpha(alpha: Int): Int =
        (TextColor.fromLegacyFormat(this)?.value ?: WHITE_RGB).withAlpha(alpha)

    private fun textAlpha(visualAlphaScale: Double): Int =
        (FULL_TEXT_ALPHA * visualAlphaScale).roundToInt().coerceIn(0, FULL_TEXT_ALPHA)

    private val LABEL_OFFSET = WorldVec(0.5, 1.8, 0.5)
    private val LABEL_STYLE = WorldLabelStyle(maxRenderDistance = 80.0, maxScale = 7.0)
    private const val PROGRESS_GAP = 3f
    private const val FULL_TEXT_ALPHA = 255
    private const val WHITE_RGB = 0xFFFFFF
    private const val BEACON_RENDER_ORDER = -1
    private const val BEACON_ANIMATION_TICKS = 40L
    private const val BEACON_SCALE_DISTANCE = 96.0
    private const val BEACON_CENTER_OFFSET = 0.5
    private val LABEL_CACHE = mutableMapOf<DianaBurrowType, Pair<LabelKey, Component>>()
    private val PROGRESS_CACHE = mutableMapOf<ProgressKey, Component>()

    private data class LabelKey(
        val boldLabels: Boolean,
        val labelFormat: WaypointLabelFormat,
        val color: Int,
    )

    private data class ProgressKey(
        val label: String,
        val textAlpha: Int,
    )
}

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
