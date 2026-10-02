// SPDX-License-Identifier: LGPL-2.1-only
// Adapted from SkyHanni; see credits.md for attribution and source details.

package com.skysoft.utils.render.shader

import com.mojang.renderpearl.api.pipeline.BlendFunction
import com.mojang.renderpearl.api.pipeline.RenderPipeline
import com.mojang.blaze3d.vertex.BufferBuilder
import com.mojang.blaze3d.vertex.VertexConsumer
import com.skysoft.SkysoftMod
import com.skysoft.utils.render.SkysoftDrawMode
import com.skysoft.utils.render.SkysoftPipelineBuilder
import com.skysoft.utils.render.shader.SkysoftVertexFormats.writeParams
import net.minecraft.client.gui.navigation.ScreenRectangle
import net.minecraft.client.gui.render.TextureSetup
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.client.renderer.state.gui.GuiElementRenderState
import kotlin.math.abs
import kotlin.math.min

class SkysoftCircleRenderState(
    private val x: Int,
    private val y: Int,
    private val width: Int,
    private val height: Int,
    private val color: Int,
    private val smoothness: Float,
    private val angle1: Float,
    private val angle2: Float,
    private val params: RoundedRectShaderParams,
    private val scissor: ScreenRectangle? = null,
) : GuiElementRenderState {
    private val padding = 5

    override fun pipeline(): RenderPipeline = CIRCLE_DEFERRED_PIPELINE

    override fun bounds(): ScreenRectangle {
        val left = params.poseX((x - padding).toFloat())
        val top = params.poseY((y - padding).toFloat())
        val right = params.poseX((x + width + padding).toFloat())
        val bottom = params.poseY((y + height + padding).toFloat())
        return ScreenRectangle(
            min(left, right).toInt(),
            min(top, bottom).toInt(),
            abs(right - left).toInt(),
            abs(bottom - top).toInt(),
        )
    }

    override fun scissorArea() = scissor

    override fun textureSetup() = TextureSetup.noTexture()

    override fun buildVertices(consumer: VertexConsumer) {
        val left = x - padding.toFloat()
        val top = y - padding.toFloat()
        val right = x + width + padding.toFloat()
        val bottom = y + height + padding.toFloat()
        emitQuad(consumer, left, top, right, bottom)
    }

    private fun emitQuad(consumer: VertexConsumer, left: Float, top: Float, right: Float, bottom: Float) {
        writeVertex(consumer, left, top)
        writeVertex(consumer, left, bottom)
        writeVertex(consumer, right, bottom)
        writeVertex(consumer, right, top)
    }

    private fun writeVertex(consumer: VertexConsumer, vx: Float, vy: Float) {
        val buffer = consumer as BufferBuilder
        buffer.addVertex(params.poseX(vx), params.poseY(vy), 0f)
        buffer.setColor(color)
        buffer.writeParams(
            params.cornerRadius,
            smoothness,
            params.shaderHalfWidth,
            params.shaderHalfHeight,
            SkysoftVertexFormats.VertexElement.ROUNDED_PARAMS_0,
        )
        buffer.writeParams(
            params.shaderCenterX,
            params.shaderCenterY,
            angle1,
            angle2,
            SkysoftVertexFormats.VertexElement.ROUNDED_PARAMS_1,
        )
    }
}

private val CIRCLE_DEFERRED_PIPELINE = RenderPipelines.register(
    SkysoftPipelineBuilder.build(
        location = SkysoftMod.id("circle_deferred"),
        snippet = SkysoftPipelineBuilder.guiSnippet(),
        vertexFormat = SkysoftVertexFormats.POSITION_COLOR_ROUNDED,
        drawMode = SkysoftDrawMode.QUADS,
        blend = BlendFunction.TRANSLUCENT,
        vertexShader = SkysoftMod.id("circle_deferred"),
        fragmentShader = SkysoftMod.id("circle_deferred"),
        depthWrite = false,
    ),
)
