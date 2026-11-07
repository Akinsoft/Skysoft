package com.skysoft.utils.render.item

import com.skysoft.utils.render.GuiRenderStateAccess
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.renderer.item.TrackingItemStackRenderState
import net.minecraft.client.renderer.state.gui.GuiItemRenderState
import net.minecraft.world.item.ItemDisplayContext
import net.minecraft.world.item.ItemStack
import net.minecraft.world.phys.Vec3
import org.joml.Matrix3x2f

object SkysoftItemRenderSupport {
    fun submit(
        context: GuiGraphicsExtractor,
        stack: ItemStack,
        scale: Double,
        rotationVector: Vec3,
        alpha: Float,
    ) {
        if (stack.isEmpty || !scale.isFinite() || scale <= 0.0 || !alpha.isFinite() || alpha <= 0f) return

        val trackingState = TrackingItemStackRenderState()
        Minecraft.getInstance().itemModelResolver.updateForTopItem(
            trackingState,
            stack,
            ItemDisplayContext.GUI,
            null,
            null,
            0,
        )
        val pose = Matrix3x2f(context.pose())
        val guiItemRenderState = GuiItemRenderState(
            pose,
            trackingState,
            0,
            0,
            null,
        )
        val state = RotatingItemPicture(
            source = guiItemRenderState,
            model = trackingState,
            rotation = rotationVector,
            requestedScale = scale.toFloat(),
            opacity = alpha,
        )
        GuiRenderStateAccess.get(context).addPicturesInPictureState(state)
    }
}
