// SPDX-License-Identifier: LGPL-2.1-only
// Adapted from SkyHanni; see credits.md for attribution and source details.

package com.skysoft.utils.render

import com.mojang.blaze3d.PrimitiveTopology
import com.mojang.blaze3d.pipeline.BlendFunction
import com.mojang.blaze3d.pipeline.ColorTargetState
import com.mojang.blaze3d.pipeline.DepthStencilState
import com.mojang.blaze3d.pipeline.RenderPipeline
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.VertexFormat
import net.minecraft.client.renderer.BindGroupLayouts
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.resources.Identifier
import java.util.Optional

object SkysoftPipelineBuilder {
    private val itemPipelineSnippet = RenderPipeline.builder()
        .withBindGroupLayout(BindGroupLayouts.GLOBALS)
        .withBindGroupLayout(BindGroupLayouts.MATRICES_PROJECTION)
        .withBindGroupLayout(BindGroupLayouts.FOG)
        .withBindGroupLayout(BindGroupLayouts.LIGHTING)
        .withVertexShader("core/item")
        .withFragmentShader("core/item")
        .withBindGroupLayout(BindGroupLayouts.SAMPLER0_SAMPLER1_SAMPLER2)
        .withVertexBinding(0, DefaultVertexFormat.ENTITY)
        .withPrimitiveTopology(PrimitiveTopology.QUADS)
        .withDepthStencilState(DepthStencilState.DEFAULT)
        .buildSnippet()

    fun guiSnippet(): RenderPipeline.Snippet = RenderPipelines.GUI_SNIPPET
    fun guiTexturedSnippet(): RenderPipeline.Snippet = RenderPipelines.GUI_TEXTURED_SNIPPET
    fun itemSnippet(): RenderPipeline.Snippet = itemPipelineSnippet

    fun build(
        location: Identifier,
        snippet: RenderPipeline.Snippet,
        vertexFormat: VertexFormat,
        drawMode: SkysoftDrawMode,
        blend: BlendFunction? = null,
        vertexShader: Identifier? = null,
        fragmentShader: Identifier? = null,
        shaderDefines: Map<String, Float> = emptyMap(),
        depthStencilState: DepthStencilState? = null,
        depthWrite: Boolean = true,
    ): RenderPipeline = RenderPipeline.builder(snippet)
        .withLocation(location)
        .withVertexBinding(0, vertexFormat)
        .withPrimitiveTopology(drawMode.toPrimitiveTopology())
        .apply {
            blend?.let { withColorTargetState(ColorTargetState(it)) }
            vertexShader?.let { withVertexShader(it) }
            fragmentShader?.let { withFragmentShader(it) }
            shaderDefines.forEach(::withShaderDefine)
            if (depthStencilState != null) {
                withDepthStencilState(depthStencilState)
            } else if (!depthWrite) {
                withDepthStencilState(Optional.empty())
            }
        }
        .build()

    private fun SkysoftDrawMode.toPrimitiveTopology(): PrimitiveTopology = when (this) {
        SkysoftDrawMode.LINES -> PrimitiveTopology.LINES
        SkysoftDrawMode.QUADS -> PrimitiveTopology.QUADS
    }
}
