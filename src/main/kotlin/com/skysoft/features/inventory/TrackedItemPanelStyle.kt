package com.skysoft.features.inventory

import com.skysoft.utils.ColorUtilities.RGB_MASK
import com.skysoft.utils.ColorUtilities.withScaledAlpha
import com.skysoft.utils.gui.Rect
import com.skysoft.utils.gui.fill
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import net.minecraft.util.FormattedCharSequence

internal object TrackedItemPanelStyle {
    const val ROW_HEIGHT = 11
    const val TEXT_HEIGHT = 9
    const val ICON_TEXT_OFFSET = 16
    const val ICON_SCALE = 0.75
    const val TITLE_COLOR = 0xFFFFFF55.toInt()
    const val MUTED_COLOR = 0xFFAAAAAA.toInt()
    const val ACTION_COLOR = 0xFF55FF55.toInt()
    const val DANGER_COLOR = 0xFFFF5555.toInt()
    private const val TEXT_COLOR = 0xFFFFFFFF.toInt()
    private const val HOVER_COLOR = 0x28FFFFFF

    fun styledText(text: String, color: Int, bold: Boolean = false): MutableComponent =
        Component.literal(text).withStyle { style -> style.withColor(color and RGB_MASK).withBold(bold) }

    fun drawText(context: GuiGraphicsExtractor, text: Component, x: Int, y: Int, opacity: Double) {
        context.text(Minecraft.getInstance().font, text, x, y, TEXT_COLOR.withScaledAlpha(opacity), false)
    }

    fun drawText(context: GuiGraphicsExtractor, text: FormattedCharSequence, x: Int, y: Int, opacity: Double) {
        context.text(Minecraft.getInstance().font, text, x, y, TEXT_COLOR.withScaledAlpha(opacity), false)
    }

    fun drawHover(context: GuiGraphicsExtractor, bounds: Rect, opacity: Double) {
        context.fill(bounds, HOVER_COLOR.withScaledAlpha(opacity))
    }
}
