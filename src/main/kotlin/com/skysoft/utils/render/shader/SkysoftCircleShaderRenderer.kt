// SPDX-License-Identifier: LGPL-2.1-only
// Adapted from SkyHanni; see credits.md for attribution and source details.

package com.skysoft.utils.render.shader

import com.skysoft.utils.MinecraftRenderer
import com.skysoft.utils.render.GuiRenderStateAccess
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import org.joml.Matrix3x2f
import java.awt.Color

object SkysoftCircleShaderRenderer {
    fun drawFilledCircle(
        context: GuiGraphicsExtractor,
        x: Int,
        y: Int,
        color: Color,
        radius: Int,
        smoothness: Float = 1f,
        angle1: Float = 7f,
        angle2: Float = 7f,
    ) {
        val diameter = radius * 2
        val params = buildRoundedStateParams(context, x, y, diameter, diameter, radius)
        val state = SkysoftCircleRenderState(
            x = x,
            y = y,
            width = diameter,
            height = diameter,
            color = color.rgb,
            smoothness = smoothness,
            angle1 = angle1 - Math.PI.toFloat(),
            angle2 = angle2 - Math.PI.toFloat(),
            params = params,
        )
        //~ if < 26.1 'addGuiElement' -> 'submitGuiElement'
        GuiRenderStateAccess.get(context).addGuiElement(state)
    }

    private fun buildRoundedStateParams(
        context: GuiGraphicsExtractor,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        radius: Int,
    ): RoundedRectShaderParams {
        val windowState = MinecraftRenderer.windowRenderState(Minecraft.getInstance().gameRenderer)
        val scaleFactor = windowState.guiScale.toFloat()
        val halfSizeX = width * scaleFactor / 2
        val halfSizeY = height * scaleFactor / 2
        val matrix = Matrix3x2f(context.pose())
        val xScale = matrix.m00()
        val yScale = matrix.m11()
        val xTranslation = matrix.m20()
        val yTranslation = matrix.m21()
        val centerX = x + width / 2f
        val centerY = y + height / 2f
        return RoundedRectShaderParams(
            cornerRadius = radius.toFloat(),
            shaderHalfWidth = halfSizeX * xScale,
            shaderHalfHeight = halfSizeY * yScale,
            shaderCenterX = (centerX * xScale + xTranslation) * scaleFactor,
            shaderCenterY = windowState.height - (centerY * yScale + yTranslation) * scaleFactor,
            poseScaleX = xScale,
            poseScaleY = yScale,
            poseOffsetX = xTranslation,
            poseOffsetY = yTranslation,
        )
    }
}
