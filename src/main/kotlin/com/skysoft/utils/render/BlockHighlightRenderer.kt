package com.skysoft.utils.render

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import com.skysoft.utils.WorldVec
import net.minecraft.world.phys.shapes.VoxelShape
import java.awt.Color

object BlockHighlightRenderer {
    fun drawBlock(
        context: SkysoftRenderContext,
        block: WorldVec,
        outlineColor: Color,
        fillColor: Color,
        lineWidth: Int = DEFAULT_LINE_WIDTH,
        expand: Double = DEFAULT_EXPAND,
        depth: Boolean = false,
    ) {
        drawFilledBlock(context, block, fillColor, expand)
        drawBlockOutline(context, block, outlineColor, lineWidth, depth)
    }

    fun drawShape(
        context: SkysoftRenderContext,
        block: WorldVec,
        shape: VoxelShape,
        outlineColor: Color,
        fillColor: Color,
        lineWidth: Int = DEFAULT_LINE_WIDTH,
        depth: Boolean = false,
    ) {
        drawFilledShape(context, block, shape, fillColor)
        LineBoxRenderer.draw3D(context, lineWidth, depth) {
            drawShape(block, shape, outlineColor)
        }
    }

    private fun drawFilledShape(
        context: SkysoftRenderContext,
        block: WorldVec,
        shape: VoxelShape,
        color: Color,
    ) {
        val cameraPos = context.camera.position()
        context.withIsolatedPose {
            context.matrices.translate(
                block.x - cameraPos.x,
                block.y - cameraPos.y,
                block.z - cameraPos.z,
            )
            context.submitNodeCollector.submitCustomGeometry(
                context.matrices,
                SkysoftRenderLayers.filledShape(),
            ) { matrix, buffer ->
                shape.forAllBoxes { minX, minY, minZ, maxX, maxY, maxZ ->
                    drawFilledBox(
                        buffer,
                        matrix,
                        WorldVec(minX, minY, minZ),
                        WorldVec(maxX, maxY, maxZ),
                        color,
                    )
                }
            }
        }
    }

    private fun drawFilledBlock(context: SkysoftRenderContext, block: WorldVec, color: Color, expand: Double) {
        val min = WorldVec(block.x - expand, block.y - expand, block.z - expand)
        val max = WorldVec(block.x + 1 + expand, block.y + 1 + expand, block.z + 1 + expand)
        val cameraPos = context.camera.position()
        context.withIsolatedPose {
            context.matrices.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z)
            context.submitNodeCollector.submitCustomGeometry(context.matrices, SkysoftRenderLayers.filledBox()) { matrix, buffer ->
                drawFilledBox(buffer, matrix, min, max, color)
            }
        }
    }

    private fun drawFilledBox(buffer: VertexConsumer, matrix: PoseStack.Pose, min: WorldVec, max: WorldVec, color: Color) {
        drawQuad(buffer, matrix, color, min.x, min.y, min.z, max.x, min.y, min.z, max.x, min.y, max.z, min.x, min.y, max.z)
        drawQuad(buffer, matrix, color, min.x, max.y, min.z, min.x, max.y, max.z, max.x, max.y, max.z, max.x, max.y, min.z)
        drawQuad(buffer, matrix, color, min.x, min.y, min.z, min.x, max.y, min.z, max.x, max.y, min.z, max.x, min.y, min.z)
        drawQuad(buffer, matrix, color, min.x, min.y, max.z, max.x, min.y, max.z, max.x, max.y, max.z, min.x, max.y, max.z)
        drawQuad(buffer, matrix, color, min.x, min.y, min.z, min.x, min.y, max.z, min.x, max.y, max.z, min.x, max.y, min.z)
        drawQuad(buffer, matrix, color, max.x, min.y, min.z, max.x, max.y, min.z, max.x, max.y, max.z, max.x, min.y, max.z)
    }

    private fun drawQuad(
        buffer: VertexConsumer,
        matrix: PoseStack.Pose,
        color: Color,
        x1: Double,
        y1: Double,
        z1: Double,
        x2: Double,
        y2: Double,
        z2: Double,
        x3: Double,
        y3: Double,
        z3: Double,
        x4: Double,
        y4: Double,
        z4: Double,
    ) {
        addVertex(buffer, matrix, x1, y1, z1, color)
        addVertex(buffer, matrix, x2, y2, z2, color)
        addVertex(buffer, matrix, x3, y3, z3, color)
        addVertex(buffer, matrix, x4, y4, z4, color)
    }

    private fun addVertex(
        buffer: VertexConsumer,
        matrix: PoseStack.Pose,
        x: Double,
        y: Double,
        z: Double,
        color: Color,
    ) {
        buffer.addVertex(matrix, x.toFloat(), y.toFloat(), z.toFloat())
            .setColor(color.red, color.green, color.blue, color.alpha)
    }

    private fun drawBlockOutline(
        context: SkysoftRenderContext,
        block: WorldVec,
        color: Color,
        lineWidth: Int,
        depth: Boolean,
    ) {
        val min = block
        val max = block + WorldVec(1.0, 1.0, 1.0)
        LineBoxRenderer.draw3D(context, lineWidth, depth) {
            drawBox(min, max, color)
        }
    }

    private const val DEFAULT_LINE_WIDTH = 3
    private const val DEFAULT_EXPAND = 0.02
}
