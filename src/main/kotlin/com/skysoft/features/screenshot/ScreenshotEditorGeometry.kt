package com.skysoft.features.screenshot

import com.skysoft.utils.gui.Rect
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

internal data class ScreenshotEditorRect(
    val x: Double,
    val y: Double,
    val width: Double,
    val height: Double,
) {
    val right: Double get() = x + width
    val bottom: Double get() = y + height

    fun contains(mouseX: Double, mouseY: Double): Boolean =
        mouseX >= x && mouseX < right && mouseY >= y && mouseY < bottom

    fun rounded(): Rect = Rect(
        x.roundToInt(),
        y.roundToInt(),
        width.roundToInt().coerceAtLeast(1),
        height.roundToInt().coerceAtLeast(1),
    )
}

internal enum class ScreenshotCropHandle {
    TOP_LEFT,
    TOP,
    TOP_RIGHT,
    RIGHT,
    BOTTOM_RIGHT,
    BOTTOM,
    BOTTOM_LEFT,
    LEFT,
    MOVE,
}

internal data class ScreenshotEditorGeometry(
    val viewport: Rect,
    val sourceCrop: ScreenshotCrop,
    val imageBounds: ScreenshotEditorRect,
    val imageWidth: Int,
    val imageHeight: Int,
    val effectivePanX: Double,
    val effectivePanY: Double,
) {
    val displayScale: Double
        get() = imageBounds.width / (imageWidth * sourceCrop.width)

    fun screenToImage(mouseX: Double, mouseY: Double, clamp: Boolean = false): ScreenshotEditPoint? {
        if (!clamp && !imageBounds.contains(mouseX, mouseY)) return null
        val xRatio = ((mouseX - imageBounds.x) / imageBounds.width).coerceIn(0.0, 1.0)
        val yRatio = ((mouseY - imageBounds.y) / imageBounds.height).coerceIn(0.0, 1.0)
        return ScreenshotEditPoint(
            sourceCrop.left + xRatio * sourceCrop.width,
            sourceCrop.top + yRatio * sourceCrop.height,
        )
    }

    fun imageToScreen(point: ScreenshotEditPoint): ScreenshotEditPoint = ScreenshotEditPoint(
        imageBounds.x + (point.x - sourceCrop.left) / sourceCrop.width * imageBounds.width,
        imageBounds.y + (point.y - sourceCrop.top) / sourceCrop.height * imageBounds.height,
    )

    fun cropSelection(crop: ScreenshotCrop): ScreenshotEditorRect {
        val topLeft = imageToScreen(ScreenshotEditPoint(crop.left, crop.top))
        val bottomRight = imageToScreen(ScreenshotEditPoint(crop.right, crop.bottom))
        return ScreenshotEditorRect(
            topLeft.x,
            topLeft.y,
            bottomRight.x - topLeft.x,
            bottomRight.y - topLeft.y,
        )
    }

    fun cropHandleBounds(crop: ScreenshotCrop): Map<ScreenshotCropHandle, Rect> {
        val selection = cropSelection(crop)
        val centerX = selection.x + selection.width / 2.0
        val centerY = selection.y + selection.height / 2.0
        return linkedMapOf(
            ScreenshotCropHandle.TOP_LEFT to handleAt(selection.x, selection.y),
            ScreenshotCropHandle.TOP to handleAt(centerX, selection.y),
            ScreenshotCropHandle.TOP_RIGHT to handleAt(selection.right, selection.y),
            ScreenshotCropHandle.RIGHT to handleAt(selection.right, centerY),
            ScreenshotCropHandle.BOTTOM_RIGHT to handleAt(selection.right, selection.bottom),
            ScreenshotCropHandle.BOTTOM to handleAt(centerX, selection.bottom),
            ScreenshotCropHandle.BOTTOM_LEFT to handleAt(selection.x, selection.bottom),
            ScreenshotCropHandle.LEFT to handleAt(selection.x, centerY),
        )
    }

    fun cropHandleAt(crop: ScreenshotCrop, mouseX: Int, mouseY: Int): ScreenshotCropHandle? {
        val handle = cropHandleBounds(crop).entries.firstOrNull { it.value.contains(mouseX, mouseY) }?.key
        if (handle != null) return handle
        return ScreenshotCropHandle.MOVE.takeIf { cropSelection(crop).contains(mouseX.toDouble(), mouseY.toDouble()) }
    }

    companion object {
        fun create(
            viewport: Rect,
            imageWidth: Int,
            imageHeight: Int,
            session: ScreenshotEditSession,
        ): ScreenshotEditorGeometry {
            val sourceCrop = if (session.tool == ScreenshotEditorTool.CROP) ScreenshotCrop.FULL else session.snapshot.crop
            val sourceWidth = imageWidth * sourceCrop.width
            val sourceHeight = imageHeight * sourceCrop.height
            val fitScale = min(viewport.width / sourceWidth, viewport.height / sourceHeight)
            val width = max(1.0, sourceWidth * fitScale * session.zoom)
            val height = max(1.0, sourceHeight * fitScale * session.zoom)
            val panX = clampPan(session.panX, width, viewport.width.toDouble())
            val panY = clampPan(session.panY, height, viewport.height.toDouble())
            return ScreenshotEditorGeometry(
                viewport = viewport,
                sourceCrop = sourceCrop,
                imageBounds = ScreenshotEditorRect(
                    viewport.x + (viewport.width - width) / 2.0 + panX,
                    viewport.y + (viewport.height - height) / 2.0 + panY,
                    width,
                    height,
                ),
                imageWidth = imageWidth,
                imageHeight = imageHeight,
                effectivePanX = panX,
                effectivePanY = panY,
            )
        }

        private fun clampPan(pan: Double, contentSize: Double, viewportSize: Double): Double {
            if (contentSize <= viewportSize) return 0.0
            val maximum = (contentSize - viewportSize) / 2.0
            return pan.coerceIn(-maximum, maximum)
        }

        private fun handleAt(x: Double, y: Double): Rect = Rect(
            (x - HANDLE_HIT_SIZE / 2.0).roundToInt(),
            (y - HANDLE_HIT_SIZE / 2.0).roundToInt(),
            HANDLE_HIT_SIZE,
            HANDLE_HIT_SIZE,
        )

        private const val HANDLE_HIT_SIZE = 12
    }
}

