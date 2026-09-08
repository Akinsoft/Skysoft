package com.skysoft.features.inventory

import com.skysoft.config.core.HudPosition
import com.skysoft.gui.OverlayControlArea
import com.skysoft.gui.OverlayControlMouse
import com.skysoft.gui.transform
import com.skysoft.utils.MinecraftClient
import com.skysoft.utils.gui.OverlayItemRowStyle
import com.skysoft.utils.gui.OverlayPanelStyle
import com.skysoft.utils.gui.OverlayTextStyle
import com.skysoft.utils.gui.Rect
import com.skysoft.utils.input.InputUtilities
import com.skysoft.utils.render.LegacyTextRenderer
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen

internal class InventoryTrackerFrame(position: HudPosition, private val width: Int, height: Int) {
    private val minecraft = Minecraft.getInstance()
    private val mouse = InputUtilities.scaledMousePosition(minecraft)
    private val normalMouse = OverlayControlMouse.normalPoint(mouse.x, mouse.y)
    private val screenMouse = OverlayControlMouse.screenPoint(mouse.x, mouse.y)
    val screenMouseX = screenMouse.first
    val screenMouseY = screenMouse.second
    private val screen = MinecraftClient.screen(minecraft) as? AbstractContainerScreen<*>
    val interactive = screen != null &&
        !InventoryOverlayInput.isPointCovered(screen, screenMouseX.toDouble(), screenMouseY.toDouble())
    private val transform = position.transform(0, 0)
    private val localMouseX = transform.localX(normalMouse.first)
    private val localMouseY = transform.localY(normalMouse.second)
    private val placePanelRight = transform.fitsRight(width, SIDE_PANEL_ESTIMATED_WIDTH, minecraft.window.guiScaledWidth)
    val isHovered = interactive && localMouseX in 0 until width && localMouseY in 0 until height

    fun <T> render(
        context: GuiGraphicsExtractor,
        render: (mouseX: Int?, mouseY: Int?, placePanelRight: Boolean) -> OverlayControlArea<T>?,
    ): OverlayControlArea<T>? {
        context.nextStratum()
        val control = transform.render(context) {
            render(localMouseX.takeIf { interactive }, localMouseY.takeIf { interactive }, placePanelRight)
        } ?: return null
        return control.copy(bounds = transform.screenBounds(control.bounds))
    }
}

internal class InventoryTrackerLayout(
    title: String,
    private val emptyText: String,
    private val indicatorText: String,
    rowWidths: List<Int>,
    minimumWidth: Int,
    private val showTitle: Boolean,
    private val background: Boolean,
    private val inventoryOpen: Boolean,
) {
    private val padding = if (background) OverlayPanelStyle.PADDING else 0
    private val rowCount = rowWidths.size
    private val titleText = OverlayTextStyle.title(title)
    private val moreLine = "§7..."
    private val contentWidth = maxOf(
        minimumWidth,
        if (showTitle) LegacyTextRenderer.width(titleText) else 0,
        rowWidths.maxOrNull() ?: LegacyTextRenderer.width(emptyText),
        LegacyTextRenderer.width(indicatorText),
        if (inventoryOpen) LegacyTextRenderer.width(moreLine) else 0,
    )
    val width: Int = contentWidth + padding * 2
    val height: Int = padding * 2 +
        (if (showTitle) OverlayTextStyle.TITLE_HEIGHT else 0) +
        (if (rowCount == 0) OverlayTextStyle.ROW_HEIGHT else rowCount * OverlayItemRowStyle.HEIGHT) +
        (if (indicatorText.isEmpty()) 0 else OverlayTextStyle.ROW_HEIGHT) +
        (if (inventoryOpen) CONTROL_ROW_HEIGHT else 0)

    fun <T> render(
        context: GuiGraphicsExtractor,
        mouseX: Int?,
        mouseY: Int?,
        moreAction: T,
        moreTooltip: List<String> = emptyList(),
        renderRow: (index: Int, left: Int, right: Int, y: Int) -> OverlayControlArea<T>?,
    ): OverlayControlArea<T>? {
        if (background) OverlayPanelStyle.draw(context, 0, 0, width, height)
        var y = padding
        if (showTitle) {
            LegacyTextRenderer.draw(context, titleText, padding, y)
            y += OverlayTextStyle.TITLE_HEIGHT
        }
        var hovered: OverlayControlArea<T>? = null
        if (rowCount == 0) {
            LegacyTextRenderer.draw(context, emptyText, padding, y)
            y += OverlayTextStyle.ROW_HEIGHT
        } else {
            repeat(rowCount) { index ->
                hovered = renderRow(index, padding, width - padding, y) ?: hovered
                y += OverlayItemRowStyle.HEIGHT
            }
        }
        if (indicatorText.isNotEmpty()) {
            LegacyTextRenderer.draw(context, indicatorText, padding, y)
            y += OverlayTextStyle.ROW_HEIGHT
        }
        if (inventoryOpen) {
            val moreWidth = LegacyTextRenderer.width(moreLine)
            val bounds = Rect(width - padding - moreWidth, y, moreWidth, CONTROL_ROW_HEIGHT)
            if (mouseX != null && mouseY != null && bounds.contains(mouseX, mouseY)) {
                OverlayTextStyle.drawControlHover(context, bounds, 1.0)
                hovered = OverlayControlArea(moreAction, bounds, moreTooltip)
            }
            LegacyTextRenderer.draw(context, moreLine, bounds.x, y + CONTROL_TEXT_Y_OFFSET)
        }
        return hovered
    }
}

private const val CONTROL_ROW_HEIGHT = 13
private const val CONTROL_TEXT_Y_OFFSET = 1
private const val SIDE_PANEL_ESTIMATED_WIDTH = 190
