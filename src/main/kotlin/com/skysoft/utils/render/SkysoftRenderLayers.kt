package com.skysoft.utils.render

import com.skysoft.utils.render.gpu.RenderPipeline
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.client.renderer.rendertype.LayeringTransform
import net.minecraft.client.renderer.rendertype.RenderType

object SkysoftRenderLayers {
    private val LINES = layer("skysoft_lines", SkysoftRenderPipeline.LINES(), LayeringTransform.VIEW_OFFSET_Z_LAYERING)
    private val LINES_XRAY = layer("skysoft_lines_xray", SkysoftRenderPipeline.LINES_XRAY(), LayeringTransform.NO_LAYERING)
    private val FILLED_BOX = layer("skysoft_filled_box", RenderPipelines.DEBUG_QUADS, LayeringTransform.NO_LAYERING)
    private val FILLED_SHAPE = layer("skysoft_filled_shape", RenderPipelines.DEBUG_QUADS, LayeringTransform.VIEW_OFFSET_Z_LAYERING)

    fun getLines(throughWalls: Boolean): RenderType = if (throughWalls) LINES_XRAY else LINES

    fun filledBox(): RenderType = FILLED_BOX

    fun filledShape(): RenderType = FILLED_SHAPE

    private fun layer(name: String, pipeline: RenderPipeline, layering: LayeringTransform): RenderType =
        RenderType(name, SkysoftPipelineBuilder.renderSetup(pipeline).setLayeringTransform(layering).createRenderSetup())
}
