package com.skysoft.utils.render

import com.mojang.blaze3d.vertex.PoseStack
import com.skysoft.utils.WorldVec
import com.skysoft.utils.toWorldVec
import net.minecraft.client.Minecraft
import net.minecraft.client.Camera
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.state.level.CameraRenderState
import java.awt.Color

class SkysoftRenderContext(
    val matrices: PoseStack,
    val submitNodeCollector: SubmitNodeCollector,
    val partialTicks: Float,
    val camera: Camera,
    val cameraRenderState: CameraRenderState,
) {
    internal inline fun <T> withIsolatedPose(block: () -> T): T {
        matrices.pushPose()
        return try {
            block()
        } finally {
            matrices.popPose()
        }
    }

    fun drawLineToCrosshair(
        location: WorldVec,
        color: Color,
        lineWidth: Int = DEFAULT_LINE_WIDTH,
        depth: Boolean = false,
    ) {
        val start = exactPlayerCrosshairLocation()
        LineBoxRenderer.draw3D(this, lineWidth, depth) {
            draw3DLine(start, location, color)
        }
    }

    private fun exactPlayerCrosshairLocation(): WorldVec {
        val player = Minecraft.getInstance().player ?: return WorldVec(0.0, 0.0, 0.0)
        return player.getEyePosition(partialTicks).toWorldVec() + player.lookAngle.toWorldVec() * CROSSHAIR_PICK_DISTANCE
    }
}

private const val DEFAULT_LINE_WIDTH = 3
private const val CROSSHAIR_PICK_DISTANCE = 2.0
