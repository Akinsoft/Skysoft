package com.skysoft.features.inventory

import com.mojang.blaze3d.pipeline.BlendFunction
import com.mojang.blaze3d.pipeline.RenderPipeline
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.textures.FilterMode
import com.mojang.blaze3d.vertex.BufferBuilder
import com.mojang.blaze3d.vertex.VertexConsumer
import com.skysoft.SkysoftMod
import com.skysoft.config.RarityHighlightDetailsConfig
import com.skysoft.config.RarityHighlightConfig
import com.skysoft.config.RarityHighlightType
import com.skysoft.config.SkysoftConfigGui
import com.skysoft.data.hypixel.HypixelLocationState
import com.skysoft.data.ravengard.RavengardItemRarity
import com.skysoft.data.skyblock.SkyBlockItemRarity
import com.skysoft.data.skyblock.SkyBlockRarity
import com.skysoft.utils.ColorUtilities.withAlpha
import com.skysoft.utils.render.SkysoftDrawMode
import com.skysoft.utils.render.SkysoftPipelineBuilder
import com.skysoft.utils.render.shader.SkysoftCircleShaderRenderer
import com.skysoft.utils.render.shader.SkysoftVertexFormats
import com.skysoft.utils.render.shader.SkysoftVertexFormats.writeParams
import java.awt.Color
import java.util.IdentityHashMap
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.navigation.ScreenRectangle
import net.minecraft.client.gui.render.GuiItemAtlas
import net.minecraft.client.gui.render.TextureSetup
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.client.renderer.state.gui.GuiElementRenderState
import net.minecraft.client.renderer.state.gui.GuiItemRenderState
import net.minecraft.client.renderer.state.gui.GuiRenderState
import net.minecraft.world.item.ItemStack

object RarityHighlightRenderer {
    private val skyBlockConfig get() = SkysoftConfigGui.config().inventory.rarityHighlight
    private val ravengardConfig get() = SkysoftConfigGui.config().ravengard.rarityHighlight
    private val contourColors = IdentityHashMap<GuiItemRenderState, Int>()
    private var pendingContourColor: Int? = null

    @JvmStatic
    fun beginFrame() {
        contourColors.clear()
        pendingContourColor = null
    }

    @JvmStatic
    fun renderContainerBackgrounds(context: GuiGraphicsExtractor, screen: AbstractContainerScreen<*>) {
        val highlight = containerConfig()
        if (!highlight.isEnabled || highlight.settings.type == RarityHighlightType.CONTOUR) return
        if (StorageOverlayController.isActive(screen)) return
        for (slot in screen.menu.slots) {
            if (slot.isActive) {
                val color = containerRarityColor(slot.item) ?: continue
                renderBackground(context, slot.x, slot.y, color, highlight.settings.type, highlight.details.opacity)
            }
        }
    }

    @JvmStatic
    fun renderBackground(context: GuiGraphicsExtractor, stack: ItemStack, x: Int, y: Int) {
        val highlight = skyBlockConfig
        if (!highlight.isEnabled || highlight.settings.type == RarityHighlightType.CONTOUR) return
        val color = rarity(stack)?.color?.rgb ?: return
        renderBackground(context, x, y, color, highlight.settings.type, highlight.details.opacity)
    }

    private fun renderBackground(
        context: GuiGraphicsExtractor,
        x: Int,
        y: Int,
        rarityColor: Int,
        type: RarityHighlightType,
        opacity: Int,
    ) {
        val color = rarityColor.withOpacity(opacity)
        when (type) {
            RarityHighlightType.ROUND -> SkysoftCircleShaderRenderer.drawFilledCircle(
                context,
                x + 1,
                y + 1,
                Color(color, true),
                ROUND_RADIUS,
            )

            RarityHighlightType.SQUARE -> context.fill(
                x,
                y,
                x + SLOT_SIZE,
                y + SLOT_SIZE,
                color,
            )

            RarityHighlightType.CONTOUR -> Unit
        }
    }

    @JvmStatic
    fun renderSlot(
        context: GuiGraphicsExtractor,
        stack: ItemStack,
        x: Int,
        y: Int,
        render: () -> Unit,
    ) {
        renderBackground(context, stack, x, y)
        renderItem(stack, render)
    }

    @JvmStatic
    fun renderItem(stack: ItemStack, render: () -> Unit) {
        val highlight = skyBlockConfig
        if (!highlight.isContourEnabled()) {
            render()
            return
        }
        renderContourItem(rarity(stack)?.color?.rgb, highlight.details.opacity, render)
    }

    @JvmStatic
    fun renderContainerItem(stack: ItemStack, render: () -> Unit) {
        val highlight = containerConfig()
        if (!highlight.isContourEnabled()) {
            render()
            return
        }
        renderContourItem(containerRarityColor(stack), highlight.details.opacity, render)
    }

    private fun renderContourItem(rarityColor: Int?, opacity: Int, render: () -> Unit) {
        if (rarityColor == null) {
            render()
            return
        }
        val color = rarityColor.withOpacity(opacity)
        val previousColor = pendingContourColor
        pendingContourColor = color
        try {
            render()
        } finally {
            pendingContourColor = previousColor
        }
    }

    @JvmStatic
    fun attachContour(itemState: GuiItemRenderState) {
        pendingContourColor?.let { contourColors[itemState] = it }
    }

