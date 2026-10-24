package com.skysoft.utils.render

import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.client.renderer.rendertype.LayeringTransform
import net.minecraft.client.renderer.rendertype.RenderType

object SkysoftRenderLayers {
    private val LINES: RenderType = RenderType(
        "skysoft_lines",
        SkysoftPipelineBuilder.renderSetup(SkysoftRenderPipeline.LINES())
            .setLayeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
            .createRenderSetup(),
    )

    private val LINES_XRAY: RenderType = RenderType(
        "skysoft_lines_xray",
        SkysoftPipelineBuilder.renderSetup(SkysoftRenderPipeline.LINES_XRAY())
            .setLayeringTransform(LayeringTransform.NO_LAYERING)
            .createRenderSetup(),
    )

    private val FILLED_BOX: RenderType = RenderType(
        "skysoft_filled_box",
        SkysoftPipelineBuilder.renderSetup(RenderPipelines.DEBUG_QUADS)
            .setLayeringTransform(LayeringTransform.NO_LAYERING)
            .createRenderSetup(),
    )

    private val FILLED_SHAPE: RenderType = RenderType(
        "skysoft_filled_shape",
        SkysoftPipelineBuilder.renderSetup(RenderPipelines.DEBUG_QUADS)
            .setLayeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
            .createRenderSetup(),
    )

    fun getLines(throughWalls: Boolean): RenderType = if (throughWalls) LINES_XRAY else LINES

    fun filledBox(): RenderType = FILLED_BOX

    fun filledShape(): RenderType = FILLED_SHAPE
}
