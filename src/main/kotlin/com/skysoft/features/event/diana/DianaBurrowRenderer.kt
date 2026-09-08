package com.skysoft.features.event.diana

import com.skysoft.config.DianaBurrowDistancePosition
import com.skysoft.config.DianaClickCounterPosition
import com.skysoft.config.WaypointLabelFormat
import com.skysoft.utils.ColorUtilities.RGB_MASK
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
        playerLocation: WorldVec,
        style: DianaBurrowRenderStyle,
    ) {
        targets.forEach { target ->
            if (target != currentTarget) {
                renderTarget(context, target, playerLocation, style)
            }
        }
        renderTarget(context, currentTarget, playerLocation, style)
        if (style.drawCrosshairLine) {
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
        playerLocation: WorldVec,
        style: DianaBurrowRenderStyle,
    ) {
        val clickProgress = DianaBurrowInteractions.clickProgress(target)
        val displayType = clickProgress?.displayType ?: target.type
        val boxColors = style.boxStyle.colorsFor(displayType, style.visualAlphaScale)
        BlockHighlightRenderer.drawBlock(
            context,
            target.location,
            boxColors.outline,
            boxColors.fill,
            displayType.lineWidth,
        )
        style.beamColors?.let { renderBeaconBeam(context, target.location, it.getValue(displayType)) }
        renderLabel(
            context,
            target,
            playerLocation,
            displayType,
            style,
            clickProgress.takeIf { style.showClickCounter },
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
        playerLocation: WorldVec,
        displayType: DianaBurrowType,
        style: DianaBurrowRenderStyle,
        clickProgress: DianaBurrowClickProgress?,
    ) {
        val label = displayType.labelComponent(
            style.boldLabels,
            style.labelFormat,
            style.labelColors.getValue(displayType),
            style.visualAlphaScale,
        )
        val distance = style.distanceStyle?.let {
            distanceComponent(playerLocation.distance(target.location.blockCenter()), it, style.visualAlphaScale)
        }
        val progress = clickProgress?.let { progressComponent(it, style.visualAlphaScale) }
        val anchor = target.location + LABEL_OFFSET
        val labelStyle = LABEL_STYLE.withAlpha(style.visualAlphaScale)
        if (distance == null && progress == null) {
            WorldLabelRenderer.draw(context, anchor, listOf(label), labelStyle)
            return
        }

        WorldLabelRenderer.drawParts(
            context,
            anchor,
            labelParts(label, distance, style.distanceStyle?.position, progress, style.clickCounterPosition),
            labelStyle,
        )
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

    private fun distanceComponent(
        distance: Double,
        distanceStyle: DianaBurrowDistanceStyle,
        visualAlphaScale: Double,
    ): Component? {
        if (distanceStyle.hideWithin?.let { distance <= it } == true) return null
        val textAlpha = textAlpha(visualAlphaScale)
        val rgb = distanceStyle.color.rgb and RGB_MASK
        val color = if (textAlpha == FULL_TEXT_ALPHA) rgb else rgb.withAlpha(textAlpha)
        return Component.literal("(${distanceStyle.format.format(distance)})").withStyle { style ->
            val colored = style.withColor(TextColor.fromRgb(color))
            if (distanceStyle.bold) colored.withBold(true) else colored
        }
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
        distance: Component?,
        distancePosition: DianaBurrowDistancePosition?,
        progress: Component?,
        clickCounterPosition: DianaClickCounterPosition,
    ): List<WorldLabelPart> {
        val labelLine = mutableListOf(label)
        val rows = mutableListOf<List<Component>>()
        distance?.let {
            when (distancePosition) {
                DianaBurrowDistancePosition.ABOVE -> rows.add(listOf(it))
                DianaBurrowDistancePosition.LEFT -> labelLine.add(0, it)
                DianaBurrowDistancePosition.RIGHT -> labelLine.add(it)
                DianaBurrowDistancePosition.BELOW,
                null,
                -> Unit
            }
        }
        if (progress != null && clickCounterPosition == DianaClickCounterPosition.RIGHT) labelLine.add(progress)
        rows.add(labelLine)
        if (distance != null && distancePosition == DianaBurrowDistancePosition.BELOW) rows.add(listOf(distance))
        if (progress != null && clickCounterPosition == DianaClickCounterPosition.BELOW) rows.add(listOf(progress))
        val lineHeight = LABEL_STYLE.lineHeight.toFloat()
        val firstY = -rows.size * lineHeight / 2
        return rows.flatMapIndexed { index, row -> inlineParts(row, firstY + index * lineHeight) }
    }

    private fun inlineParts(components: List<Component>, y: Float): List<WorldLabelPart> {
        val font = Minecraft.getInstance().font
        val widths = components.map { font.width(it).toFloat() }
        val totalWidth = widths.sum() + LABEL_PART_GAP * (components.size - 1)
        var x = -totalWidth / 2
        return components.mapIndexed { index, component ->
            WorldLabelPart(component, x, y).also { x += widths[index] + LABEL_PART_GAP }
        }
    }

    private fun WorldLabelStyle.withAlpha(visualAlphaScale: Double): WorldLabelStyle =
        copy(textColor = WHITE_RGB.withAlpha(textAlpha(visualAlphaScale)))

    private fun ChatFormatting.withAlpha(alpha: Int): Int =
        (TextColor.fromLegacyFormat(this)?.value ?: WHITE_RGB).withAlpha(alpha)

    private fun textAlpha(visualAlphaScale: Double): Int =
        (FULL_TEXT_ALPHA * visualAlphaScale).roundToInt().coerceIn(0, FULL_TEXT_ALPHA)

    private val LABEL_OFFSET = WorldVec(0.5, 1.8, 0.5)
    private val LABEL_STYLE = WorldLabelStyle(maxRenderDistance = 80.0, maxScale = 7.0)
    private const val LABEL_PART_GAP = 3f
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
