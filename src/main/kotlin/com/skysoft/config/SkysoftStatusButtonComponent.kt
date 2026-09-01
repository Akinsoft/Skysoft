package com.skysoft.config

import io.github.notenoughupdates.moulconfig.GuiTextures
import io.github.notenoughupdates.moulconfig.common.text.StructuredText
import io.github.notenoughupdates.moulconfig.gui.GuiComponent
import io.github.notenoughupdates.moulconfig.gui.GuiImmediateContext
import io.github.notenoughupdates.moulconfig.gui.MouseEvent

internal class SkysoftStatusButtonComponent(
    private val statusText: () -> String,
    private val statusColor: () -> Int,
    private val buttonText: () -> String,
    private val onClick: () -> Unit,
) : GuiComponent() {
    override fun getWidth(): Int = BUTTON_WIDTH

    override fun getHeight(): Int = HEIGHT

    override fun render(context: GuiImmediateContext) {
        val render = context.renderContext
        val font = render.minecraft.defaultFontRenderer
        render.drawStringCenteredScaledMaxWidth(
            StructuredText.of(statusText()),
            font,
            BUTTON_WIDTH.toFloat() / 2,
            STATUS_TEXT_Y,
            false,
            BUTTON_WIDTH,
            statusColor(),
        )
        render.drawTexturedRect(
            GuiTextures.BUTTON,
            0f,
            BUTTON_Y.toFloat(),
            BUTTON_WIDTH.toFloat(),
            BUTTON_HEIGHT.toFloat(),
        )
        render.drawStringCenteredScaledMaxWidth(
            StructuredText.of(buttonText()),
            font,
            BUTTON_WIDTH.toFloat() / 2,
            BUTTON_Y + BUTTON_TEXT_Y_OFFSET,
            false,
            BUTTON_WIDTH - BUTTON_TEXT_MAX_WIDTH_INSET,
            BUTTON_TEXT_COLOR,
        )
    }

    override fun mouseEvent(mouseEvent: MouseEvent, context: GuiImmediateContext): Boolean {
        if (mouseEvent !is MouseEvent.Click || !mouseEvent.mouseState || mouseEvent.mouseButton != 0) return false
        if (context.mouseX !in 0..BUTTON_WIDTH) return false
        if (context.mouseY !in BUTTON_Y..(BUTTON_Y + BUTTON_HEIGHT)) return false
        onClick()
        return true
    }

    private companion object {
        private const val HEIGHT = 34
        private const val BUTTON_WIDTH = 70
        private const val BUTTON_Y = 16
        private const val BUTTON_HEIGHT = 16
        private const val STATUS_TEXT_Y = 7f
        private const val BUTTON_TEXT_Y_OFFSET = 8f
        private const val BUTTON_TEXT_MAX_WIDTH_INSET = 4
        private val BUTTON_TEXT_COLOR = 0xFF303030.toInt()
    }
}
