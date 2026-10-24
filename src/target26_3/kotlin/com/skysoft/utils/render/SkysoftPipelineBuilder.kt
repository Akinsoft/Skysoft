// SPDX-License-Identifier: LGPL-2.1-only
// Adapted from SkyHanni; see credits.md for attribution and source details.

package com.skysoft.utils.render

import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.renderpearl.api.pipeline.BlendFunction
import com.mojang.renderpearl.api.pipeline.ColorTargetState
import com.mojang.renderpearl.api.pipeline.CompareOp
import com.mojang.renderpearl.api.pipeline.DepthStencilState
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology
import com.mojang.renderpearl.api.pipeline.RenderPipeline
import com.mojang.renderpearl.api.vertex.VertexFormat
import net.minecraft.client.renderer.BindGroupLayouts
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.client.renderer.oit.OitPipelineSet
import net.minecraft.client.renderer.rendertype.RenderSetup
import net.minecraft.resources.Identifier
import java.util.Optional

object SkysoftPipelineBuilder {
    private val itemPipelineSnippet = RenderPipeline.builder()
        .withBindGroupLayout(BindGroupLayouts.GLOBALS)
        .withBindGroupLayout(BindGroupLayouts.PROJECTION)
        .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
        .withBindGroupLayout(BindGroupLayouts.FOG)
        .withBindGroupLayout(BindGroupLayouts.LIGHTING)
        .withVertexShader("core/item")
        .withFragmentShader("core/item")
        .withBindGroupLayout(BindGroupLayouts.SAMPLER0_SAMPLER1_SAMPLER2)
        .withVertexBinding(0, DefaultVertexFormat.ENTITY)
        .withPrimitiveTopology(PrimitiveTopology.QUADS)
        .withDepthStencilState(DepthStencilState.DEFAULT)
        .buildSnippet()

    private val oitPipelines = mutableMapOf(RenderPipelines.DEBUG_QUADS to lazyOf(RenderPipelines.OIT_DEBUG_QUADS))

    fun guiSnippet(): RenderPipeline.Snippet = RenderPipelines.GUI_SNIPPET
    fun guiTexturedSnippet(): RenderPipeline.Snippet = RenderPipelines.GUI_TEXTURED_SNIPPET
    fun itemSnippet(): RenderPipeline.Snippet = itemPipelineSnippet

    fun renderSetup(pipeline: RenderPipeline): RenderSetup.RenderSetupBuilder =
        RenderSetup.builder(pipeline).apply { oitPipelines[pipeline]?.let { setOitPipelines(it.value) } }

    internal fun configureItemRenderSetup(builder: RenderSetup.RenderSetupBuilder) {
        builder.useLightmap().useOverlay()
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
    ): RenderPipeline {
        val builder = RenderPipeline.builder(snippet)
            .withVertexBinding(0, vertexFormat)
            .withPrimitiveTopology(drawMode.toPrimitiveTopology())
            .withShaderDefine(SHADER_PLATFORM_DEFINE)
            .apply {
                vertexShader?.let { withVertexShader(it) }
                fragmentShader?.let { withFragmentShader(it) }
                shaderDefines.forEach(::withShaderDefine)
                if (depthStencilState != null) {
                    withDepthStencilState(depthStencilState)
                } else if (!depthWrite) {
                    withDepthStencilState(Optional.empty())
                }
            }
        val ignoresDepth = depthStencilState?.depthTest() == CompareOp.ALWAYS_PASS || (depthStencilState == null && !depthWrite)
        val oitPipelineSet = blend?.let {
            OitPipelineSet.builder("${location.namespace}_${location.path}", builder)
                .apply { if (ignoresDepth) withoutDepthTest() }
        }
        builder.withColorTargetState(blend?.let(::ColorTargetState) ?: ColorTargetState.DEFAULT)
        val pipeline = builder.withLocation(location).build()
        oitPipelineSet?.let { oitPipelines[pipeline] = lazy { RenderPipelines.register(it.build()) } }
        return pipeline
    }

    private fun SkysoftDrawMode.toPrimitiveTopology(): PrimitiveTopology = when (this) {
        SkysoftDrawMode.LINES -> PrimitiveTopology.LINES
        SkysoftDrawMode.QUADS -> PrimitiveTopology.QUADS
    }

    private const val SHADER_PLATFORM_DEFINE = "SKYSOFT_RENDERPEARL"
}
