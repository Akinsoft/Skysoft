package com.skysoft.gui

import com.skysoft.utils.input.InputHandlingResult
import kotlin.math.roundToInt

internal class HudEditorElementDrag(
    val element: HudEditorElement,
    mouseX: Int,
    mouseY: Int,
) {
    private val initialWidth = (element.width() * element.position.scale).roundToInt()
    private val initialHeight = (element.height() * element.position.scale).roundToInt()
    private val initialX = element.absoluteX(initialWidth)
    private val initialY = element.absoluteY(initialHeight)
    private val offsetX = mouseX - initialX
    private val offsetY = mouseY - initialY
    private val resizeHandle = findResizeHandle(element, offsetX, offsetY, initialWidth, initialHeight)

    init {
        element.beginEditorDrag(offsetX, offsetY, initialWidth, initialHeight)
    }

    fun moveTo(mouseX: Int, mouseY: Int, snapper: HudEditorSnapper) {
        val width = initialWidth.takeIf { it > 0 } ?: (element.width() * element.position.scale).roundToInt()
        val height = initialHeight.takeIf { it > 0 } ?: (element.height() * element.position.scale).roundToInt()
        resizeHandle?.let { handle ->
            resizeElement(handle, mouseX, mouseY, snapper)
            return
        }
        val targetX = mouseX - offsetX
        val targetY = mouseY - offsetY
        val snapped = snapper.snapPosition(element, targetX, targetY, width, height)
        val deltaX = snapped.x - element.absoluteX(width)
        val deltaY = snapped.y - element.absoluteY(height)
        if (element.applyEditorDrag(deltaX, deltaY) == InputHandlingResult.IGNORED && element.canMove) {
            val positionX = snapped.x - element.layoutOffsetX
            val positionY = snapped.y - element.layoutOffsetY
            if (element.keepsInsideScreen) {
                element.position.moveToAbsolute(positionX, positionY, width, height)
            } else {
                element.position.moveToAbsoluteAllowingOverflow(positionX, positionY, width, height)
            }
        }
        snapper.confirmPosition(element, width, height)
    }

    private fun resizeElement(
        handle: HudResizeHandle,
        mouseX: Int,
        mouseY: Int,
        snapper: HudEditorSnapper,
    ) {
        val mouseDeltaX = mouseX - initialX - offsetX
        val mouseDeltaY = mouseY - initialY - offsetY
        var left = initialX
        var right = initialX + initialWidth
        var top = initialY
        var bottom = initialY + initialHeight
        if (element.canResizeWidth) {
            if (handle.isLeft) left += mouseDeltaX else right += mouseDeltaX
        }
        if (element.canResizeHeight) {
            if (handle.isTop) top += mouseDeltaY else bottom += mouseDeltaY
        }
        if (element.canResizeWidth) {
            if (handle.isLeft) {
                left = snapper.snapResizeCoordinate(
                    element,
                    left,
                    HudSnapAxis.HORIZONTAL,
                    HudSnapAnchor.START,
                )
            } else {
                right = snapper.snapResizeCoordinate(
                    element,
                    right,
                    HudSnapAxis.HORIZONTAL,
                    HudSnapAnchor.END,
                )
            }
        } else {
            snapper.clear(HudSnapAxis.HORIZONTAL)
        }
        if (element.canResizeHeight) {
            if (handle.isTop) {
                top = snapper.snapResizeCoordinate(
                    element,
                    top,
                    HudSnapAxis.VERTICAL,
                    HudSnapAnchor.START,
                )
            } else {
                bottom = snapper.snapResizeCoordinate(
                    element,
                    bottom,
                    HudSnapAxis.VERTICAL,
                    HudSnapAnchor.END,
                )
            }
        } else {
            snapper.clear(HudSnapAxis.VERTICAL)
        }
        val scale = element.position.effectiveScale
        val minimumWidth = (element.minEditorWidth() * scale).roundToInt()
        val minimumHeight = (element.minEditorHeight() * scale).roundToInt()
        if (right - left < minimumWidth) {
            if (handle.isLeft) left = right - minimumWidth else right = left + minimumWidth
        }
        if (bottom - top < minimumHeight) {
            if (handle.isTop) top = bottom - minimumHeight else bottom = top + minimumHeight
        }
        element.resizeEditor(
            ((right - left) / scale).roundToInt(),
            ((bottom - top) / scale).roundToInt(),
        )
        val actualWidth = (element.width() * scale).roundToInt()
        val actualHeight = (element.height() * scale).roundToInt()
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
