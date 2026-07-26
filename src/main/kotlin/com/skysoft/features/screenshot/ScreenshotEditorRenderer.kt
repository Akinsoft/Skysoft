package com.skysoft.features.screenshot

import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.textures.FilterMode
import com.skysoft.gui.tooltip.SkysoftNativeTooltip
import com.skysoft.utils.ColorUtilities.withScaledAlpha
import com.skysoft.utils.gui.PixelButtonRenderer
import com.skysoft.utils.gui.Rect
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
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
        layout.toolButtons.forEach { (tool, bounds) ->
            drawButton(context, font, bounds, tool.label, session.tool == tool, isEnabled, mouseX, mouseY, alpha)
        }
        drawButton(context, font, layout.zoomOut, "-", false, isEnabled, mouseX, mouseY, alpha)
        drawZoomValue(context, font, layout.zoomValue, session.zoom, alpha)
        drawButton(context, font, layout.zoomIn, "+", false, isEnabled, mouseX, mouseY, alpha)
        drawButton(context, font, layout.zoomFit, "Fit", false, isEnabled, mouseX, mouseY, alpha)
        drawButton(context, font, layout.undo, "Undo", false, isEnabled && session.canUndo, mouseX, mouseY, alpha)
        drawButton(context, font, layout.redo, "Redo", false, isEnabled && session.canRedo, mouseX, mouseY, alpha)
        drawButton(context, font, layout.reset, "Reset", false, isEnabled && session.hasEdits, mouseX, mouseY, alpha)
        drawContextControls(context, font, layout, session, isEnabled, mouseX, mouseY, alpha)
    }

    fun drawCanvas(
        context: GuiGraphicsExtractor,
        font: Font,
        layout: ScreenshotFocusLayout,
        texture: ScreenshotTexture?,
        didLoadFail: Boolean,
        session: ScreenshotEditSession,
        geometry: ScreenshotEditorGeometry?,
        mouseX: Int,
        mouseY: Int,
    ) {
        context.fill(
            layout.preview.x,
            layout.preview.y,
            layout.preview.x + layout.preview.width,
            layout.preview.y + layout.preview.height,
            ScreenshotRenderStyle.BORDER,
        )
        val viewport = layout.editorViewport()
        context.fill(
            viewport.x,
            viewport.y,
            viewport.x + viewport.width,
            viewport.y + viewport.height,
            ScreenshotRenderStyle.IMAGE_BACKGROUND,
        )
        when {
            texture != null && geometry != null -> drawEditableImage(context, texture, session, geometry, mouseX, mouseY)
            didLoadFail -> ScreenshotRenderStyle.drawCentered(
                context,
                font,
                viewport,
                "Couldn't load screenshot.",
                ScreenshotRenderStyle.ERROR_TEXT,
            )
            else -> ScreenshotRenderStyle.drawCentered(
                context,
                font,
                viewport,
                "Loading...",
                ScreenshotRenderStyle.MUTED_TEXT,
            )
        }
    }

    private fun drawContextControls(
        context: GuiGraphicsExtractor,
        font: Font,
        layout: ScreenshotFocusLayout,
        session: ScreenshotEditSession,
        isEnabled: Boolean,
        mouseX: Int,
        mouseY: Int,
        alpha: Double,
    ) {
        when (session.tool) {
            ScreenshotEditorTool.VIEW -> Unit
            ScreenshotEditorTool.CROP -> {
                drawButton(
                    context,
                    font,
                    layout.resetCrop,
                    "Reset Crop",
                    false,
                    isEnabled && session.snapshot.crop != ScreenshotCrop.FULL,
                    mouseX,
                    mouseY,
                    alpha,
                )
            }
            ScreenshotEditorTool.DRAW -> drawDrawingControls(
                context,
                font,
                layout,
                session,
                isEnabled,
                mouseX,
                mouseY,
                alpha,
            )
        }
    }

    private fun drawDrawingControls(
        context: GuiGraphicsExtractor,
        font: Font,
        layout: ScreenshotFocusLayout,
        session: ScreenshotEditSession,
        isEnabled: Boolean,
        mouseX: Int,
        mouseY: Int,
        alpha: Double,
    ) {
        drawInlineLabel(context, font, layout.editorContext.x, layout.editorContext.y, "Color", alpha)
        layout.colorSwatches.forEach { (color, bounds) ->
            drawSwatch(context, bounds, color, session.color == color, isEnabled, mouseX, mouseY, alpha)
        }
        val firstBrush = layout.brushSizes.values.first()
        drawInlineLabel(
            context,
            font,
            firstBrush.x - ScreenshotLayoutDimensions.BRUSH_LABEL_WIDTH,
            layout.editorContext.y,
            "Brush",
            alpha,
        )
        layout.brushSizes.forEach { (size, bounds) ->
            drawButton(
                context,
                font,
                bounds,
                size.label,
                session.brushSize == size,
                isEnabled,
                mouseX,
                mouseY,
                alpha,
            )
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
        context.fill(
            bounds.x,
            bounds.y,
            bounds.x + bounds.width,
            bounds.y + bounds.height,
            border.withScaledAlpha(alpha),
        )
        context.fill(
            bounds.x + 2,
            bounds.y + 2,
            bounds.x + bounds.width - 2,
            bounds.y + bounds.height - 2,
            color.argb.withScaledAlpha(alpha),
        )
        if (isHovered) SkysoftNativeTooltip.setForNextFrame(context, listOf(color.displayName), mouseX, mouseY)
    }

    private fun drawEditableImage(
        context: GuiGraphicsExtractor,
        texture: ScreenshotTexture,
        session: ScreenshotEditSession,
        geometry: ScreenshotEditorGeometry,
        mouseX: Int,
        mouseY: Int,
    ) {
        val clip = clippedImageBounds(geometry) ?: return
        context.enableScissor(clip.x, clip.y, clip.x + clip.width, clip.y + clip.height)
        try {
            drawTexture(context, texture, geometry)
            drawStrokes(context, session.snapshot.strokes, geometry)
            if (session.tool == ScreenshotEditorTool.CROP) drawCropOverlay(context, session.snapshot.crop, geometry)
            if (session.tool == ScreenshotEditorTool.DRAW) drawBrushCursor(context, session, geometry, mouseX, mouseY)
        } finally {
            context.disableScissor()
        }
    }

    private fun drawTexture(
        context: GuiGraphicsExtractor,
        texture: ScreenshotTexture,
        geometry: ScreenshotEditorGeometry,
    ) {
        val bounds = geometry.imageBounds.rounded()
        context.blit(
            texture.texture.textureView,
            RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR),
            bounds.x,
            bounds.y,
            bounds.x + bounds.width,
            bounds.y + bounds.height,
            geometry.sourceCrop.left.toFloat(),
            geometry.sourceCrop.right.toFloat(),
            geometry.sourceCrop.top.toFloat(),
            geometry.sourceCrop.bottom.toFloat(),
        )
    }

    private fun drawStrokes(
        context: GuiGraphicsExtractor,
        strokes: List<ScreenshotStroke>,
        geometry: ScreenshotEditorGeometry,
    ) {
        strokes.forEach { stroke ->
            val radius = (
                stroke.normalizedWidth * min(geometry.imageWidth, geometry.imageHeight) *
                    geometry.displayScale / 2.0
                ).coerceAtLeast(MINIMUM_STROKE_RADIUS)
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
        context.fill(image.x, image.y, image.x + image.width, selection.y, CROP_SHIELD)
        context.fill(image.x, selection.y, selection.x, selection.y + selection.height, CROP_SHIELD)
        context.fill(
            selection.x + selection.width,
            selection.y,
            image.x + image.width,
            selection.y + selection.height,
            CROP_SHIELD,
        )
        context.fill(
            image.x,
            selection.y + selection.height,
            image.x + image.width,
            image.y + image.height,
            CROP_SHIELD,
        )
        drawOutline(context, selection, CROP_BORDER)
        geometry.cropHandleBounds(crop).values.forEach { bounds ->
            val visual = Rect(
                bounds.x + (bounds.width - CROP_HANDLE_SIZE) / 2,
                bounds.y + (bounds.height - CROP_HANDLE_SIZE) / 2,
                CROP_HANDLE_SIZE,
                CROP_HANDLE_SIZE,
            )
            context.fill(visual.x, visual.y, visual.x + visual.width, visual.y + visual.height, CROP_HANDLE_BORDER)
            context.fill(
                visual.x + 1,
                visual.y + 1,
                visual.x + visual.width - 1,
                visual.y + visual.height - 1,
                CROP_HANDLE_FILL,
            )
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
        val diameter = (
            session.brushSize.normalizedWidth * min(geometry.imageWidth, geometry.imageHeight) *
                geometry.displayScale
            ).roundToInt().coerceAtLeast(MINIMUM_BRUSH_CURSOR_SIZE)
        drawOutline(
            context,
            Rect(mouseX - diameter / 2, mouseY - diameter / 2, diameter, diameter),
            BRUSH_CURSOR,
        )
    }

    private fun drawZoomValue(
        context: GuiGraphicsExtractor,
        font: Font,
        bounds: Rect,
        zoom: Double,
        alpha: Double,
    ) {
        val label = "${(zoom * PERCENT_SCALE).roundToInt()}%"
        context.text(
            font,
            label,
            bounds.x + (bounds.width - font.width(label)) / 2,
            bounds.y + (bounds.height - font.lineHeight) / 2,
            ScreenshotRenderStyle.MUTED_TEXT.withScaledAlpha(alpha),
            false,
        )
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

    private fun drawButton(
        context: GuiGraphicsExtractor,
        font: Font,
        bounds: Rect,
        label: String,
        isSelected: Boolean,
        isEnabled: Boolean,
        mouseX: Int,
        mouseY: Int,
        alpha: Double,
    ) {
        PixelButtonRenderer.draw(
            context,
            font,
            bounds,
            label,
            isSelected,
            isEnabled && bounds.contains(mouseX, mouseY),
            isEnabled,
            alpha = alpha,
        )
    }

    private fun clippedImageBounds(geometry: ScreenshotEditorGeometry): Rect? {
        val image = geometry.imageBounds.rounded()
        val left = max(image.x, geometry.viewport.x)
        val top = max(image.y, geometry.viewport.y)
        val right = min(image.x + image.width, geometry.viewport.x + geometry.viewport.width)
        val bottom = min(image.y + image.height, geometry.viewport.y + geometry.viewport.height)
        if (right <= left || bottom <= top) return null
        return Rect(left, top, right - left, bottom - top)
    }

    private fun drawOutline(context: GuiGraphicsExtractor, bounds: Rect, color: Int) {
        context.fill(bounds.x, bounds.y, bounds.x + bounds.width, bounds.y + 1, color)
        context.fill(bounds.x, bounds.y + bounds.height - 1, bounds.x + bounds.width, bounds.y + bounds.height, color)
        context.fill(bounds.x, bounds.y, bounds.x + 1, bounds.y + bounds.height, color)
        context.fill(bounds.x + bounds.width - 1, bounds.y, bounds.x + bounds.width, bounds.y + bounds.height, color)
    }

    private const val CROP_HANDLE_SIZE = 7
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
