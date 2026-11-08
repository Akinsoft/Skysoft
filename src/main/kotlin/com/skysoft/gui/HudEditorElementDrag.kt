package com.skysoft.gui

import com.skysoft.utils.input.InputHandlingResult
import kotlin.math.roundToInt

internal class HudEditorElementDrag(
    val element: HudEditorElement,
    mouseX: Int,
    mouseY: Int,
) {
    private val initialWidth = element.scaledWidth()
    private val initialHeight = element.scaledHeight()
    private val initialX = element.absoluteX(initialWidth)
    private val initialY = element.absoluteY(initialHeight)
    private val offsetX = mouseX - initialX
    private val offsetY = mouseY - initialY
    private val resizeHandle = findResizeHandle(element, offsetX, offsetY, initialWidth, initialHeight)

    init {
        element.beginEditorDrag(offsetX, offsetY, initialWidth, initialHeight)
    }

    fun moveTo(mouseX: Int, mouseY: Int, snapper: HudEditorSnapper) {
        resizeHandle?.let { handle ->
            resizeElement(handle, mouseX, mouseY, snapper)
            return
        }
        val width = initialWidth.takeIf { it > 0 } ?: element.scaledWidth()
        val height = initialHeight.takeIf { it > 0 } ?: element.scaledHeight()
        val snapped = snapper.snapPosition(element, mouseX - offsetX, mouseY - offsetY, width, height)
        val deltaX = snapped.x - element.absoluteX(width)
        val deltaY = snapped.y - element.absoluteY(height)
        if (element.applyEditorDrag(deltaX, deltaY) == InputHandlingResult.IGNORED && element.canMove) {
            element.moveInEditor(snapped.x, snapped.y, width, height)
        }
        snapper.confirmPosition(element, width, height)
    }

    private fun resizeElement(
        handle: HudResizeHandle,
        mouseX: Int,
        mouseY: Int,
        snapper: HudEditorSnapper,
    ) {
        val scale = element.position.scale
        val (left, right) = resizedEdges(
            initialX,
            initialX + initialWidth,
            mouseX - initialX - offsetX,
            (element.minEditorWidth() * scale).roundToInt(),
            element.canResizeWidth,
            handle.isLeft,
            HudSnapAxis.HORIZONTAL,
            snapper,
        )
        val (top, bottom) = resizedEdges(
            initialY,
            initialY + initialHeight,
            mouseY - initialY - offsetY,
            (element.minEditorHeight() * scale).roundToInt(),
            element.canResizeHeight,
            handle.isTop,
            HudSnapAxis.VERTICAL,
            snapper,
        )
        element.resizeEditor(
            ((right - left) / scale).roundToInt(),
            ((bottom - top) / scale).roundToInt(),
        )
        val actualWidth = element.scaledWidth(scale)
        val actualHeight = element.scaledHeight(scale)
        val actualLeft = if (handle.isLeft) right - actualWidth else left
        val actualTop = if (handle.isTop) bottom - actualHeight else top
        element.position.moveToAbsoluteAllowingOverflow(
            actualLeft - element.layoutOffsetX,
            actualTop - element.layoutOffsetY,
            actualWidth,
            actualHeight,
        )
        snapper.confirmPosition(element, actualWidth, actualHeight)
    }

    private fun resizedEdges(
        start: Int,
        end: Int,
        mouseDelta: Int,
        minimumLength: Int,
        isResizable: Boolean,
        movesStart: Boolean,
        axis: HudSnapAxis,
        snapper: HudEditorSnapper,
    ): Pair<Int, Int> {
        var newStart = start
        var newEnd = end
        when {
            !isResizable -> snapper.clear(axis)
            movesStart -> newStart = snapper.snapResizeCoordinate(element, start + mouseDelta, axis, HudSnapAnchor.START)
            else -> newEnd = snapper.snapResizeCoordinate(element, end + mouseDelta, axis, HudSnapAnchor.END)
        }
        if (newEnd - newStart < minimumLength) {
            if (movesStart) newStart = newEnd - minimumLength else newEnd = newStart + minimumLength
        }
        return newStart to newEnd
    }
}

internal fun findResizeHandle(
    element: HudEditorElement,
    localX: Int,
    localY: Int,
    width: Int,
    height: Int,
): HudResizeHandle? {
    if (!element.canResizeWidth && !element.canResizeHeight) return null
    val horizontal = when {
        localX in -RESIZE_HANDLE_HITBOX until 0 -> true
        localX in width until width + RESIZE_HANDLE_HITBOX -> false
        else -> return null
    }
    val vertical = when {
        localY in -RESIZE_HANDLE_HITBOX until 0 -> true
        localY in height until height + RESIZE_HANDLE_HITBOX -> false
        else -> return null
    }
    return HudResizeHandle(isLeft = horizontal, isTop = vertical)
}

internal data class HudResizeHandle(val isLeft: Boolean, val isTop: Boolean)

private const val RESIZE_HANDLE_HITBOX = 6
