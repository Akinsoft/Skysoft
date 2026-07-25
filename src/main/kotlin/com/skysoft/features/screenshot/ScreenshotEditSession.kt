package com.skysoft.features.screenshot

import java.util.ArrayDeque
import kotlin.math.hypot

internal enum class ScreenshotEditorTool(val label: String) {
    VIEW("View"),
    CROP("Crop"),
    DRAW("Draw"),
}

internal enum class ScreenshotDrawColor(val displayName: String, val argb: Int) {
    WHITE("White", 0xFFFFFFFF.toInt()),
    BLACK("Black", 0xFF111111.toInt()),
    RED("Red", 0xFFFF4D57.toInt()),
    ORANGE("Orange", 0xFFFF963D.toInt()),
    YELLOW("Yellow", 0xFFFFD84A.toInt()),
    GREEN("Green", 0xFF55D879.toInt()),
    BLUE("Blue", 0xFF4AA8FF.toInt()),
    PURPLE("Purple", 0xFFB778FF.toInt()),
}

internal enum class ScreenshotBrushSize(
    val label: String,
    val normalizedWidth: Double,
) {
    SMALL("S", 0.003),
    MEDIUM("M", 0.006),
    LARGE("L", 0.012),
}

internal data class ScreenshotEditPoint(
    val x: Double,
    val y: Double,
) {
    fun clamped(): ScreenshotEditPoint = ScreenshotEditPoint(
        x.coerceIn(0.0, 1.0),
        y.coerceIn(0.0, 1.0),
    )
}

internal data class ScreenshotCrop(
    val left: Double,
    val top: Double,
    val right: Double,
    val bottom: Double,
) {
    val width: Double get() = right - left
    val height: Double get() = bottom - top

    fun contains(point: ScreenshotEditPoint): Boolean =
        point.x in left..right && point.y in top..bottom

    companion object {
        val FULL = ScreenshotCrop(0.0, 0.0, 1.0, 1.0)
        const val MINIMUM_SIZE = 0.02
    }
}

internal data class ScreenshotStroke(
    val color: ScreenshotDrawColor,
    val normalizedWidth: Double,
    val points: List<ScreenshotEditPoint>,
)

internal data class ScreenshotEditSnapshot(
    val crop: ScreenshotCrop = ScreenshotCrop.FULL,
    val strokes: List<ScreenshotStroke> = emptyList(),
) {
    val hasEdits: Boolean get() = crop != ScreenshotCrop.FULL || strokes.isNotEmpty()
}

internal class ScreenshotEditSession {
    var tool = ScreenshotEditorTool.VIEW
        private set
    var color = ScreenshotDrawColor.RED
        private set
    var brushSize = ScreenshotBrushSize.MEDIUM
        private set
    var zoom = DEFAULT_ZOOM
        private set
    var panX = 0.0
        private set
    var panY = 0.0
        private set

    private var crop = ScreenshotCrop.FULL
    private val strokes = mutableListOf<ScreenshotStroke>()
    private val undo = ArrayDeque<ScreenshotEditSnapshot>()
    private val redo = ArrayDeque<ScreenshotEditSnapshot>()

    val snapshot: ScreenshotEditSnapshot
        get() = ScreenshotEditSnapshot(crop, strokes.toList())
    val hasEdits: Boolean get() = snapshot.hasEdits
    val canUndo: Boolean get() = undo.isNotEmpty()
    val canRedo: Boolean get() = redo.isNotEmpty()

    fun selectTool(tool: ScreenshotEditorTool) {
        if (this.tool == tool) return
        this.tool = tool
        fit()
    }

    fun selectColor(color: ScreenshotDrawColor) {
        this.color = color
    }

    fun selectBrushSize(size: ScreenshotBrushSize) {
        brushSize = size
    }

    fun zoomBy(steps: Double) {
        zoom = (zoom * Math.pow(ZOOM_STEP, steps)).coerceIn(MINIMUM_ZOOM, MAXIMUM_ZOOM)
    }

    fun setPan(x: Double, y: Double) {
        panX = x
        panY = y
    }

    fun panBy(x: Double, y: Double) {
        panX += x
        panY += y
    }

    fun fit() {
        zoom = DEFAULT_ZOOM
        panX = 0.0
        panY = 0.0
    }

    fun beginEdit(): ScreenshotEditSnapshot = snapshot

    fun updateCrop(crop: ScreenshotCrop) {
        this.crop = crop
    }

    fun startStroke(point: ScreenshotEditPoint) {
        strokes.add(ScreenshotStroke(color, brushSize.normalizedWidth, listOf(point)))
    }

    fun extendStroke(point: ScreenshotEditPoint) {
        val stroke = strokes.lastOrNull() ?: return
        val previous = stroke.points.lastOrNull()
        if (previous != null && hypot(point.x - previous.x, point.y - previous.y) < MINIMUM_POINT_DISTANCE) return
        strokes[strokes.lastIndex] = stroke.copy(points = stroke.points + point)
    }

    fun commitEdit(before: ScreenshotEditSnapshot) {
        if (snapshot == before) return
        undo.addLast(before)
        undo.trimToLimit()
        redo.clear()
    }

    fun undo() {
        val previous = undo.pollLast() ?: return
        redo.addLast(snapshot)
        redo.trimToLimit()
        restore(previous)
    }

    fun redo() {
        val next = redo.pollLast() ?: return
        undo.addLast(snapshot)
        undo.trimToLimit()
        restore(next)
    }

    fun reset() {
        val before = snapshot
        crop = ScreenshotCrop.FULL
        strokes.clear()
        commitEdit(before)
        fit()
    }

    fun resetCrop() {
        if (crop == ScreenshotCrop.FULL) return
        val before = snapshot
        crop = ScreenshotCrop.FULL
        commitEdit(before)
        fit()
    }

    private fun restore(snapshot: ScreenshotEditSnapshot) {
        crop = snapshot.crop
        strokes.clear()
        strokes.addAll(snapshot.strokes)
        fit()
    }

    private fun <T> ArrayDeque<T>.trimToLimit() {
        while (size > MAXIMUM_HISTORY_STEPS) removeFirst()
    }

    private companion object {
        const val DEFAULT_ZOOM = 1.0
        const val MINIMUM_ZOOM = 0.25
        const val MAXIMUM_ZOOM = 8.0
        const val ZOOM_STEP = 1.2
        const val MINIMUM_POINT_DISTANCE = 0.0005
        const val MAXIMUM_HISTORY_STEPS = 32
    }
}
