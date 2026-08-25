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
    private val xRotationSpeedDegreesPerSecond: Float = 0f,
    private val yRotationSpeedDegreesPerSecond: Float = 0f,
    private val zRotationSpeedDegreesPerSecond: Float = 0f,
    private val alpha: Float = 1f,
    private val highQualityScaling: Boolean = false,
    override val horizontalAlign: GuiAlignment.HorizontalAlignment = GuiAlignment.HorizontalAlignment.LEFT,
    override val verticalAlign: GuiAlignment.VerticalAlignment = GuiAlignment.VerticalAlignment.TOP,
) : GuiRenderable {
    private val renderScale: Double = scale.takeIf { it.isFinite() && it > 0.0 } ?: 0.0

    override val width: Int = (16 * renderScale).roundToInt()
    override val height: Int = (16 * renderScale).roundToInt()

    override fun render(context: GuiGraphicsExtractor) {
        if (stack.isEmpty || renderScale <= 0.0 || alpha <= 0f) return

        val elapsedSeconds = if (
            xRotationSpeedDegreesPerSecond != 0f ||
            yRotationSpeedDegreesPerSecond != 0f ||
            zRotationSpeedDegreesPerSecond != 0f
        ) {
            (System.currentTimeMillis() % MILLIS_PER_HOUR) / MILLIS_PER_SECOND_FLOAT
        } else {
            0f
        }
        val rotationVector = Vec3(
            (xRotationDegrees + xRotationSpeedDegreesPerSecond * elapsedSeconds).toDouble(),
            (yRotationDegrees + yRotationSpeedDegreesPerSecond * elapsedSeconds).toDouble(),
            (zRotationDegrees + zRotationSpeedDegreesPerSecond * elapsedSeconds).toDouble(),
        )
        if (
            rotationVector != Vec3.ZERO ||
            alpha < OPAQUE_ALPHA_THRESHOLD ||
            highQualityScaling && renderScale != 1.0
        ) {
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
        private const val MILLIS_PER_HOUR = 3_600_000L
        private const val MILLIS_PER_SECOND_FLOAT = 1000f
    }
}
