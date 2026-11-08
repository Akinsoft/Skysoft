package com.skysoft.gui

import com.skysoft.utils.gui.Point
import com.skysoft.utils.gui.Rect
import com.skysoft.utils.gui.fill
import com.skysoft.utils.renderables.withIsolatedPose
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor

internal fun GuiGraphicsExtractor.fillEditorPanel(bounds: Rect, padding: Int, isSelected: Boolean) {
    fill(bounds.inset(-padding), if (isSelected) PANEL_HOVER else PANEL_BACKGROUND)
}

internal fun renderEditorElement(
    context: GuiGraphicsExtractor,
    element: HudEditorElement,
    isSelected: Boolean,
    padding: Int,
) {
    val bounds = element.editorBounds()
    if (element.hasEditorBackground) {
        context.fillEditorPanel(bounds, padding, isSelected)
    } else if (isSelected) {
        context.fillOutline(bounds.inset(-padding), HUD_EDITOR_BORDER, PANEL_HOVER)
    }
    val scale = element.position.scale
    context.withIsolatedPose {
        context.pose().translate(bounds.x.toFloat(), bounds.y.toFloat())
        context.pose().scale(scale, scale)
        element.renderEditor(context)
    }
    if (isSelected && (element.canResizeWidth || element.canResizeHeight)) {
        context.drawResizeHandles(bounds)
    }
}

internal fun renderEditorGrid(context: GuiGraphicsExtractor, element: HudEditorElement?) {
    val window = Minecraft.getInstance().window
    val spacing = element?.editorGridSpacing ?: HUD_EDITOR_GRID_SPACING
    val origin = element?.let { target ->
        val bounds = target.editorBounds()
        Point(bounds.x - target.position.x, bounds.y - target.position.y)
    } ?: Point(0, 0)
    val color = if (spacing < HUD_EDITOR_GRID_SPACING) FINE_GRID_COLOR else GRID_COLOR
    for (x in origin.x.mod(spacing)..window.guiScaledWidth step spacing) {
        context.fill(x, 0, x + 1, window.guiScaledHeight, color)
    }
    for (y in origin.y.mod(spacing)..window.guiScaledHeight step spacing) {
        context.fill(0, y, window.guiScaledWidth, y + 1, color)
    }
}

internal fun renderSnapGuides(context: GuiGraphicsExtractor, snapper: HudEditorSnapper, isActive: Boolean) {
    if (!isActive || !Minecraft.getInstance().hasShiftDown()) return
    val guides = snapper.guides()
    for (guide in guides) {
        context.fillSnapGuide(guide, SNAP_GUIDE_OUTLINE_WIDTH, SNAP_GUIDE_OUTLINE_COLOR)
        context.fillSnapGuide(guide, 0, SNAP_GUIDE_COLOR)
    }
    guides.map(HudSnapGuide::targetBounds).distinct().forEach { bounds ->
        val targetBounds = Rect(bounds.left, bounds.top, bounds.right - bounds.left, bounds.bottom - bounds.top)
        context.fillOutline(targetBounds.inset(-1), 1, SNAP_TARGET_OUTLINE_COLOR)
    }
}

private fun GuiGraphicsExtractor.fillSnapGuide(guide: HudSnapGuide, outset: Int, color: Int) {
    val lineStart = guide.coordinate - outset
    val lineEnd = guide.coordinate + outset + 1
    if (guide.axis == HudSnapAxis.HORIZONTAL) {
        fill(lineStart, guide.start, lineEnd, guide.end + 1, color)
    } else {
        fill(guide.start, lineStart, guide.end + 1, lineEnd, color)
    }
}

private fun GuiGraphicsExtractor.fillOutline(bounds: Rect, thickness: Int, color: Int) {
    val right = bounds.x + bounds.width
    val bottom = bounds.y + bounds.height
    fill(bounds.x, bounds.y, right, bounds.y + thickness, color)
    fill(bounds.x, bottom - thickness, right, bottom, color)
    fill(bounds.x, bounds.y + thickness, bounds.x + thickness, bottom - thickness, color)
    fill(right - thickness, bounds.y + thickness, right, bottom - thickness, color)
}

private fun GuiGraphicsExtractor.drawResizeHandles(bounds: Rect) {
    for (left in listOf(bounds.x - RESIZE_HANDLE_SIZE, bounds.x + bounds.width)) {
        for (top in listOf(bounds.y - RESIZE_HANDLE_SIZE, bounds.y + bounds.height)) {
            fill(left, top, left + RESIZE_HANDLE_SIZE, top + RESIZE_HANDLE_SIZE, RESIZE_HANDLE_COLOR)
        }
    }
}

internal const val HUD_EDITOR_BORDER = 2
private const val PANEL_BACKGROUND = 0x90000000.toInt()
private const val PANEL_HOVER = 0x90F0F0F0.toInt()
private const val RESIZE_HANDLE_SIZE = 4
private const val RESIZE_HANDLE_COLOR = 0xFFF0F0F0.toInt()
private const val GRID_COLOR = 0x2855FFFF
private const val FINE_GRID_COLOR = 0x1055FFFF
private const val SNAP_GUIDE_OUTLINE_WIDTH = 1
private const val SNAP_GUIDE_COLOR = 0xFF55FFFF.toInt()
private const val SNAP_GUIDE_OUTLINE_COLOR = 0xB0000000.toInt()
private const val SNAP_TARGET_OUTLINE_COLOR = 0xD055FFFF.toInt()
