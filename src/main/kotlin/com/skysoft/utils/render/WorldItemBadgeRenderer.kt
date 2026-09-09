package com.skysoft.utils.render

import com.skysoft.utils.WorldVec
import com.skysoft.utils.toWorldVec
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.renderer.item.TrackingItemStackRenderState
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.network.chat.Component
import net.minecraft.util.LightCoordsUtil
import net.minecraft.world.item.ItemDisplayContext
import net.minecraft.world.item.ItemStack

object WorldItemBadgeRenderer {
    fun draw(
        context: SkysoftRenderContext,
        anchor: WorldVec,
        stack: ItemStack,
        badge: Component,
        maxRenderDistance: Double = DEFAULT_MAX_RENDER_DISTANCE,
        cameraOffset: Double = DEFAULT_CAMERA_OFFSET,
    ) {
        if (stack.isEmpty) return
        val cameraPosition = context.camera.position().toWorldVec()
        val distance = cameraPosition.distance(anchor)
        if (distance > maxRenderDistance) return
        val scale = (distance / SCALE_DISTANCE * SCALE_MULTIPLIER)
            .coerceIn(MIN_SCALE, MAX_SCALE)
            .toFloat()
        val itemScale = BASE_ITEM_SCALE * scale
        val displayAnchor = anchor + (cameraPosition - anchor).normalize() * cameraOffset

        context.withIsolatedPose {
            context.matrices.translate(
                displayAnchor.x - cameraPosition.x,
                displayAnchor.y - cameraPosition.y,
                displayAnchor.z - cameraPosition.z,
            )
            context.matrices.mulPose(context.cameraRenderState.orientation)
            renderItem(context, stack, itemScale)
            renderBadge(context, badge, itemScale)
        }
    }

    private fun renderItem(context: SkysoftRenderContext, stack: ItemStack, scale: Float) {
        val minecraft = Minecraft.getInstance()
        val itemState = TrackingItemStackRenderState()
        minecraft.itemModelResolver.updateForTopItem(
            itemState,
            stack,
            ItemDisplayContext.GUI,
            minecraft.level,
            null,
            0,
        )
        context.withIsolatedPose {
            context.matrices.scale(scale, -scale, -scale)
            itemState.submit(
                context.matrices,
                context.submitNodeCollector,
                LightCoordsUtil.FULL_BRIGHT,
                OverlayTexture.NO_OVERLAY,
                THROUGH_WALLS_MARKER,
            )
        }
    }

    private fun renderBadge(context: SkysoftRenderContext, badge: Component, itemScale: Float) {
        val textScale = itemScale * BADGE_SCALE
        context.withIsolatedPose {
            context.matrices.translate(
                (itemScale * BADGE_X_OFFSET).toDouble(),
                (-itemScale * BADGE_Y_OFFSET).toDouble(),
                BADGE_Z_OFFSET,
            )
            context.matrices.scale(textScale, -textScale, textScale)
            context.submitNodeCollector.submitText(
                context.matrices,
                0f,
                0f,
                badge.visualOrderText,
                true,
                Font.DisplayMode.SEE_THROUGH,
                LightCoordsUtil.FULL_BRIGHT,
                0xFFFFFFFF.toInt(),
                0,
                0,
            )
        }
    }

    const val THROUGH_WALLS_MARKER = Int.MIN_VALUE
    private const val BASE_ITEM_SCALE = 0.48f
    private const val DEFAULT_CAMERA_OFFSET = 0.35
    private const val SCALE_DISTANCE = 12.0
    private const val SCALE_MULTIPLIER = 1.35
    private const val MIN_SCALE = 0.9
    private const val MAX_SCALE = 4.0
    private const val BADGE_SCALE = 0.03f
    private const val BADGE_X_OFFSET = 0.22f
    private const val BADGE_Y_OFFSET = 0.22f
    private const val BADGE_Z_OFFSET = -0.02
    private const val DEFAULT_MAX_RENDER_DISTANCE = 50.0
}
