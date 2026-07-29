package com.skysoft.utils.render

import com.mojang.blaze3d.pipeline.BlendFunction
import com.mojang.blaze3d.pipeline.DepthStencilState
import com.mojang.blaze3d.platform.CompareOp
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.skysoft.SkysoftMod
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.client.renderer.rendertype.RenderSetup
import net.minecraft.client.renderer.rendertype.RenderType
import net.minecraft.resources.Identifier

object EntityHighlightRenderLayers {
    private val layers = mutableMapOf<Identifier, RenderType>()

    @JvmStatic
    fun fill(texture: Identifier): RenderType = layers.getOrPut(texture) {
        val setup = RenderSetup.builder(ENTITY_HIGHLIGHT_FILL_PIPELINE).withTexture("Sampler0", texture)
        SkysoftPipelineBuilder.configureItemRenderSetup(setup)
        RenderType("skysoft_entity_highlight_fill", setup.sortOnUpload().createRenderSetup())
    }
}

private val ENTITY_HIGHLIGHT_FILL_PIPELINE = RenderPipelines.register(
    SkysoftPipelineBuilder.build(
        location = SkysoftMod.id("entity_highlight_fill"),
        snippet = SkysoftPipelineBuilder.itemSnippet(),
        vertexFormat = DefaultVertexFormat.ENTITY,
        drawMode = SkysoftDrawMode.QUADS,
        blend = BlendFunction.TRANSLUCENT,
        vertexShader = SkysoftMod.id("entity_highlight_fill"),
        fragmentShader = SkysoftMod.id("entity_highlight_fill"),
        depthStencilState = DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false),
    ),
)
