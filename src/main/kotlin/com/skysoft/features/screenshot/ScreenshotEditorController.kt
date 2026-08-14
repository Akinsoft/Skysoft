package com.skysoft.features.screenshot

import com.skysoft.utils.image.RegisteredImageTexture
import com.skysoft.utils.input.InputHandlingResult
import java.nio.file.Path

internal data class ScreenshotEditorPresentation(
    val session: ScreenshotEditSession,
    val geometry: ScreenshotEditorGeometry?,
)

internal class ScreenshotEditorController {
    private val sessions = mutableMapOf<Path, ScreenshotEditSession>()
    private var geometry: ScreenshotEditorGeometry? = null
    private var drag: ScreenshotEditorDrag? = null

    fun session(path: Path): ScreenshotEditSession =
        sessions.getOrPut(path) { ScreenshotEditSession() }

    fun prepare(
        path: Path,
        viewport: com.skysoft.utils.gui.Rect,
        texture: RegisteredImageTexture?,
    ): ScreenshotEditorPresentation {
        val session = session(path)
        val geometry = texture?.let {
            ScreenshotEditorGeometry.create(viewport, it.width, it.height, session)
        }
        geometry?.let { session.setPan(it.effectivePanX, it.effectivePanY) }
        this.geometry = geometry
        return ScreenshotEditorPresentation(session, geometry)
    }

    fun clearPresentation() {
        geometry = null
        drag = null
    }

    fun remove(path: Path) {
        sessions.remove(path)
        clearPresentation()
    }

    fun clear() {
        sessions.clear()
        clearPresentation()
    }

    fun firstUnsavedPath(): Path? = sessions.entries.firstOrNull { it.value.hasEdits }?.key

    fun processClick(
        layout: ScreenshotFocusLayout,
        path: Path,
        mouseX: Int,
        mouseY: Int,
    ): InputHandlingResult = when {
        didProcessToolbarClick(layout, path, mouseX, mouseY) -> InputHandlingResult.CONSUMED
        didProcessContextClick(layout, path, mouseX, mouseY) -> InputHandlingResult.CONSUMED
        didBeginCanvasInteraction(layout, path, mouseX, mouseY) -> InputHandlingResult.CONSUMED
        else -> InputHandlingResult.IGNORED
    }

    fun processDrag(path: Path, mouseX: Double, mouseY: Double): InputHandlingResult {
        val drag = drag ?: return InputHandlingResult.IGNORED
        val session = session(path)
        val geometry = geometry ?: return InputHandlingResult.IGNORED
        when (drag) {
            is ScreenshotEditorDrag.Pan -> {
                session.panBy(mouseX - drag.lastX, mouseY - drag.lastY)
                drag.lastX = mouseX
                drag.lastY = mouseY
            }
            is ScreenshotEditorDrag.Crop -> {
                val point = geometry.screenToImage(mouseX, mouseY, clamp = true)
                    ?: return InputHandlingResult.IGNORED
                val crop = if (drag.handle == ScreenshotCropHandle.MOVE) {
                    moveScreenshotCrop(drag.initial, drag.start, point)
                } else {
                    resizeScreenshotCrop(drag.initial, drag.handle, point)
                }
                session.updateCrop(crop)
            }
            is ScreenshotEditorDrag.Draw -> {
                geometry.screenToImage(mouseX, mouseY, clamp = true)?.let(session::extendStroke)
            }
        }
        return InputHandlingResult.CONSUMED
    }

    fun processRelease(path: Path): InputHandlingResult {
        val drag = drag ?: return InputHandlingResult.IGNORED
        this.drag = null
        drag.before?.let { session(path).commitEdit(it) }
        return InputHandlingResult.CONSUMED
    }

    fun processScroll(path: Path, mouseX: Double, mouseY: Double, steps: Double): InputHandlingResult {
        val session = session(path)
        val current = geometry ?: return InputHandlingResult.IGNORED
        val anchorX = ((mouseX - current.imageBounds.x) / current.imageBounds.width).coerceIn(0.0, 1.0)
        val anchorY = ((mouseY - current.imageBounds.y) / current.imageBounds.height).coerceIn(0.0, 1.0)
        session.zoomBy(steps)
        val resized = ScreenshotEditorGeometry.create(
            current.viewport,
            current.imageWidth,
            current.imageHeight,
            session,
        )
        val centeredX = current.viewport.x + (current.viewport.width - resized.imageBounds.width) / 2.0
        val centeredY = current.viewport.y + (current.viewport.height - resized.imageBounds.height) / 2.0
        session.setPan(
            mouseX - anchorX * resized.imageBounds.width - centeredX,
            mouseY - anchorY * resized.imageBounds.height - centeredY,
        )
        val clamped = ScreenshotEditorGeometry.create(
            current.viewport,
            current.imageWidth,
            current.imageHeight,
            session,
        )
        session.setPan(clamped.effectivePanX, clamped.effectivePanY)
        geometry = clamped
        return InputHandlingResult.CONSUMED
    }

