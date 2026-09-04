package com.skysoft.gui.tooltip

import com.skysoft.utils.gui.OverlayItemRowStyle
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
        setComponentForNextFrame(
            context,
            ItemActionTooltip(
                stack,
                LegacyTextRenderer.formattedSequence(
                    if (action.isNullOrBlank()) formattedItemName else "§7$action $formattedItemName",
                ),
                actionLines.map(LegacyTextRenderer::formattedSequence),
            ),
            mouseX,
            mouseY,
        )
    }

    fun setItemRowsForNextFrame(
        context: GuiGraphicsExtractor,
        title: String,
        rows: List<ItemRow>,
        mouseX: Int,
        mouseY: Int,
    ) {
        setComponentForNextFrame(context, ItemRowsTooltip(title, rows), mouseX, mouseY)
    }

    private fun setComponentForNextFrame(
        context: GuiGraphicsExtractor,
        tooltip: SkysoftTooltipComponent,
        mouseX: Int,
        mouseY: Int,
    ) {
        TooltipViewport.clear()
        context.setTooltipForNextFrame(
            Minecraft.getInstance().font,
            emptyList<FormattedCharSequence>(),
            Optional.of(tooltip),
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

    data class ItemRow(val stack: ItemStack?, val label: String, val value: String)

    private data class ItemRowsTooltip(
        val title: String,
        val rows: List<ItemRow>,
    ) : SkysoftTooltipComponent {
        override fun clientComponent(): ClientTooltipComponent = ClientItemRowsTooltip(this)
    }

    private class ClientItemRowsTooltip(tooltip: ItemRowsTooltip) : ClientTooltipComponent {
        private val title = LegacyTextRenderer.formattedSequence(tooltip.title)
        private val rows = tooltip.rows.map { row ->
            Triple(
                row.stack,
                LegacyTextRenderer.formattedSequence(row.label),
                LegacyTextRenderer.formattedSequence(row.value),
            )
        }
        private val labelWidth = rows.maxOfOrNull { Minecraft.getInstance().font.width(it.second) } ?: 0
        private val valueX = OverlayItemRowStyle.ICON_TEXT_OFFSET + labelWidth + OverlayItemRowStyle.VALUE_COLUMN_GAP

        override fun getHeight(font: Font): Int = ITEM_TOOLTIP_HEIGHT + rows.size * OverlayItemRowStyle.HEIGHT

        override fun getWidth(font: Font): Int = maxOf(
            font.width(title),
            valueX + (rows.maxOfOrNull { font.width(it.third) } ?: 0),
        )

        override fun extractImage(
            font: Font,
            x: Int,
            y: Int,
            width: Int,
            height: Int,
            context: GuiGraphicsExtractor,
        ) {
            context.text(font, title, x, y, ITEM_TOOLTIP_COLOR, false)
            rows.forEachIndexed { index, (stack, label, value) ->
                val rowY = y + ITEM_TOOLTIP_HEIGHT + index * OverlayItemRowStyle.HEIGHT
                stack?.let {
                    ItemIconRenderable(it, OverlayItemRowStyle.ICON_SCALE).renderAt(context, x, rowY)
                }
                val textY = rowY + OverlayItemRowStyle.TEXT_Y_OFFSET
                context.text(font, label, x + OverlayItemRowStyle.ICON_TEXT_OFFSET, textY, ITEM_TOOLTIP_COLOR, false)
                context.text(font, value, x + valueX, textY, ITEM_TOOLTIP_COLOR, false)
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
