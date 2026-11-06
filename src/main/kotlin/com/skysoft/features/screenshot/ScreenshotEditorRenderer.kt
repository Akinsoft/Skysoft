package com.skysoft.features.screenshot

import com.skysoft.features.screenshot.ScreenshotRenderStyle.drawButton
import com.skysoft.gui.tooltip.SkysoftNativeTooltip
import com.skysoft.utils.ColorUtilities.withScaledAlpha
import com.skysoft.utils.gui.Rect
import com.skysoft.utils.gui.enableScissor
import com.skysoft.utils.gui.fill
import com.skysoft.utils.image.RegisteredImageTexture
import com.skysoft.utils.image.drawLinearTexture
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor

internal object ScreenshotEditorRenderer {
    fun drawToolbar(
        context: GuiGraphicsExtractor,
        font: Font,
        layout: ScreenshotFocusLayout,
        session: ScreenshotEditSession,
        isEnabled: Boolean,
        mouseX: Int,
        mouseY: Int,
        alpha: Double,
    ) {
        fun button(bounds: Rect, label: String, isButtonEnabled: Boolean = isEnabled, isSelected: Boolean = false) =
            drawButton(context, font, bounds, label, isButtonEnabled, mouseX, mouseY, alpha = alpha, isSelected = isSelected)

        layout.toolButtons.forEach { (tool, bounds) -> button(bounds, tool.label, isSelected = session.tool == tool) }
        button(layout.zoomOut, "-")
        ScreenshotRenderStyle.drawCentered(
            context,
            font,
            layout.zoomValue,
            "${(session.zoom * PERCENT_SCALE).roundToInt()}%",
            ScreenshotRenderStyle.MUTED_TEXT.withScaledAlpha(alpha),
        )
        button(layout.zoomIn, "+")
        button(layout.zoomFit, "Fit")
        button(layout.undo, "Undo", isEnabled && session.canUndo)
        button(layout.redo, "Redo", isEnabled && session.canRedo)
        button(layout.reset, "Reset", isEnabled && session.hasEdits)
        when (session.tool) {
            ScreenshotEditorTool.VIEW -> Unit
            ScreenshotEditorTool.CROP ->
                button(layout.resetCrop, "Reset Crop", isEnabled && session.snapshot.crop != ScreenshotCrop.FULL)
            ScreenshotEditorTool.DRAW -> {
                drawInlineLabel(context, font, layout.editorContext.x, layout.editorContext.y, "Color", alpha)
                layout.colorSwatches.forEach { (color, bounds) ->
                    drawSwatch(context, bounds, color, session.color == color, isEnabled, mouseX, mouseY, alpha)
                }
                drawInlineLabel(
                    context,
                    font,
                    layout.brushSizes.values.first().x - ScreenshotLayoutDimensions.BRUSH_LABEL_WIDTH,
                    layout.editorContext.y,
                    "Brush",
                    alpha,
                )
                layout.brushSizes.forEach { (size, bounds) ->
                    button(bounds, size.label, isSelected = session.brushSize == size)
                }
            }
        }
    }

    fun drawCanvas(
        context: GuiGraphicsExtractor,
        font: Font,
        layout: ScreenshotFocusLayout,
        texture: RegisteredImageTexture?,
        didLoadFail: Boolean,
        session: ScreenshotEditSession,
        geometry: ScreenshotEditorGeometry?,
        mouseX: Int,
        mouseY: Int,
    ) {
        context.fill(layout.preview, ScreenshotRenderStyle.BORDER)
        val viewport = layout.editorViewport()
        context.fill(viewport, ScreenshotRenderStyle.IMAGE_BACKGROUND)
        if (texture != null && geometry != null) {
            drawEditableImage(context, texture, session, geometry, mouseX, mouseY)
        } else {
            ScreenshotRenderStyle.drawLoadState(context, font, viewport, didLoadFail)
        }
    }

    private fun drawSwatch(
        context: GuiGraphicsExtractor,
        bounds: Rect,
        color: ScreenshotDrawColor,
        isSelected: Boolean,
        isEnabled: Boolean,
        mouseX: Int,
        mouseY: Int,
        alpha: Double,
    ) {
        val isHovered = isEnabled && bounds.contains(mouseX, mouseY)
        val border = when {
            isSelected -> SELECTED_SWATCH
            isHovered -> HOVERED_SWATCH
            else -> ScreenshotRenderStyle.BORDER
        }
        context.fill(bounds, border.withScaledAlpha(alpha))
        context.fill(bounds.inset(SWATCH_BORDER), color.argb.withScaledAlpha(alpha))
        if (isHovered) SkysoftNativeTooltip.setForNextFrame(context, listOf(color.displayName), mouseX, mouseY)
    }

    private fun drawEditableImage(
        context: GuiGraphicsExtractor,
        texture: RegisteredImageTexture,
        session: ScreenshotEditSession,
        geometry: ScreenshotEditorGeometry,
        mouseX: Int,
        mouseY: Int,
    ) {
        val image = geometry.imageBounds.rounded()
        val clip = image.intersection(geometry.viewport) ?: return
        context.enableScissor(clip)
        try {
            context.drawLinearTexture(
                texture.texture,
                image,
                geometry.sourceCrop.left.toFloat(),
                geometry.sourceCrop.right.toFloat(),
                geometry.sourceCrop.top.toFloat(),
                geometry.sourceCrop.bottom.toFloat(),
            )
            drawStrokes(context, session.snapshot.strokes, geometry)
            if (session.tool == ScreenshotEditorTool.CROP) drawCropOverlay(context, session.snapshot.crop, geometry)
            if (session.tool == ScreenshotEditorTool.DRAW) drawBrushCursor(context, session, geometry, mouseX, mouseY)
        } finally {
            context.disableScissor()
        }
    }

