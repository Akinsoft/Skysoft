package com.skysoft.features.waypoints

import com.skysoft.utils.gui.OverlayTextStyle
import com.skysoft.utils.gui.Rect

internal data class WaypointSettingsLayout(
    val bounds: Rect,
    val optionRows: List<Rect>,
    val optionOffset: Int,
    val maximumOffset: Int,
) {
    val close: Rect get() = Rect(bounds.x + bounds.width - INSET - CLOSE_SIZE, bounds.y + CLOSE_Y, CLOSE_SIZE, CLOSE_SIZE)
    val tabs: Rect get() = Rect(bounds.x + INSET, bounds.y + HEADER, bounds.width - INSET * 2, ROW_HEIGHT)
    val back: Rect get() = Rect(bounds.x + INSET, bounds.y + bounds.height - INSET - ROW_HEIGHT, BACK_WIDTH, ROW_HEIGHT)
    val scrollIndicator: Rect get() = Rect(back.x, back.y - GAP - ROW_HEIGHT, bounds.width - INSET * 2, ROW_HEIGHT)

    fun isOptionVisible(index: Int): Boolean = index in optionOffset until optionOffset + optionRows.size

    fun optionRow(index: Int): Rect = optionRows[index - optionOffset]

    fun value(row: Rect): Rect = Rect(row.x + row.width - VALUE_WIDTH, row.y, VALUE_WIDTH, row.height)

    fun label(row: Rect): Rect = Rect(
        row.x, row.y + if (row.height == SLIDER_HEIGHT) SLIDER_TEXT_Y else TEXT_Y, row.width - VALUE_WIDTH - INSET, row.height,
    )

    fun track(setting: WaypointSlider): Rect {
        val row = optionRow(setting.ordinal)
        return Rect(row.x, row.y + TRACK_Y, row.width, TRACK_HEIGHT)
    }

    companion object {
        const val HEADER = 24
        const val INSET = 8
        const val WIDTH = 248
        private const val ROW_HEIGHT = OverlayTextStyle.ROW_HEIGHT
        private const val GAP = 3
        private const val CONTENT_TOP = HEADER + ROW_HEIGHT + GAP
        private const val CHROME_HEIGHT = CONTENT_TOP + GAP + ROW_HEIGHT + INSET
        private const val SLIDER_HEIGHT = 22
        private const val TEXT_Y = 1
        private const val SLIDER_TEXT_Y = 4
        private const val VALUE_WIDTH = 100
        private const val CLOSE_SIZE = 16
        private const val CLOSE_Y = 4
        private const val BACK_WIDTH = 40
        private const val TRACK_Y = 17
        private const val TRACK_HEIGHT = 3

        fun create(panel: WaypointPanelLayout, screenWidth: Int, screenHeight: Int, offset: Int): WaypointSettingsLayout {
            val heights = if (WaypointSettingsPanel.showDetails) {
                WaypointSlider.entries.map { SLIDER_HEIGHT } + WaypointDetailToggle.visibleEntries.map { ROW_HEIGHT }
            } else {
                WaypointBinding.entries.map { ROW_HEIGHT }
            }
            val room = (screenHeight - WaypointPanelLayout.MARGIN * 2 - CHROME_HEIGHT).coerceAtLeast(0)
            val scrolling = heights.sum() > room
            val available = (room - if (scrolling) ROW_HEIGHT + GAP else 0).coerceAtLeast(0)
            fun fit(rows: List<Int>): Int = rows.runningFold(0, Int::plus).drop(1).takeWhile { it <= available }.size
            val maximumOffset = (heights.size - fit(heights.asReversed()).coerceAtLeast(1)).coerceAtLeast(0)
            val optionOffset = offset.coerceIn(0, maximumOffset)
            val visible = heights.drop(optionOffset).take(fit(heights.drop(optionOffset)))
            val height = CHROME_HEIGHT + if (scrolling) available + ROW_HEIGHT + GAP else visible.sum()
            val bounds = panel.beside(WIDTH, height, screenWidth, screenHeight)
            var y = bounds.y + CONTENT_TOP
            val rows = visible.map { rowHeight ->
                Rect(bounds.x + INSET, y, bounds.width - INSET * 2, rowHeight).also { y += rowHeight }
            }
            return WaypointSettingsLayout(bounds, rows, optionOffset, maximumOffset)
        }
    }
}
