package com.skysoft.features.inventory

import com.skysoft.config.InventoryButtonDefaults
import com.skysoft.utils.gui.Rect
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

internal class InventoryButtonEditorGeometry(
    val left: Int = 0,
    val top: Int = 0,
    val scale: Float = 1f,
) {
    fun previewMouseX(mouseX: Int): Int = floor((mouseX - left) / scale.toDouble()).toInt()

    fun previewMouseY(mouseY: Int): Int = floor((mouseY - top) / scale.toDouble()).toInt()

    fun panelBounds(width: Int, height: Int, placements: List<Rect>): Rect {
        val content = previewContentBounds(placements)
        val maxPanelLeft = max(PANEL_MARGIN, width - PANEL_WIDTH - PANEL_MARGIN)
        val right = content.x + content.width + previewSideGap(placements, rightSide = true)
        if (right + PANEL_WIDTH <= width - PANEL_MARGIN) {
            return Rect(right, sidePanelTop(height, placements, rightSide = true), PANEL_WIDTH, PANEL_HEIGHT)
        }

        val left = content.x - PANEL_WIDTH - previewSideGap(placements, rightSide = false)
        if (left >= PANEL_MARGIN) {
            return Rect(left, sidePanelTop(height, placements, rightSide = false), PANEL_WIDTH, PANEL_HEIGHT)
        }

        val horizontal = (content.x + (content.width - PANEL_WIDTH) / 2)
            .coerceIn(PANEL_MARGIN, maxPanelLeft)
        val below = content.y + content.height + previewVerticalGap(placements, bottomSide = true)
        if (below + PANEL_HEIGHT <= height - PANEL_MARGIN) {
            return Rect(horizontal, below, PANEL_WIDTH, PANEL_HEIGHT)
        }

        val above = content.y - PANEL_HEIGHT - previewVerticalGap(placements, bottomSide = false)
        if (above >= PANEL_MARGIN) return Rect(horizontal, above, PANEL_WIDTH, PANEL_HEIGHT)

        val rightSpace = width - (content.x + content.width)
        val fallbackLeft = if (rightSpace >= content.x) {
            width - PANEL_WIDTH - PANEL_MARGIN
        } else {
            PANEL_MARGIN
        }.coerceIn(PANEL_MARGIN, maxPanelLeft)
        val top = sidePanelTop(height, placements, rightSide = rightSpace >= content.x)
        return Rect(fallbackLeft, top, PANEL_WIDTH, PANEL_HEIGHT)
    }

    private fun sidePanelTop(height: Int, placements: List<Rect>, rightSide: Boolean): Int {
        val buttonTop = placements
            .asSequence()
            .filter { placement ->
                if (rightSide) {
                    placement.x >= InventoryButtonPreviewDimensions.WIDTH
                } else {
                    placement.x + placement.width <= 0
                }
            }
            .minOfOrNull { it.y }
            ?: 0
        return (top + buttonTop * scale).roundToInt().coerceIn(
            PANEL_MARGIN,
            max(PANEL_MARGIN, height - PANEL_HEIGHT - PANEL_MARGIN),
        )
    }

    private fun previewContentBounds(placements: List<Rect>): Rect {
        var minX = 0
        var minY = 0
        var maxX = InventoryButtonPreviewDimensions.WIDTH
        var maxY = InventoryButtonPreviewDimensions.HEIGHT
        for (placement in placements) {
            minX = min(minX, placement.x)
            minY = min(minY, placement.y)
            maxX = max(maxX, placement.x + placement.width)
            maxY = max(maxY, placement.y + placement.height)
        }
        val x0 = left + floor(minX * scale.toDouble()).toInt()
        val y0 = top + floor(minY * scale.toDouble()).toInt()
        val x1 = left + ceil(maxX * scale.toDouble()).toInt()
        val y1 = top + ceil(maxY * scale.toDouble()).toInt()
        return Rect(x0, y0, x1 - x0, y1 - y0)
    }

    private fun previewSideGap(placements: List<Rect>, rightSide: Boolean): Int {
        val rawGap = placements.mapNotNull { placement ->
            if (rightSide) {
                (placement.x - InventoryButtonPreviewDimensions.WIDTH).takeIf { it >= 0 }
            } else {
                (0 - (placement.x + placement.width)).takeIf { it >= 0 }
            }
        }.minOrNull() ?: PREVIEW_PLACEMENT_GAP_FALLBACK
        return (rawGap * scale).roundToInt().coerceAtLeast(1)
    }

    private fun previewVerticalGap(placements: List<Rect>, bottomSide: Boolean): Int {
        val rawGap = placements.mapNotNull { placement ->
            if (bottomSide) {
                (placement.y - InventoryButtonPreviewDimensions.HEIGHT).takeIf { it >= 0 }
            } else {
                (0 - (placement.y + placement.height)).takeIf { it >= 0 }
            }
        }.minOrNull() ?: PREVIEW_PLACEMENT_GAP_FALLBACK
        return (rawGap * scale).roundToInt().coerceAtLeast(1)
    }

    companion object {
        private const val PANEL_WIDTH = 196
        private const val PANEL_HEIGHT = 318
        private const val PANEL_MARGIN = 8
        private const val PREVIEW_PLACEMENT_GAP_FALLBACK = 2

        fun centered(width: Int, height: Int, previewScale: Float): InventoryButtonEditorGeometry {
            val previewWidth = (InventoryButtonPreviewDimensions.WIDTH * previewScale).roundToInt()
            val previewHeight = (InventoryButtonPreviewDimensions.HEIGHT * previewScale).roundToInt()
            val leftCandidate = (width - previewWidth) / 2
            val left = leftCandidate.coerceIn(
                InventoryButtonPreviewDimensions.HORIZONTAL_MARGIN,
                max(
                    InventoryButtonPreviewDimensions.HORIZONTAL_MARGIN,
                    width - previewWidth - InventoryButtonPreviewDimensions.HORIZONTAL_MARGIN,
                ),
            )
            val top = ((height - previewHeight) / 2).coerceIn(
                InventoryButtonPreviewDimensions.VERTICAL_MARGIN,
                max(
                    InventoryButtonPreviewDimensions.VERTICAL_MARGIN,
                    height - previewHeight - InventoryButtonPreviewDimensions.VERTICAL_MARGIN,
                ),
            )
            return InventoryButtonEditorGeometry(left, top, previewScale)
        }
    }
}

internal object InventoryButtonPreviewDimensions {
    const val WIDTH = 176
    const val HEIGHT = InventoryButtonDefaults.PLAYER_INVENTORY_HEIGHT
    const val HORIZONTAL_MARGIN = 48
    const val VERTICAL_MARGIN = 32
}
