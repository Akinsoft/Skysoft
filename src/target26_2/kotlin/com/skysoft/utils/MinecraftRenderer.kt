package com.skysoft.utils

import com.mojang.blaze3d.platform.Lighting
import com.mojang.blaze3d.vertex.PoseStack
import com.skysoft.utils.render.WorldItemBadgeRenderer
import net.minecraft.client.Camera
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner
import net.minecraft.client.model.Model
import net.minecraft.client.renderer.GameRenderer
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.item.ItemStackRenderState
import net.minecraft.client.renderer.rendertype.RenderType
import net.minecraft.client.renderer.state.WindowRenderState
import net.minecraft.client.renderer.state.level.CameraRenderState
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.resources.Identifier
import net.minecraft.util.LightCoordsUtil
import org.joml.Quaternionfc

object MinecraftRenderer {
    fun mainCamera(renderer: GameRenderer): Camera = renderer.mainCamera()

    fun lighting(renderer: GameRenderer): Lighting = renderer.lighting()

    fun windowRenderState(renderer: GameRenderer): WindowRenderState = renderer.gameRenderState().windowRenderState

    fun invalidateCompiledGeometry(minecraft: Minecraft) {
        val level = minecraft.level ?: return
        minecraft.levelRenderer.invalidateCompiledGeometry(
            level,
            minecraft.options,
            minecraft.gameRenderer.mainCamera(),
            minecraft.blockColors,
        )
    }

    fun rotate(poseStack: PoseStack, rotation: Quaternionfc) {
        poseStack.mulPose(rotation)
    }

    fun extractCameraRenderState(camera: Camera, state: CameraRenderState, deltaTracker: DeltaTracker) {
        camera.extractRenderState(state, deltaTracker.getGameTimeDeltaPartialTick(false))
    }

    fun tooltip(
        graphics: GuiGraphicsExtractor,
        font: Font,
        components: List<ClientTooltipComponent>,
        positioner: ClientTooltipPositioner,
        style: Identifier?,
    ) {
        graphics.tooltip(font, components, 0, 0, positioner, style)
    }

    fun <S : Any> submitTintedModel(
        collector: SubmitNodeCollector,
        model: Model<in S>,
        state: S,
        poseStack: PoseStack,
        renderType: RenderType,
        light: Int,
        color: Int,
    ) {
        collector.submitModel(model, state, poseStack, renderType, light, 0, color, null, 0, null)
    }

    fun submitThroughWallsItem(itemState: ItemStackRenderState, poseStack: PoseStack, collector: SubmitNodeCollector) {
        itemState.submit(
            poseStack,
            collector,
            LightCoordsUtil.FULL_BRIGHT,
            OverlayTexture.NO_OVERLAY,
            WorldItemBadgeRenderer.THROUGH_WALLS_MARKER,
        )
    }
}
