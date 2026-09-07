package com.skysoft.features.bazaar

import com.skysoft.gui.OverlayControlArea

internal object BazaarDisplayState {
    var mode = TrackerDisplayMode.SESSION
        private set
    var hoveredControlArea: OverlayControlArea<TrackerControl>? = null

    fun clearInteraction() {
        hoveredControlArea = null
    }

    fun cycleMode(backwards: Boolean) {
        val currentIndex = displayModeCycle.indexOf(mode)
        check(currentIndex in displayModeCycle.indices) { "Display mode is missing from its cycle" }
        val step = if (backwards) -1 else 1
        mode = displayModeCycle[Math.floorMod(currentIndex + step, displayModeCycle.size)]
    }
}
