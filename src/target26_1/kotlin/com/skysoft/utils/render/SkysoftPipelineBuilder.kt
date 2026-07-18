// SPDX-License-Identifier: LGPL-2.1-only
// Adapted from SkyHanni; see credits.md for attribution and source details.

package com.skysoft.utils.render

import com.mojang.blaze3d.pipeline.BlendFunction
import com.mojang.blaze3d.pipeline.ColorTargetState
import com.mojang.blaze3d.pipeline.DepthStencilState
import com.mojang.blaze3d.pipeline.RenderPipeline
import com.mojang.blaze3d.shaders.UniformType
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.VertexFormat
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.client.renderer.rendertype.RenderSetup
import net.minecraft.resources.Identifier
import java.util.Optional

object SkysoftPipelineBuilder {
    private val itemPipelineSnippet = RenderPipeline.builder()
        .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
        .withUniform("Projection", UniformType.UNIFORM_BUFFER)
        .withUniform("Fog", UniformType.UNIFORM_BUFFER)
        .withUniform("Lighting", UniformType.UNIFORM_BUFFER)
        .withVertexShader("core/item")
        .withFragmentShader("core/item")
        .withSampler("Sampler0")
        .withSampler("Sampler2")
        .withVertexFormat(DefaultVertexFormat.ENTITY, VertexFormat.Mode.QUADS)
        .withDepthStencilState(DepthStencilState.DEFAULT)
        .buildSnippet()

    fun guiSnippet(): RenderPipeline.Snippet = RenderPipelines.GUI_SNIPPET
    fun guiTexturedSnippet(): RenderPipeline.Snippet = RenderPipelines.GUI_TEXTURED_SNIPPET
    fun itemSnippet(): RenderPipeline.Snippet = itemPipelineSnippet

    internal fun configureItemRenderSetup(builder: RenderSetup.RenderSetupBuilder) {
        builder.useLightmap()
    }

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
        .withVertexFormat(vertexFormat, drawMode.toVertexFormatMode())
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

    private fun SkysoftDrawMode.toVertexFormatMode(): VertexFormat.Mode = when (this) {
        SkysoftDrawMode.LINES -> VertexFormat.Mode.LINES
        SkysoftDrawMode.QUADS -> VertexFormat.Mode.QUADS
    }
}