    @JvmStatic
    fun renderContour(
        renderState: GuiRenderState,
        itemState: GuiItemRenderState,
        slotView: GuiItemAtlas.SlotView,
    ) {
        val color = contourColors.remove(itemState) ?: return
        renderState.addGlyphToCurrentLayer(RarityContourRenderState(itemState, slotView, color))
    }

    internal fun rarity(stack: ItemStack): SkyBlockRarity? = SkyBlockItemRarity.from(stack)

    private fun containerConfig(): RarityHighlightConfig =
        if (HypixelLocationState.inRavengard) ravengardConfig else skyBlockConfig

    private fun RarityHighlightConfig.isContourEnabled(): Boolean =
        isEnabled && settings.type == RarityHighlightType.CONTOUR

    private fun containerRarityColor(stack: ItemStack): Int? =
        if (HypixelLocationState.inRavengard) {
            RavengardItemRarity.color(stack)
        } else {
            rarity(stack)?.color?.rgb
        }

    private fun Int.withOpacity(opacity: Int): Int {
        val clampedOpacity = opacity.coerceIn(
            RarityHighlightDetailsConfig.MIN_OPACITY,
            RarityHighlightDetailsConfig.MAX_OPACITY,
        )
        val alpha = (clampedOpacity * MAX_ALPHA + HALF_PERCENT) / RarityHighlightDetailsConfig.MAX_OPACITY
        return withAlpha(alpha)
    }

    private const val SLOT_SIZE = 16
    private const val ROUND_RADIUS = 7
    private const val MAX_ALPHA = 255
    private const val HALF_PERCENT = 50
}

private class RarityContourRenderState(
    private val itemState: GuiItemRenderState,
    private val slotView: GuiItemAtlas.SlotView,
    private val color: Int,
) : GuiElementRenderState {
    override fun pipeline(): RenderPipeline = RARITY_CONTOUR_PIPELINE

    override fun textureSetup(): TextureSetup = TextureSetup.singleTexture(
        slotView.textureView(),
        RenderSystem.getSamplerCache().getRepeat(FilterMode.NEAREST),
    )

    override fun scissorArea(): ScreenRectangle? = itemState.scissorArea()

    override fun bounds(): ScreenRectangle? {
        val bounds = ScreenRectangle(
            itemState.x() - CONTOUR_WIDTH,
            itemState.y() - CONTOUR_WIDTH,
            CONTOUR_SIZE,
            CONTOUR_SIZE,
        ).transformMaxBounds(itemState.pose())
        return itemState.scissorArea()?.intersection(bounds) ?: bounds
    }

    override fun buildVertices(consumer: VertexConsumer) {
        val uStep = (slotView.u1() - slotView.u0()) / ITEM_SIZE
        val vStep = (slotView.v1() - slotView.v0()) / ITEM_SIZE
        writeVertex(
            consumer,
            itemState.x() - CONTOUR_WIDTH.toFloat(),
            itemState.y() - CONTOUR_WIDTH.toFloat(),
            slotView.u0() - uStep,
            slotView.v0() - vStep,
        )
        writeVertex(
            consumer,
            itemState.x() - CONTOUR_WIDTH.toFloat(),
            itemState.y() + ITEM_SIZE + CONTOUR_WIDTH,
            slotView.u0() - uStep,
            slotView.v1() + vStep,
        )
        writeVertex(
            consumer,
            itemState.x() + ITEM_SIZE + CONTOUR_WIDTH,
            itemState.y() + ITEM_SIZE + CONTOUR_WIDTH,
            slotView.u1() + uStep,
            slotView.v1() + vStep,
        )
        writeVertex(
            consumer,
            itemState.x() + ITEM_SIZE + CONTOUR_WIDTH,
            itemState.y() - CONTOUR_WIDTH.toFloat(),
            slotView.u1() + uStep,
            slotView.v0() - vStep,
        )
    }

    private fun writeVertex(consumer: VertexConsumer, x: Float, y: Float, u: Float, v: Float) {
        val buffer = consumer as BufferBuilder
        buffer.addVertexWith2DPose(itemState.pose(), x, y).setUv(u, v).setColor(color)
        buffer.writeParams(
            slotView.u0(),
            slotView.v0(),
            slotView.u1(),
            slotView.v1(),
            SkysoftVertexFormats.VertexElement.CONTOUR_UV_BOUNDS,
        )
    }

    companion object {
        private const val ITEM_SIZE = 16f
        private const val CONTOUR_WIDTH = 1
        private const val CONTOUR_SIZE = 18
    }
}

private val RARITY_CONTOUR_PIPELINE = RenderPipelines.register(
    SkysoftPipelineBuilder.build(
        location = SkysoftMod.id("rarity_contour"),
        snippet = SkysoftPipelineBuilder.guiTexturedSnippet(),
        vertexFormat = SkysoftVertexFormats.POSITION_TEX_COLOR_CONTOUR,
        drawMode = SkysoftDrawMode.QUADS,
        blend = BlendFunction.TRANSLUCENT,
        vertexShader = SkysoftMod.id("rarity_contour"),
        fragmentShader = SkysoftMod.id("rarity_contour"),
        depthWrite = false,
    ),
)