    private fun didProcessToolbarClick(
        layout: ScreenshotFocusLayout,
        path: Path,
        mouseX: Int,
        mouseY: Int,
    ): Boolean {
        val session = session(path)
        val selectedTool = layout.toolButtons.entries.firstOrNull { it.value.contains(mouseX, mouseY) }?.key
        return when {
            selectedTool != null -> {
                session.selectTool(selectedTool)
                drag = null
                true
            }
            layout.zoomOut.contains(mouseX, mouseY) -> {
                zoomFromToolbar(layout, path, -1.0)
                true
            }
            layout.zoomIn.contains(mouseX, mouseY) -> {
                zoomFromToolbar(layout, path, 1.0)
                true
            }
            layout.zoomFit.contains(mouseX, mouseY) -> {
                session.fit()
                true
            }
            layout.undo.contains(mouseX, mouseY) -> {
                session.undo()
                true
            }
            layout.redo.contains(mouseX, mouseY) -> {
                session.redo()
                true
            }
            layout.reset.contains(mouseX, mouseY) -> {
                session.reset()
                true
            }
            else -> false
        }
    }

    private fun didProcessContextClick(
        layout: ScreenshotFocusLayout,
        path: Path,
        mouseX: Int,
        mouseY: Int,
    ): Boolean {
        val session = session(path)
        return when (session.tool) {
            ScreenshotEditorTool.VIEW -> false
            ScreenshotEditorTool.CROP -> layout.resetCrop.contains(mouseX, mouseY).also {
                if (it) session.resetCrop()
            }
            ScreenshotEditorTool.DRAW -> {
                val color = layout.colorSwatches.entries.firstOrNull { it.value.contains(mouseX, mouseY) }?.key
                val brush = layout.brushSizes.entries.firstOrNull { it.value.contains(mouseX, mouseY) }?.key
                when {
                    color != null -> {
                        session.selectColor(color)
                        true
                    }
                    brush != null -> {
                        session.selectBrushSize(brush)
                        true
                    }
                    else -> false
                }
            }
        }
    }

    private fun didBeginCanvasInteraction(
        layout: ScreenshotFocusLayout,
        path: Path,
        mouseX: Int,
        mouseY: Int,
    ): Boolean {
        if (!layout.editorViewport().contains(mouseX, mouseY)) return false
        val session = session(path)
        val geometry = geometry ?: return false
        drag = when (session.tool) {
            ScreenshotEditorTool.VIEW -> {
                ScreenshotEditorDrag.Pan(mouseX.toDouble(), mouseY.toDouble()).takeIf {
                    geometry.imageBounds.contains(mouseX.toDouble(), mouseY.toDouble())
                }
            }
            ScreenshotEditorTool.CROP -> beginCropDrag(session, geometry, mouseX, mouseY)
            ScreenshotEditorTool.DRAW -> beginDrawDrag(session, geometry, mouseX, mouseY)
        }
        return drag != null
    }

    private fun beginCropDrag(
        session: ScreenshotEditSession,
        geometry: ScreenshotEditorGeometry,
        mouseX: Int,
        mouseY: Int,
    ): ScreenshotEditorDrag.Crop? {
        val initial = session.snapshot.crop
        val handle = geometry.cropHandleAt(initial, mouseX, mouseY) ?: return null
        val start = geometry.screenToImage(mouseX.toDouble(), mouseY.toDouble(), clamp = true) ?: return null
        return ScreenshotEditorDrag.Crop(handle, session.beginEdit(), initial, start)
    }

    private fun beginDrawDrag(
        session: ScreenshotEditSession,
        geometry: ScreenshotEditorGeometry,
        mouseX: Int,
        mouseY: Int,
    ): ScreenshotEditorDrag.Draw? {
        val point = geometry.screenToImage(mouseX.toDouble(), mouseY.toDouble()) ?: return null
        if (!session.snapshot.crop.contains(point)) return null
        val before = session.beginEdit()
        session.startStroke(point)
        return ScreenshotEditorDrag.Draw(before)
    }

    private fun zoomFromToolbar(layout: ScreenshotFocusLayout, path: Path, steps: Double) {
        val viewport = layout.editorViewport()
        processScroll(
            path,
            viewport.x + viewport.width / 2.0,
            viewport.y + viewport.height / 2.0,
            steps,
        )
    }

    private sealed interface ScreenshotEditorDrag {
        val before: ScreenshotEditSnapshot?

        data class Pan(
            var lastX: Double,
            var lastY: Double,
        ) : ScreenshotEditorDrag {
            override val before: ScreenshotEditSnapshot? = null
        }

        data class Crop(
            val handle: ScreenshotCropHandle,
            override val before: ScreenshotEditSnapshot,
            val initial: ScreenshotCrop,
            val start: ScreenshotEditPoint,
        ) : ScreenshotEditorDrag

        data class Draw(
            override val before: ScreenshotEditSnapshot,
        ) : ScreenshotEditorDrag
    }
}
