package com.skysoft.utils.render

import com.skysoft.utils.WorldVec
import com.skysoft.utils.toWorldVec
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.network.chat.Component
import net.minecraft.util.LightCoordsUtil
import net.minecraft.client.renderer.rendertype.RenderTypes
import net.minecraft.resources.Identifier
import kotlin.math.sqrt

object WorldLabelRenderer {
    fun draw(
        context: SkysoftRenderContext,
        anchor: WorldVec,
        lines: List<Component>,
        style: WorldLabelStyle = WorldLabelStyle(),
    ) {
        if (lines.isEmpty()) return

        drawTransformed(context, anchor, style) { font, _ ->
            val totalHeight = lines.size * style.lineHeight
            lines.forEachIndexed { index, line ->
                val x = -font.width(line).toFloat() / 2
                val y = (index * style.lineHeight).toFloat() - totalHeight.toFloat() / 2
                submitText(context, line, x, y, style)
            }
        }
    }

    fun drawAbove(
        context: SkysoftRenderContext,
        anchor: WorldVec,
        lines: List<Component>,
        style: WorldLabelStyle = WorldLabelStyle(),
        gap: Int = DEFAULT_ABOVE_GAP,
        reservedLinesBelow: Int = 0,
    ) {
        if (lines.isEmpty()) return

        drawTransformed(context, anchor, style) { font, scale ->
            val reservedHeight = reservedLinesBelow.coerceAtLeast(0) * style.lineHeight
            val bottomY = -font.lineHeight - (reservedHeight + gap) / scale
            lines.forEachIndexed { index, line ->
                val x = -font.width(line).toFloat() / 2
                val y = bottomY - (lines.lastIndex - index) * style.lineHeight
                submitText(context, line, x, y, style)
            }
        }
    }

    fun drawParts(
        context: SkysoftRenderContext,
        anchor: WorldVec,
        parts: List<WorldLabelPart>,
        style: WorldLabelStyle = WorldLabelStyle(),
    ) {
        if (parts.isEmpty()) return

        drawTransformed(context, anchor, style) { _, _ ->
            parts.forEach { part ->
                context.matrices.pushPose()
                context.matrices.translate(part.x.toDouble(), part.y.toDouble(), 0.0)
                submitText(context, part.component, 0f, 0f, style)
                context.matrices.popPose()
            }
        }
    }

    fun drawHeadLabel(
        context: SkysoftRenderContext,
        anchor: WorldVec,
        texture: Identifier,
        label: Component,
        style: WorldLabelStyle = WorldLabelStyle(),
    ) {
        drawTransformed(context, anchor, style) { font, _ ->
            submitHead(context, texture)
            submitText(
                context,
                label,
                -font.width(label).toFloat() / 2,
                HeadGeometry.LABEL_GAP.toFloat(),
                style,
            )
        }
    }

    private fun drawTransformed(
        context: SkysoftRenderContext,
        anchor: WorldVec,
        style: WorldLabelStyle,
        render: (Font, Float) -> Unit,
    ) {
        val font = Minecraft.getInstance().font
        val cameraPosition = context.camera.position().toWorldVec()
        val distance = distance(cameraPosition, anchor).coerceAtLeast(MIN_DISTANCE)
        val renderDistance = distance.coerceAtMost(style.maxRenderDistance)
        val renderLocation = cameraPosition + (anchor - cameraPosition) * (renderDistance / distance)
        val scale = (renderDistance / style.scaleDistance * style.scaleMultiplier)
            .coerceIn(style.minScale, style.maxScale)
            .toFloat()
        val worldScale = (style.worldScale * scale).toFloat()

        context.matrices.pushPose()
        context.matrices.translate(
            renderLocation.x - cameraPosition.x,
            renderLocation.y - cameraPosition.y,
            renderLocation.z - cameraPosition.z,
        )
        context.matrices.mulPose(context.cameraRenderState.orientation)
        context.matrices.scale(worldScale, -worldScale, worldScale)
        render(font, scale)
        context.matrices.popPose()
    }

