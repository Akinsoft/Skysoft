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

object WorldItemRenderLayers {
    private val BADGE_DEPTH_STATE = DepthStencilState(CompareOp.ALWAYS_PASS, true)
    private val cutoutPipeline = itemPipeline("world_item_xray_cutout")
    private val translucentPipeline = itemPipeline("world_item_xray_translucent", BlendFunction.TRANSLUCENT)
    private val layers = mutableMapOf<Pair<Identifier, Boolean>, RenderType>()
    private val throughWallsState = ThreadLocal.withInitial { false }

    fun beginItemRender(isThroughWalls: Boolean) = throughWallsState.set(isThroughWalls)

    fun endItemRender() = throughWallsState.remove()

    fun isRenderingThroughWalls(): Boolean = throughWallsState.get()

    @JvmStatic
    fun throughWalls(texture: Identifier, isTranslucent: Boolean): RenderType =
        layers.getOrPut(texture to isTranslucent) {
            val setupBuilder = RenderSetup.builder(if (isTranslucent) translucentPipeline else cutoutPipeline)
                .withTexture("Sampler0", texture)
            SkysoftPipelineBuilder.configureItemRenderSetup(setupBuilder)
            if (isTranslucent) setupBuilder.sortOnUpload()
            RenderType("skysoft_world_item_xray", setupBuilder.createRenderSetup())
        }

    private fun itemPipeline(name: String, blend: BlendFunction? = null) = RenderPipelines.register(
        SkysoftPipelineBuilder.build(
            location = SkysoftMod.id(name),
            snippet = SkysoftPipelineBuilder.itemSnippet(),
            vertexFormat = DefaultVertexFormat.ENTITY,
            drawMode = SkysoftDrawMode.QUADS,
            blend = blend,
            shaderDefines = mapOf("ALPHA_CUTOUT" to ITEM_ALPHA_CUTOUT),
            depthStencilState = BADGE_DEPTH_STATE,
        ),
    )

    private const val ITEM_ALPHA_CUTOUT = 0.1f
}