internal fun resizeScreenshotCrop(
    initial: ScreenshotCrop,
    handle: ScreenshotCropHandle,
    point: ScreenshotEditPoint,
): ScreenshotCrop {
    val normalized = point.clamped()
    var left = initial.left
    var top = initial.top
    var right = initial.right
    var bottom = initial.bottom
    if (handle in LEFT_CROP_HANDLES) left = min(normalized.x, right - ScreenshotCrop.MINIMUM_SIZE)
    if (handle in RIGHT_CROP_HANDLES) right = max(normalized.x, left + ScreenshotCrop.MINIMUM_SIZE)
    if (handle in TOP_CROP_HANDLES) top = min(normalized.y, bottom - ScreenshotCrop.MINIMUM_SIZE)
    if (handle in BOTTOM_CROP_HANDLES) bottom = max(normalized.y, top + ScreenshotCrop.MINIMUM_SIZE)
    return ScreenshotCrop(left, top, right, bottom)
}

internal fun moveScreenshotCrop(
    initial: ScreenshotCrop,
    start: ScreenshotEditPoint,
    current: ScreenshotEditPoint,
): ScreenshotCrop {
    val requestedX = current.x - start.x
    val requestedY = current.y - start.y
    val deltaX = requestedX.coerceIn(-initial.left, 1.0 - initial.right)
    val deltaY = requestedY.coerceIn(-initial.top, 1.0 - initial.bottom)
    return ScreenshotCrop(
        initial.left + deltaX,
        initial.top + deltaY,
        initial.right + deltaX,
        initial.bottom + deltaY,
    )
}

private val LEFT_CROP_HANDLES = setOf(
    ScreenshotCropHandle.TOP_LEFT,
    ScreenshotCropHandle.LEFT,
    ScreenshotCropHandle.BOTTOM_LEFT,
)
private val RIGHT_CROP_HANDLES = setOf(
    ScreenshotCropHandle.TOP_RIGHT,
    ScreenshotCropHandle.RIGHT,
    ScreenshotCropHandle.BOTTOM_RIGHT,
)
private val TOP_CROP_HANDLES = setOf(
    ScreenshotCropHandle.TOP_LEFT,
    ScreenshotCropHandle.TOP,
    ScreenshotCropHandle.TOP_RIGHT,
)
private val BOTTOM_CROP_HANDLES = setOf(
    ScreenshotCropHandle.BOTTOM_LEFT,
    ScreenshotCropHandle.BOTTOM,
    ScreenshotCropHandle.BOTTOM_RIGHT,
)
