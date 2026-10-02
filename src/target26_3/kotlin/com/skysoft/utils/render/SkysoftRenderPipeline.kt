package com.skysoft.utils.render

import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.renderpearl.api.pipeline.RenderPipeline
import com.skysoft.SkysoftMod
import net.minecraft.client.renderer.RenderPipelines

enum class SkysoftRenderPipeline(
    private val depthWrite: Boolean = true,
) {
    LINES,
    LINES_XRAY(depthWrite = false),
    ;

    private val internalPipeline: RenderPipeline = RenderPipelines.register(
        SkysoftPipelineBuilder.build(
            location = SkysoftMod.id(name.lowercase()),
            snippet = RenderPipelines.LINES_SNIPPET,
            vertexFormat = DefaultVertexFormat.POSITION_COLOR_NORMAL_LINE_WIDTH,
            drawMode = SkysoftDrawMode.LINES,
            depthWrite = depthWrite,
        ),
    )

    operator fun invoke(): RenderPipeline = internalPipeline
}
