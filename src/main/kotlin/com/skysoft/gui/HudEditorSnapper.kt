package com.skysoft.gui

import com.skysoft.utils.gui.Rect
import kotlin.math.roundToInt

internal class HudEditorSnapper(
    private val elements: () -> List<HudEditorElement>,
    private val usesInventoryCoordinates: (HudEditorElement) -> Boolean,
    private val isSnapping: () -> Boolean,
) {
    private var horizontalLock: HudSnapLock? = null
    private var verticalLock: HudSnapLock? = null
    private var movingBounds: HudSnapBounds? = null

    var gridEnabled = false

    fun snapPosition(
        element: HudEditorElement,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
    ): HudSnappedPosition {
        val gridPosition = HudSnappedPosition(
            hudGridTarget(
                x,
                element.absoluteX(width),
                element.position.x,
                element.editorGridSpacing,
                gridEnabled,
            ),
            hudGridTarget(
                y,
                element.absoluteY(height),
                element.position.y,
                element.editorGridSpacing,
                gridEnabled,
            ),
        )
        return snapPosition(x, y, width, height, gridPosition) { axis -> targets(element, axis) }
    }

    fun snapPosition(
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        targets: List<Rect>,
    ): HudSnappedPosition = snapPosition(
        x,
        y,
        width,
        height,
        HudSnappedPosition(
            hudGridCoordinate(x, HUD_EDITOR_GRID_SPACING, gridEnabled),
            hudGridCoordinate(y, HUD_EDITOR_GRID_SPACING, gridEnabled),
        ),
    ) { axis ->
        targets.flatMap { target ->
            targetPoints(
                HudSnapBounds(target.x, target.y, target.x + target.width, target.y + target.height),
                axis,
            )
        }
    }

    private fun snapPosition(
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        gridPosition: HudSnappedPosition,
        targetProvider: (HudSnapAxis) -> List<HudSnapTargetPoint>,
    ): HudSnappedPosition {
        if (!isSnapping()) {
            clear()
            return gridPosition
        }
        val rawBounds = HudSnapBounds(x, y, x + width, y + height)
        val horizontalOffset = snapAxis(
            HudSnapAxis.HORIZONTAL,
            axisPoints(x, width),
            rawBounds,
            matchingAnchorsOnly = true,
            targets = targetProvider(HudSnapAxis.HORIZONTAL),
        )
        val verticalOffset = snapAxis(
            HudSnapAxis.VERTICAL,
            axisPoints(y, height),
            rawBounds,
            matchingAnchorsOnly = true,
            targets = targetProvider(HudSnapAxis.VERTICAL),
        )
        return HudSnappedPosition(
            if (horizontalLock != null) x + horizontalOffset else gridPosition.x,
            if (verticalLock != null) y + verticalOffset else gridPosition.y,
        )
    }

    fun snapResizeCoordinate(
        element: HudEditorElement,
        value: Int,
        axis: HudSnapAxis,
        anchor: HudSnapAnchor,
    ): Int {
        val elementBounds = bounds(element)
        val gridOrigin = when (axis) {
            HudSnapAxis.HORIZONTAL -> elementBounds.left - element.position.x
            HudSnapAxis.VERTICAL -> elementBounds.top - element.position.y
        }
        val gridValue = gridOrigin + hudGridCoordinate(
            value - gridOrigin,
            element.editorGridSpacing,
            gridEnabled,
        )
        if (!isSnapping()) {
            clear(axis)
            return gridValue
        }
        val offset = snapAxis(
            axis,
            listOf(HudSnapPoint(anchor, value)),
            elementBounds,
            matchingAnchorsOnly = false,
            targets = targets(element, axis),
        )
        return if (lock(axis) != null) value + offset else gridValue
    }

    fun guides(): List<HudSnapGuide> {
        val currentBounds = movingBounds ?: return emptyList()
        return buildList {
            horizontalLock?.let { add(guide(HudSnapAxis.HORIZONTAL, it, currentBounds)) }
            verticalLock?.let { add(guide(HudSnapAxis.VERTICAL, it, currentBounds)) }
        }
    }

    fun confirmPosition(element: HudEditorElement, width: Int, height: Int) {
        val left = element.absoluteX(width)
        val top = element.absoluteY(height)
        confirmPosition(
            HudSnapBounds(
                left = left,
                top = top,
                right = left + width,
                bottom = top + height,
            ),
        )
    }

    fun confirmPosition(bounds: Rect) {
        confirmPosition(
            HudSnapBounds(
                left = bounds.x,
                top = bounds.y,
                right = bounds.x + bounds.width,
                bottom = bounds.y + bounds.height,
            ),
        )
    }

    private fun confirmPosition(confirmedBounds: HudSnapBounds) {
        movingBounds = confirmedBounds
        confirmAxis(
            HudSnapAxis.HORIZONTAL,
            axisPoints(confirmedBounds.left, confirmedBounds.right - confirmedBounds.left),
        )
        confirmAxis(
            HudSnapAxis.VERTICAL,
            axisPoints(confirmedBounds.top, confirmedBounds.bottom - confirmedBounds.top),
        )
    }

    fun clear() {
        horizontalLock = null
        verticalLock = null
        movingBounds = null
    }

    fun clear(axis: HudSnapAxis) {
        setLock(axis, null)
    }

    private fun snapAxis(
        axis: HudSnapAxis,
        movingPoints: List<HudSnapPoint>,
        movingBounds: HudSnapBounds,
        matchingAnchorsOnly: Boolean,
        targets: List<HudSnapTargetPoint>,
    ): Int {
        val currentLock = lock(axis)
        if (currentLock != null) {
            val movingPoint = movingPoints.firstOrNull { it.anchor == currentLock.movingAnchor }
            if (movingPoint != null) {
                val offset = currentLock.targetCoordinate - movingPoint.coordinate
                if (kotlin.math.abs(offset) <= SNAP_RELEASE_DISTANCE) return offset
            }
            setLock(axis, null)
        }

        val candidate = targets
            .asSequence()
            .flatMap { target ->
                movingPoints.asSequence().mapNotNull { moving ->
                    candidate(moving, target, axis, movingBounds, matchingAnchorsOnly)
                }
            }
            .filter { it.distance <= SNAP_ACQUIRE_DISTANCE }
            .minWithOrNull(
                compareBy<HudSnapCandidate> { it.distance }
                    .thenBy { it.perpendicularDistance }
                    .thenBy { it.priority },
            )
            ?: return 0
        setLock(
            axis,
            HudSnapLock(
                movingAnchor = candidate.movingAnchor,
                targetCoordinate = candidate.targetCoordinate,
                targetBounds = candidate.targetBounds,
                relation = candidate.relation,
            ),
        )
        return candidate.targetCoordinate - candidate.movingCoordinate
    }

    private fun targets(element: HudEditorElement, axis: HudSnapAxis): List<HudSnapTargetPoint> {
        val values = mutableListOf<HudSnapTargetPoint>()
        elements()
            .filter {
                it !== element &&
                    !it.isInSnapGroupWith(element) &&
                    usesInventoryCoordinates(it) == usesInventoryCoordinates(element)
            }
            .forEach { target ->
                values += targetPoints(bounds(target), axis)
            }
        return values
    }

    private fun candidate(
        moving: HudSnapPoint,
        target: HudSnapTargetPoint,
        axis: HudSnapAxis,
        movingBounds: HudSnapBounds,
        matchingAnchorsOnly: Boolean,
    ): HudSnapCandidate? {
        val isAdjacent = moving.anchor.isAdjacentTo(target.anchor)
        val priority = when {
            moving.anchor == HudSnapAnchor.CENTER && target.anchor == HudSnapAnchor.CENTER ->
                CENTER_SNAP_PRIORITY
            moving.anchor == target.anchor -> MATCHING_EDGE_SNAP_PRIORITY
            isAdjacent -> ADJACENT_EDGE_SNAP_PRIORITY
            matchingAnchorsOnly -> return null
            else -> CROSS_ANCHOR_SNAP_PRIORITY
        }
        return HudSnapCandidate(
            movingAnchor = moving.anchor,
            movingCoordinate = moving.coordinate,
            targetCoordinate = target.coordinate,
            distance = kotlin.math.abs(target.coordinate - moving.coordinate),
            priority = priority,
            perpendicularDistance = movingBounds.perpendicularDistance(target.bounds, axis),
            targetBounds = target.bounds,
            relation = if (isAdjacent) HudSnapRelation.ADJACENCY else HudSnapRelation.ALIGNMENT,
        )
    }

    private fun axisPoints(
        start: Int,
        length: Int,
    ): List<HudSnapPoint> = listOf(
        HudSnapPoint(HudSnapAnchor.START, start),
        HudSnapPoint(HudSnapAnchor.CENTER, snapCenter(start, length)),
        HudSnapPoint(HudSnapAnchor.END, start + length),
    )

    private fun targetPoints(bounds: HudSnapBounds, axis: HudSnapAxis): List<HudSnapTargetPoint> {
        val start = if (axis == HudSnapAxis.HORIZONTAL) bounds.left else bounds.top
        val end = if (axis == HudSnapAxis.HORIZONTAL) bounds.right else bounds.bottom
        return listOf(
            HudSnapTargetPoint(HudSnapAnchor.START, start, bounds),
            HudSnapTargetPoint(HudSnapAnchor.CENTER, snapCenter(start, end - start), bounds),
            HudSnapTargetPoint(HudSnapAnchor.END, end, bounds),
        )
    }

    private fun bounds(element: HudEditorElement): HudSnapBounds {
        val scale = element.position.effectiveScale
        val width = (element.width() * scale).roundToInt()
        val height = (element.height() * scale).roundToInt()
        val left = element.absoluteX(width)
        val top = element.absoluteY(height)
        return HudSnapBounds(left, top, left + width, top + height)
    }

    private fun guide(axis: HudSnapAxis, lock: HudSnapLock, moving: HudSnapBounds): HudSnapGuide {
        val movingCenter = moving.perpendicularCenter(axis)
        val targetCenter = lock.targetBounds.perpendicularCenter(axis)
        val useFullBounds = lock.relation == HudSnapRelation.ADJACENCY || movingCenter == targetCenter
        val start = if (useFullBounds) {
            minOf(moving.perpendicularStart(axis), lock.targetBounds.perpendicularStart(axis))
        } else {
            minOf(movingCenter, targetCenter)
        }
        val end = if (useFullBounds) {
            maxOf(moving.perpendicularEnd(axis), lock.targetBounds.perpendicularEnd(axis))
        } else {
            maxOf(movingCenter, targetCenter)
        }
        return HudSnapGuide(axis, lock.targetCoordinate, start, end, lock.targetBounds)
    }

    private fun confirmAxis(axis: HudSnapAxis, points: List<HudSnapPoint>) {
        val currentLock = lock(axis) ?: return
        val actualCoordinate = points.firstOrNull { it.anchor == currentLock.movingAnchor }?.coordinate
        if (actualCoordinate != currentLock.targetCoordinate) clear(axis)
    }

    private fun lock(axis: HudSnapAxis): HudSnapLock? = when (axis) {
        HudSnapAxis.HORIZONTAL -> horizontalLock
        HudSnapAxis.VERTICAL -> verticalLock
    }

    private fun setLock(axis: HudSnapAxis, lock: HudSnapLock?) {
        when (axis) {
            HudSnapAxis.HORIZONTAL -> horizontalLock = lock
            HudSnapAxis.VERTICAL -> verticalLock = lock
        }
    }
}

