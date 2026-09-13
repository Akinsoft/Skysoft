package com.skysoft.features.waypoints

import com.skysoft.utils.MinecraftClient
import com.skysoft.utils.gui.OverlayTextStyle
import com.skysoft.utils.gui.Rect
import com.skysoft.utils.render.LegacyTextRenderer

internal data class WaypointPanelLayout(
    val bounds: Rect,
    val visibleRows: Int,
    val listTop: Int,
    val showPlacementControls: Boolean,
    val showRouteControls: Boolean,
    val hasMoreItems: Boolean,
    val showFooter: Boolean,
) {
    val header: Rect get() = Rect(bounds.x, bounds.y, bounds.width, HEADER)
    val contentWidth: Int get() = bounds.width - INSET * 2
    val list: Rect get() = Rect(bounds.x + INSET, bounds.y + listTop, contentWidth, visibleRows * ROW_HEIGHT)
    val search: Rect get() = Rect(bounds.x + INSET, bounds.y + HEADER + ROW_HEIGHT + GAP, contentWidth, WaypointForm.FIELD_HEIGHT)
    val footerTop: Int get() = list.y + list.height + (if (hasMoreItems) INDICATOR_HEIGHT else 0) + GAP
    val routeControlsTop: Int get() = footerTop + if (showPlacementControls) SECTION_STEP else 0
    val actionsTop: Int get() = routeControlsTop + if (showRouteControls) SECTION_STEP else 0

    fun row(offset: Int): Rect = Rect(bounds.x + INSET, offset, contentWidth, BUTTON_HEIGHT)

    fun beside(preferredWidth: Int, preferredHeight: Int, screenWidth: Int, screenHeight: Int): Rect {
        val width = minOf(preferredWidth, screenWidth - MARGIN * 2).coerceAtLeast(1)
        val height = minOf(preferredHeight, screenHeight - MARGIN * 2).coerceAtLeast(1)
        val right = bounds.x + bounds.width + SIDE_GAP
        val left = bounds.x - width - SIDE_GAP
        val x = when {
            right + width <= screenWidth - MARGIN -> right
            left >= MARGIN -> left
            else -> (screenWidth - width) / 2
        }
        return Rect(x.coerceAtLeast(0), bounds.y.coerceIn(0, (screenHeight - height).coerceAtLeast(0)), width, height)
    }

    companion object {
        const val INSET = 5
        const val HEADER = 17
        const val GAP = 3
        const val BUTTON_HEIGHT = OverlayTextStyle.ROW_HEIGHT
        const val ROW_HEIGHT = OverlayTextStyle.ROW_HEIGHT
        private const val PRESET_LIST_TOP = HEADER + ROW_HEIGHT + GAP + WaypointForm.FIELD_HEIGHT + GAP
        const val INDICATOR_HEIGHT = 12
        const val SECTION_STEP = BUTTON_HEIGHT + GAP
        const val MARGIN = 4
        private const val CONTROL_GAP = 6
        private const val SIDE_GAP = 4
        private const val WIDTH = 268
        private const val MIN_WIDTH = 160
        private const val MAX_ROWS = 8

        fun columns(row: Rect, vararg labels: String): List<Rect> {
            val widths = labels.map { LegacyTextRenderer.width("[$it]") + 2 }
            val scale = minOf(1.0, (row.width - CONTROL_GAP * (labels.size - 1)).toDouble() / widths.sum())
            var x = row.x
            return widths.map { width ->
                Rect(x, row.y, (width * scale).toInt(), row.height).also { x += it.width + CONTROL_GAP }
            }
        }

        fun canFitBeside(screenWidth: Int, sideWidth: Int): Boolean =
            screenWidth >= MIN_WIDTH + sideWidth + MARGIN * 2 + SIDE_GAP

        fun create(screenWidth: Int, screenHeight: Int): WaypointPanelLayout {
            val sideWidth = (MinecraftClient.screen() as? WaypointDialog)?.preferredWidth
                ?: if (WaypointSettingsPanel.isOpen) WaypointSettingsLayout.WIDTH else 0
            val reservedSide = sideWidth.takeIf { canFitBeside(screenWidth, it) } ?: 0
            val width = minOf(WIDTH, screenWidth - reservedSide - MARGIN * 2 - SIDE_GAP).coerceAtLeast(1)
            val settings = Waypoints.config
            val showFooter = WaypointPanel.showFooter
            val editing = !WaypointPanel.isBrowsingPresets && settings.enabled
            val showPlacement = showFooter && editing && settings.details.showPlacementControls
            val showRoute = showFooter && editing && settings.details.showRouteControls && Waypoints.group?.route == true
            val footerRows = (if (showFooter) 1 else 0) + (if (showPlacement) 1 else 0) + (if (showRoute) 1 else 0)
            val footerHeight = INDICATOR_HEIGHT + GAP * 2 + footerRows * SECTION_STEP
            val count = if (editing || WaypointPanel.isBrowsingPresets) WaypointPanel.itemCount else 0
            val listTop = if (WaypointPanel.isBrowsingPresets) PRESET_LIST_TOP else HEADER
            val availableRows = ((screenHeight - MARGIN * 2 - listTop - footerHeight) / ROW_HEIGHT).coerceIn(1, MAX_ROWS)
            val rows = minOf(count.coerceAtLeast(1), availableRows)
            val hasMore = count > rows
            val height = listTop + rows * ROW_HEIGHT + footerHeight - if (hasMore) 0 else INDICATOR_HEIGHT
            var x = (settings.editorX.takeIf { it >= 0 } ?: MARGIN).coerceIn(MARGIN, (screenWidth - width - MARGIN).coerceAtLeast(MARGIN))
            if (reservedSide > 0 && x + width + SIDE_GAP + reservedSide > screenWidth - MARGIN && x - reservedSide - SIDE_GAP < MARGIN) {
                x = screenWidth - MARGIN - width - SIDE_GAP - reservedSide
            }
            val y = settings.editorY.takeIf { it >= 0 } ?: MARGIN
            return WaypointPanelLayout(
                Rect(
                    x.coerceIn(0, (screenWidth - width).coerceAtLeast(0)),
                    y.coerceIn(0, (screenHeight - height).coerceAtLeast(0)),
                    width,
                    height,
                ),
                rows,
                listTop,
                showPlacement,
                showRoute,
                hasMore,
                showFooter,
            )
        }
    }
}