    private fun drawStrokes(
        context: GuiGraphicsExtractor,
        strokes: List<ScreenshotStroke>,
        geometry: ScreenshotEditorGeometry,
    ) {
        strokes.forEach { stroke ->
            val radius = (geometry.screenStrokeWidth(stroke.normalizedWidth) / 2.0).coerceAtLeast(MINIMUM_STROKE_RADIUS)
            val screenPoints = stroke.points.map(geometry::imageToScreen)
            screenPoints.zipWithNext().forEach { (start, end) ->
                drawStrokeSegment(context, start, end, radius, stroke.color.argb)
            }
            screenPoints.firstOrNull()?.let { drawCircle(context, it.x, it.y, radius, stroke.color.argb) }
        }
    }

    private fun drawStrokeSegment(
        context: GuiGraphicsExtractor,
        start: ScreenshotEditPoint,
        end: ScreenshotEditPoint,
        radius: Double,
        color: Int,
    ) {
        val distance = hypot(end.x - start.x, end.y - start.y)
        val steps = ceil(distance / max(1.0, radius)).toInt().coerceAtLeast(1)
        repeat(steps + 1) { index ->
            val progress = index.toDouble() / steps
            drawCircle(
                context,
                start.x + (end.x - start.x) * progress,
                start.y + (end.y - start.y) * progress,
                radius,
                color,
            )
        }
    }

    private fun drawCircle(
        context: GuiGraphicsExtractor,
        centerX: Double,
        centerY: Double,
        radius: Double,
        color: Int,
    ) {
        val integerRadius = ceil(radius).toInt().coerceAtLeast(1)
        for (offsetY in -integerRadius..integerRadius) {
            val width = sqrt(max(0.0, radius * radius - offsetY * offsetY)).roundToInt()
            context.fill(
                centerX.roundToInt() - width,
                centerY.roundToInt() + offsetY,
                centerX.roundToInt() + width + 1,
                centerY.roundToInt() + offsetY + 1,
                color,
            )
        }
    }

    private fun drawCropOverlay(
        context: GuiGraphicsExtractor,
        crop: ScreenshotCrop,
        geometry: ScreenshotEditorGeometry,
    ) {
        val image = geometry.imageBounds.rounded()
        val selection = geometry.cropSelection(crop).rounded()
        val imageRight = image.x + image.width
        val selectionRight = selection.x + selection.width
        val selectionBottom = selection.y + selection.height
        context.fill(image.x, image.y, imageRight, selection.y, CROP_SHIELD)
        context.fill(image.x, selection.y, selection.x, selectionBottom, CROP_SHIELD)
        context.fill(selectionRight, selection.y, imageRight, selectionBottom, CROP_SHIELD)
        context.fill(image.x, selectionBottom, imageRight, image.y + image.height, CROP_SHIELD)
        context.outline(selection.x, selection.y, selection.width, selection.height, CROP_BORDER)
        geometry.cropHandleBounds(crop).values.forEach { bounds ->
            val visual = Rect(
                bounds.x + (bounds.width - CROP_HANDLE_SIZE) / 2,
                bounds.y + (bounds.height - CROP_HANDLE_SIZE) / 2,
                CROP_HANDLE_SIZE,
                CROP_HANDLE_SIZE,
            )
            context.fill(visual, CROP_HANDLE_BORDER)
            context.fill(visual.inset(1), CROP_HANDLE_FILL)
        }
    }

    private fun drawBrushCursor(
        context: GuiGraphicsExtractor,
        session: ScreenshotEditSession,
        geometry: ScreenshotEditorGeometry,
        mouseX: Int,
        mouseY: Int,
    ) {
        val point = geometry.screenToImage(mouseX.toDouble(), mouseY.toDouble()) ?: return
        if (!session.snapshot.crop.contains(point)) return
        val diameter = geometry.screenStrokeWidth(session.brushSize.normalizedWidth).roundToInt()
            .coerceAtLeast(MINIMUM_BRUSH_CURSOR_SIZE)
        context.outline(mouseX - diameter / 2, mouseY - diameter / 2, diameter, diameter, BRUSH_CURSOR)
    }

    private fun drawInlineLabel(
        context: GuiGraphicsExtractor,
        font: Font,
        x: Int,
        y: Int,
        label: String,
        alpha: Double,
    ) {
        context.text(
            font,
            label,
            x,
            y + (ScreenshotLayoutDimensions.ACTION_HEIGHT - font.lineHeight) / 2,
            ScreenshotRenderStyle.MUTED_TEXT.withScaledAlpha(alpha),
            false,
        )
    }

    private const val CROP_HANDLE_SIZE = 7
    private const val SWATCH_BORDER = 2
    private val HOVERED_SWATCH = 0xFF8D99A1.toInt()
    private val SELECTED_SWATCH = 0xFF58B8EA.toInt()
    private val CROP_SHIELD = 0xA0000000.toInt()
    private val CROP_BORDER = 0xFF58B8EA.toInt()
    private val CROP_HANDLE_BORDER = 0xFF111315.toInt()
    private val CROP_HANDLE_FILL = 0xFFFFFFFF.toInt()
    private val BRUSH_CURSOR = 0xFFFFFFFF.toInt()
}

private const val MINIMUM_STROKE_RADIUS = 0.5
private const val MINIMUM_BRUSH_CURSOR_SIZE = 3
private const val PERCENT_SCALE = 100

internal fun ScreenshotFocusLayout.editorViewport(): Rect = Rect(
    preview.x + 1,
    preview.y + 1,
    (preview.width - 2).coerceAtLeast(1),
    (preview.height - 2).coerceAtLeast(1),
)
