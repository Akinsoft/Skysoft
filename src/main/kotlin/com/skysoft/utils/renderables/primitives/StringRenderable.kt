package com.skysoft.utils.renderables.primitives

import com.skysoft.utils.renderables.withIsolatedPose
import com.skysoft.utils.gui.GuiAlignment
import com.skysoft.utils.render.LegacyTextRenderer
import com.skysoft.utils.renderables.GuiRenderable
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import kotlin.math.roundToInt

data class StringRenderable(
    private val text: String,
    private val scale: Double = 1.0,
    private val color: Int = 0xFFFFFFFF.toInt(),
    override val horizontalAlign: GuiAlignment.HorizontalAlignment = GuiAlignment.HorizontalAlignment.LEFT,
    override val verticalAlign: GuiAlignment.VerticalAlignment = GuiAlignment.VerticalAlignment.TOP,
) : GuiRenderable {
    override val width: Int = (LegacyTextRenderer.width(text) * scale).roundToInt()
    override val height: Int = (Minecraft.getInstance().font.lineHeight * scale).roundToInt()

    override fun render(context: GuiGraphicsExtractor) {
        context.withIsolatedPose {
            context.pose().scale(scale.toFloat(), scale.toFloat())
            LegacyTextRenderer.draw(context, text, 0, 0, defaultColor = color)
        }
    }
}
