package com.skysoft.utils.render

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import com.skysoft.utils.WorldVec
import net.minecraft.world.phys.shapes.VoxelShape
import java.awt.Color

class LineBoxRenderer private constructor(
    private val context: SkysoftRenderContext,
    private val lineWidth: Int,
    private val depth: Boolean,
) {
    fun draw3DLine(p1: WorldVec, p2: WorldVec, color: Color) {
        val layer = SkysoftRenderLayers.getLines(!depth)
        val normal = (p2 - p1).normalize()
        context.submitNodeCollector.submitCustomGeometry(context.matrices, layer) { matrix, buffer ->
            addLine(buffer, matrix, p1, p2, normal, color)
        }
    }

    fun drawShape(origin: WorldVec, shape: VoxelShape, color: Color) {
        val layer = SkysoftRenderLayers.getLines(!depth)
        context.submitNodeCollector.submitCustomGeometry(context.matrices, layer) { matrix, buffer ->
            shape.forAllEdges { x1, y1, z1, x2, y2, z2 ->
                val p1 = origin + WorldVec(x1, y1, z1)
                val p2 = origin + WorldVec(x2, y2, z2)
                addLine(buffer, matrix, p1, p2, (p2 - p1).normalize(), color)
            }
        }
    }

    private fun addLine(
        buffer: VertexConsumer,
        matrix: PoseStack.Pose,
        p1: WorldVec,
        p2: WorldVec,
        normal: WorldVec,
        color: Color,
    ) {
        buffer.addVertex(matrix.pose(), p1.x.toFloat(), p1.y.toFloat(), p1.z.toFloat())
            .setNormal(matrix, normal.x.toFloat(), normal.y.toFloat(), normal.z.toFloat())
            .setColor(color.red, color.green, color.blue, color.alpha)
            .setLineWidth(lineWidth.toFloat())

        buffer.addVertex(matrix.pose(), p2.x.toFloat(), p2.y.toFloat(), p2.z.toFloat())
            .setNormal(matrix, normal.x.toFloat(), normal.y.toFloat(), normal.z.toFloat())
            .setColor(color.red, color.green, color.blue, color.alpha)
            .setLineWidth(lineWidth.toFloat())
    }

    fun drawBox(min: WorldVec, max: WorldVec, color: Color) {
        draw3DLine(WorldVec(min.x, min.y, min.z), WorldVec(max.x, min.y, min.z), color)
        draw3DLine(WorldVec(min.x, min.y, max.z), WorldVec(max.x, min.y, max.z), color)
        draw3DLine(WorldVec(min.x, max.y, min.z), WorldVec(max.x, max.y, min.z), color)
        draw3DLine(WorldVec(min.x, max.y, max.z), WorldVec(max.x, max.y, max.z), color)
        draw3DLine(WorldVec(min.x, min.y, min.z), WorldVec(min.x, min.y, max.z), color)
        draw3DLine(WorldVec(max.x, min.y, min.z), WorldVec(max.x, min.y, max.z), color)
        draw3DLine(WorldVec(min.x, max.y, min.z), WorldVec(min.x, max.y, max.z), color)
        draw3DLine(WorldVec(max.x, max.y, min.z), WorldVec(max.x, max.y, max.z), color)
        draw3DLine(WorldVec(min.x, min.y, min.z), WorldVec(min.x, max.y, min.z), color)
        draw3DLine(WorldVec(max.x, min.y, min.z), WorldVec(max.x, max.y, min.z), color)
        draw3DLine(WorldVec(min.x, min.y, max.z), WorldVec(min.x, max.y, max.z), color)
        draw3DLine(WorldVec(max.x, min.y, max.z), WorldVec(max.x, max.y, max.z), color)
    }

    companion object {
        fun draw3D(
            context: SkysoftRenderContext,
            lineWidth: Int,
            depth: Boolean,
            draws: LineBoxRenderer.() -> Unit,
        ) {
            context.withIsolatedPose {
                val cameraPos = context.camera.position()
                context.matrices.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z)

                LineBoxRenderer(context, lineWidth, depth).draws()
            }
        }
    }
}
