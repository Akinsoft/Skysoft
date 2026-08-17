package com.skysoft.gui.tooltip

import com.skysoft.utils.render.LegacyTextRenderer
import com.skysoft.utils.renderables.primitives.ItemIconRenderable
import com.skysoft.utils.renderables.renderAt
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner
import net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner
import net.minecraft.util.FormattedCharSequence
import net.minecraft.world.item.ItemStack
import java.util.Optional
import org.joml.Vector2ic

object SkysoftNativeTooltip {
    fun setForNextFrame(
        context: GuiGraphicsExtractor,
        lines: List<String>,
        mouseX: Int,
        mouseY: Int,
        scrollable: Boolean = true,
        positioner: ClientTooltipPositioner? = null,
    ) {
        if (lines.isEmpty()) return
        if (!scrollable) TooltipViewport.clear()
        context.setTooltipForNextFrame(
            Minecraft.getInstance().font,
            lines.map(LegacyTextRenderer::formattedSequence),
            positioner ?: if (scrollable) DefaultTooltipPositioner.INSTANCE else NonScrollableTooltipPositioner,
            mouseX,
            mouseY,
            true,
        )
    }

    fun setItemActionForNextFrame(
        context: GuiGraphicsExtractor,
        stack: ItemStack,
        action: String?,
        formattedItemName: String,
        mouseX: Int,
        mouseY: Int,
        actionLines: List<String> = emptyList(),
    ) {
        TooltipViewport.clear()
        context.setTooltipForNextFrame(
            Minecraft.getInstance().font,
            emptyList<FormattedCharSequence>(),
            Optional.of(
                ItemActionTooltip(
                    stack,
                    LegacyTextRenderer.formattedSequence(
                        if (action.isNullOrBlank()) formattedItemName else "§7$action $formattedItemName",
                    ),
                    actionLines.map(LegacyTextRenderer::formattedSequence),
                ),
            ),
            NonScrollableTooltipPositioner,
            mouseX,
            mouseY,
            true,
            null,
        )
    }

    private data class ItemActionTooltip(
        val stack: ItemStack,
        val text: FormattedCharSequence,
        val actionLines: List<FormattedCharSequence>,
    ) : SkysoftTooltipComponent {
        override fun clientComponent(): ClientTooltipComponent = ClientItemActionTooltip(this)
    }

    private class ClientItemActionTooltip(
        private val tooltip: ItemActionTooltip,
    ) : ClientTooltipComponent {
        override fun getHeight(font: Font): Int = ITEM_TOOLTIP_HEIGHT +
            tooltip.actionLines.size * ITEM_TOOLTIP_LINE_HEIGHT

        override fun getWidth(font: Font): Int = maxOf(
            ITEM_TOOLTIP_TEXT_X + font.width(tooltip.text),
            tooltip.actionLines.maxOfOrNull(font::width) ?: 0,
        )

        override fun extractImage(
            font: Font,
            x: Int,
            y: Int,
            width: Int,
            height: Int,
            context: GuiGraphicsExtractor,
        ) {
            ItemIconRenderable(tooltip.stack, ITEM_TOOLTIP_ICON_SCALE).renderAt(context, x, y + ITEM_TOOLTIP_ICON_Y)
            context.text(
                font,
                tooltip.text,
                x + ITEM_TOOLTIP_TEXT_X,
                y + ITEM_TOOLTIP_TEXT_Y,
                ITEM_TOOLTIP_COLOR,
                false,
            )
            tooltip.actionLines.forEachIndexed { index, line ->
                context.text(
                    font,
                    line,
                    x,
                    y + ITEM_TOOLTIP_HEIGHT + index * ITEM_TOOLTIP_LINE_HEIGHT,
                    ITEM_TOOLTIP_COLOR,
                    false,
                )
            }
        }
    }

    private object NonScrollableTooltipPositioner : ClientTooltipPositioner, TooltipViewportExcludedPositioner {
        override fun positionTooltip(
            screenWidth: Int,
            screenHeight: Int,
            x: Int,
            y: Int,
            tooltipWidth: Int,
            tooltipHeight: Int,
        ): Vector2ic = DefaultTooltipPositioner.INSTANCE.positionTooltip(
            screenWidth,
            screenHeight,
            x,
            y,
            tooltipWidth,
            tooltipHeight,
        )
    }

    private const val ITEM_TOOLTIP_HEIGHT = 10
    private const val ITEM_TOOLTIP_LINE_HEIGHT = 10
    private const val ITEM_TOOLTIP_TEXT_X = 10
    private const val ITEM_TOOLTIP_TEXT_Y = 1
    private const val ITEM_TOOLTIP_ICON_SCALE = 0.5
    private const val ITEM_TOOLTIP_ICON_Y = 1
    private const val ITEM_TOOLTIP_COLOR = 0xFFFFFFFF.toInt()
}