private fun HudEditorElement.isInSnapGroupWith(other: HudEditorElement): Boolean =
    snapGroup != null && snapGroup == other.snapGroup

private fun HudSnapAnchor.isAdjacentTo(other: HudSnapAnchor): Boolean =
    this == HudSnapAnchor.START && other == HudSnapAnchor.END ||
        this == HudSnapAnchor.END && other == HudSnapAnchor.START

internal enum class HudSnapAxis {
    HORIZONTAL,
    VERTICAL,
}

internal enum class HudSnapAnchor {
    START,
    CENTER,
    END,
}

private enum class HudSnapRelation {
    ALIGNMENT,
    ADJACENCY,
}

private data class HudSnapPoint(val anchor: HudSnapAnchor, val coordinate: Int)

private data class HudSnapTargetPoint(
    val anchor: HudSnapAnchor,
    val coordinate: Int,
    val bounds: HudSnapBounds,
)

private data class HudSnapCandidate(
    val movingAnchor: HudSnapAnchor,
    val movingCoordinate: Int,
    val targetCoordinate: Int,
    val distance: Int,
    val priority: Int,
    val perpendicularDistance: Int,
    val targetBounds: HudSnapBounds,
    val relation: HudSnapRelation,
)

private data class HudSnapLock(
    val movingAnchor: HudSnapAnchor,
    val targetCoordinate: Int,
    val targetBounds: HudSnapBounds,
    val relation: HudSnapRelation,
)

