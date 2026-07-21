package com.skysoft.utils.renderables.primitives

import com.skysoft.utils.gui.GuiAlignment
import com.skysoft.utils.render.item.SkysoftItemRenderSupport
import com.skysoft.utils.renderables.GuiRenderable
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.world.item.ItemStack
import net.minecraft.world.phys.Vec3
import kotlin.math.roundToInt

data class ItemIconRenderable(
    private val stack: ItemStack,
    private val scale: Double = 1.0,
    private val xRotationDegrees: Float = 0f,
    private val yRotationDegrees: Float = 0f,
    private val zRotationDegrees: Float = 0f,
    private val alpha: Float = 1f,
    override val horizontalAlign: GuiAlignment.HorizontalAlignment = GuiAlignment.HorizontalAlignment.LEFT,
    override val verticalAlign: GuiAlignment.VerticalAlignment = GuiAlignment.VerticalAlignment.TOP,
) : GuiRenderable {
    private val renderScale: Double = scale.takeIf { it.isFinite() && it > 0.0 } ?: 0.0

    override val width: Int = (16 * renderScale).roundToInt()
    override val height: Int = (16 * renderScale).roundToInt()

    override fun render(context: GuiGraphicsExtractor) {
        if (stack.isEmpty || renderScale <= 0.0 || alpha <= 0f) return

        val rotationVector = Vec3(xRotationDegrees.toDouble(), yRotationDegrees.toDouble(), zRotationDegrees.toDouble())
        if (rotationVector != Vec3.ZERO || alpha < OPAQUE_ALPHA_THRESHOLD) {
            SkysoftItemRenderSupport.submit(context, stack, renderScale, rotationVector, alpha)
            return
        }

        context.pose().pushMatrix()
        context.pose().scale(renderScale.toFloat(), renderScale.toFloat())
        context.item(stack, 0, 0)
        context.pose().popMatrix()
    }

    private companion object {
        private const val OPAQUE_ALPHA_THRESHOLD = 0.999f
    }
}