    private fun submitText(
        context: SkysoftRenderContext,
        component: Component,
        x: Float,
        y: Float,
        style: WorldLabelStyle,
    ) {
        if (style.displayMode == Font.DisplayMode.SEE_THROUGH) {
            context.submitNodeCollector.submitText(
                context.matrices,
                x,
                y,
                component.visualOrderText,
                style.shadow,
                Font.DisplayMode.NORMAL,
                LightCoordsUtil.FULL_BRIGHT,
                style.textColor,
                style.backgroundColor,
                style.outlineColor,
            )
        }
        context.submitNodeCollector.submitText(
            context.matrices,
            x,
            y,
            component.visualOrderText,
            style.shadow,
            style.displayMode,
            LightCoordsUtil.FULL_BRIGHT,
            style.textColor,
            style.backgroundColor,
            style.outlineColor,
        )
    }

    private fun submitHead(context: SkysoftRenderContext, texture: Identifier) {
        context.submitNodeCollector.submitCustomGeometry(
            context.matrices,
            RenderTypes.textSeeThrough(texture),
        ) { pose, vertices ->
            submitHeadQuad(
                vertices,
                pose,
                HeadGeometry.FACE_U_MIN,
                HeadGeometry.FACE_V_MIN,
                HeadGeometry.FACE_U_MAX,
                HeadGeometry.FACE_V_MAX,
                HeadGeometry.BASE_Z,
            )
            submitHeadQuad(
                vertices,
                pose,
                HeadGeometry.HAT_U_MIN,
                HeadGeometry.HAT_V_MIN,
                HeadGeometry.HAT_U_MAX,
                HeadGeometry.HAT_V_MAX,
                HeadGeometry.HAT_Z,
            )
        }
    }

    private fun submitHeadQuad(
        vertices: com.mojang.blaze3d.vertex.VertexConsumer,
        pose: com.mojang.blaze3d.vertex.PoseStack.Pose,
        minU: Float,
        minV: Float,
        maxU: Float,
        maxV: Float,
        z: Float,
    ) {
        vertices.addVertex(pose, -HeadGeometry.HALF_SIZE, -HeadGeometry.SIZE, z)
            .setColor(HeadGeometry.COLOR).setUv(minU, minV)
            .setLight(LightCoordsUtil.FULL_BRIGHT)
        vertices.addVertex(pose, -HeadGeometry.HALF_SIZE, 0f, z)
            .setColor(HeadGeometry.COLOR).setUv(minU, maxV)
            .setLight(LightCoordsUtil.FULL_BRIGHT)
        vertices.addVertex(pose, HeadGeometry.HALF_SIZE, 0f, z)
            .setColor(HeadGeometry.COLOR).setUv(maxU, maxV)
            .setLight(LightCoordsUtil.FULL_BRIGHT)
        vertices.addVertex(pose, HeadGeometry.HALF_SIZE, -HeadGeometry.SIZE, z)
            .setColor(HeadGeometry.COLOR).setUv(maxU, minV)
            .setLight(LightCoordsUtil.FULL_BRIGHT)
    }

    private fun distance(from: WorldVec, to: WorldVec): Double {
        val delta = to - from
        return sqrt(delta.x * delta.x + delta.y * delta.y + delta.z * delta.z)
    }

    private const val MIN_DISTANCE = 0.001
    private const val DEFAULT_ABOVE_GAP = 2
}

private object HeadGeometry {
    const val SIZE = 16f
    const val HALF_SIZE = SIZE / 2f
    const val LABEL_GAP = 3
    const val BASE_Z = 0f
    const val HAT_Z = -0.01f
    const val COLOR = -1
    const val FACE_U_MIN = 8f / 64f
    const val FACE_V_MIN = 8f / 64f
    const val FACE_U_MAX = 16f / 64f
    const val FACE_V_MAX = 16f / 64f
    const val HAT_U_MIN = 40f / 64f
    const val HAT_V_MIN = 8f / 64f
    const val HAT_U_MAX = 48f / 64f
    const val HAT_V_MAX = 16f / 64f
}

data class WorldLabelPart(
    val component: Component,
    val x: Float,
    val y: Float,
)

data class WorldLabelStyle(
    val lineHeight: Int = 10,
    val worldScale: Double = 0.05,
    val scaleDistance: Double = 12.0,
    val scaleMultiplier: Double = 1.35,
    val minScale: Double = 0.9,
    val maxScale: Double = 6.0,
    val maxRenderDistance: Double = 50.0,
    val textColor: Int = 0xFFFFFFFF.toInt(),
    val backgroundColor: Int = 0,
    val outlineColor: Int = 0,
    val shadow: Boolean = true,
    val displayMode: Font.DisplayMode = Font.DisplayMode.SEE_THROUGH,
)