internal data class HudSnapGuide(
    val axis: HudSnapAxis,
    val coordinate: Int,
    val start: Int,
    val end: Int,
    val targetBounds: HudSnapBounds,
)

internal data class HudSnappedPosition(val x: Int, val y: Int)

internal data class HudSnapBounds(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    fun perpendicularStart(axis: HudSnapAxis): Int =
        if (axis == HudSnapAxis.HORIZONTAL) top else left

    fun perpendicularEnd(axis: HudSnapAxis): Int =
        if (axis == HudSnapAxis.HORIZONTAL) bottom else right

    fun perpendicularCenter(axis: HudSnapAxis): Int {
        val start = perpendicularStart(axis)
        return snapCenter(start, perpendicularEnd(axis) - start)
    }

    fun perpendicularDistance(other: HudSnapBounds, axis: HudSnapAxis): Int {
        val start = perpendicularStart(axis)
        val end = perpendicularEnd(axis)
        val otherStart = other.perpendicularStart(axis)
        val otherEnd = other.perpendicularEnd(axis)
        return when {
            end < otherStart -> otherStart - end
            otherEnd < start -> start - otherEnd
            else -> 0
        }
    }
}

private fun snapCenter(start: Int, length: Int): Int = start + length / 2

private const val SNAP_ACQUIRE_DISTANCE = 8
private const val SNAP_RELEASE_DISTANCE = 12
private const val CENTER_SNAP_PRIORITY = 0
private const val MATCHING_EDGE_SNAP_PRIORITY = 1
private const val ADJACENT_EDGE_SNAP_PRIORITY = 2
private const val CROSS_ANCHOR_SNAP_PRIORITY = 3
