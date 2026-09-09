package com.skysoft.features.inventory

import com.mojang.blaze3d.pipeline.RenderPipeline
import com.mojang.blaze3d.vertex.VertexConsumer
import com.skysoft.utils.ColorUtilities.withScaledAlpha
import kotlin.math.abs
import kotlin.math.floor
import net.minecraft.client.gui.navigation.ScreenRectangle
import net.minecraft.client.gui.render.TextureSetup
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.client.renderer.state.gui.GuiElementRenderState
import org.joml.Matrix3x2fc

internal class SlotBindingLineRenderState(
    private val pose: Matrix3x2fc,
    private val startX: Int,
    private val startY: Int,
    private val endX: Int,
    private val endY: Int,
    private val color: Int,
) : GuiElementRenderState {
    private val bounds = ScreenRectangle(
        minOf(startX, endX) - PIXEL_SIZE,
        minOf(startY, endY) - PIXEL_SIZE,
        abs(endX - startX) + PIXEL_SIZE * 3,
        abs(endY - startY) + PIXEL_SIZE * 3,
    ).transformMaxBounds(pose)

    override fun pipeline(): RenderPipeline = RenderPipelines.GUI

    override fun textureSetup(): TextureSetup = TextureSetup.noTexture()

    override fun scissorArea(): ScreenRectangle? = null

    override fun bounds(): ScreenRectangle = bounds

    override fun buildVertices(consumer: VertexConsumer) {
        if (startX == endX && startY == endY) {
            drawPixel(consumer, startX, startY, 1.0)
            return
        }

        var x0 = startX.toDouble()
        var y0 = startY.toDouble()
        var x1 = endX.toDouble()
        var y1 = endY.toDouble()
        val steep = abs(y1 - y0) > abs(x1 - x0)
        if (steep) {
            val oldX0 = x0
            x0 = y0
            y0 = oldX0
            val oldX1 = x1
            x1 = y1
            y1 = oldX1
        }
        if (x0 > x1) {
            val oldX0 = x0
            x0 = x1
            x1 = oldX0
            val oldY0 = y0
            y0 = y1
            y1 = oldY0
        }

        val dx = x1 - x0
        val gradient = if (dx == 0.0) 1.0 else (y1 - y0) / dx
        drawLineEndpoints(consumer, steep, LineValues(x0, y0, x1, y1, gradient))
    }

    private fun drawLineEndpoints(consumer: VertexConsumer, steep: Boolean, values: LineValues) {
        val xEnd1 = roundLineCoordinate(values.x0)
        val yEnd1 = values.y0 + values.gradient * (xEnd1 - values.x0)
        val xGap1 = reverseFractionalPart(values.x0 + SUBPIXEL_CENTER)
        val xPixel1 = xEnd1.toInt()
        val yPixel1 = integerPart(yEnd1)
        plotLinePixel(consumer, steep, xPixel1, yPixel1, reverseFractionalPart(yEnd1) * xGap1)
        plotLinePixel(consumer, steep, xPixel1, yPixel1 + 1, fractionalPart(yEnd1) * xGap1)

        val xEnd2 = roundLineCoordinate(values.x1)
        val yEnd2 = values.y1 + values.gradient * (xEnd2 - values.x1)
        val xGap2 = fractionalPart(values.x1 + SUBPIXEL_CENTER)
        val xPixel2 = xEnd2.toInt()
        val yPixel2 = integerPart(yEnd2)
        plotLinePixel(consumer, steep, xPixel2, yPixel2, reverseFractionalPart(yEnd2) * xGap2)
        plotLinePixel(consumer, steep, xPixel2, yPixel2 + 1, fractionalPart(yEnd2) * xGap2)

        var interY = yEnd1 + values.gradient
        for (x in (xPixel1 + 1) until xPixel2) {
            val y = integerPart(interY)
            plotLinePixel(consumer, steep, x, y, reverseFractionalPart(interY))
            plotLinePixel(consumer, steep, x, y + 1, fractionalPart(interY))
            interY += values.gradient
        }
    }

    private fun plotLinePixel(
        consumer: VertexConsumer,
        steep: Boolean,
        x: Int,
        y: Int,
        coverage: Double,
    ) {
        if (steep) drawPixel(consumer, y, x, coverage) else drawPixel(consumer, x, y, coverage)
    }

    private fun drawPixel(consumer: VertexConsumer, x: Int, y: Int, coverage: Double) {
        if (coverage <= 0.0) return
        val pixelColor = color.withScaledAlpha(coverage)
        consumer.addVertexWith2DPose(pose, x.toFloat(), y.toFloat()).setColor(pixelColor)
        consumer.addVertexWith2DPose(pose, x.toFloat(), y + PIXEL_SIZE.toFloat()).setColor(pixelColor)
        consumer.addVertexWith2DPose(pose, x + PIXEL_SIZE.toFloat(), y + PIXEL_SIZE.toFloat())
            .setColor(pixelColor)
        consumer.addVertexWith2DPose(pose, x + PIXEL_SIZE.toFloat(), y.toFloat()).setColor(pixelColor)
    }

    private fun integerPart(value: Double): Int = floor(value).toInt()

    private fun roundLineCoordinate(value: Double): Double = floor(value + SUBPIXEL_CENTER)

    private fun fractionalPart(value: Double): Double = value - floor(value)

    private fun reverseFractionalPart(value: Double): Double = 1.0 - fractionalPart(value)
}

private data class LineValues(val x0: Double, val y0: Double, val x1: Double, val y1: Double, val gradient: Double)

private const val PIXEL_SIZE = 1
private const val SUBPIXEL_CENTER = 0.5
