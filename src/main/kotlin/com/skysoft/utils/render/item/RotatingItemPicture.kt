// SPDX-License-Identifier: LGPL-2.1-only
// Adapted from SkyHanni; see credits.md for attribution and source details.

package com.skysoft.utils.render.item

import com.mojang.blaze3d.platform.Lighting
import com.mojang.blaze3d.vertex.PoseStack
import com.skysoft.utils.MinecraftRenderer
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.navigation.ScreenRectangle
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.item.TrackingItemStackRenderState
import net.minecraft.client.renderer.state.gui.GuiItemRenderState
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.world.phys.Vec3
import org.joml.Matrix3x2f
import org.joml.Quaternionf

class RotatingItemPicture(
    private val source: GuiItemRenderState,
    private val model: TrackingItemStackRenderState,
    private val rotation: Vec3,
    requestedScale: Float,
    val opacity: Float,
) : PictureInPictureRenderState {
    private val side = (requestedScale.takeIf { it.isFinite() && it > 0f } ?: 1f) * ITEM_SIDE
    private val right = side.toInt().coerceAtLeast(1)

    init {
        model.appendModelIdentityElement(rotation)
        if (rotation != Vec3.ZERO) model.setAnimated()
    }

    override fun x0(): Int = 0
    override fun y0(): Int = 0
    override fun x1(): Int = right
    override fun y1(): Int = right
    override fun scale(): Float = side
    override fun scissorArea(): ScreenRectangle? = source.scissorArea()
    override fun pose(): Matrix3x2f = source.pose()
    override fun bounds(): ScreenRectangle? = source.bounds()

    fun drawModel(poseStack: PoseStack, nodes: SubmitNodeCollector) {
        if (poseStack.didApplyRotation(rotation)) model.setAnimated()
        poseStack.translate(0.0f, MODEL_Y_OFFSET, MODEL_Z_OFFSET)
        val lighting = if (model.usesBlockLight()) Lighting.Entry.ITEMS_3D else Lighting.Entry.ITEMS_FLAT
        MinecraftRenderer.lighting(Minecraft.getInstance().gameRenderer).setupFor(lighting)
        model.submit(poseStack, nodes, FULL_BRIGHT_LIGHT, OverlayTexture.NO_OVERLAY, 0)
    }

    private companion object {
        private const val ITEM_SIDE = 16f
        private const val MODEL_Y_OFFSET = 0.03f
        private const val MODEL_Z_OFFSET = 0.125f
        private const val FULL_BRIGHT_LIGHT = 15_728_880
    }
}

private fun PoseStack.didApplyRotation(rotation: Vec3): Boolean {
    val xRad = Math.toRadians(rotation.x % FULL_ROTATION_DEGREES).toFloat()
    val yRad = Math.toRadians(rotation.y % FULL_ROTATION_DEGREES).toFloat()
    val zRad = Math.toRadians(rotation.z % FULL_ROTATION_DEGREES).toFloat()
    if (xRad == 0f && yRad == 0f && zRad == 0f) return false

    mulPose(Quaternionf().rotateXYZ(xRad, yRad, zRad))
    return true
}

private const val FULL_ROTATION_DEGREES = 360
