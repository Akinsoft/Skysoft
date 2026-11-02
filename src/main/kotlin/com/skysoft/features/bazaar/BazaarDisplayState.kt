package com.skysoft.features.bazaar

import com.skysoft.gui.OverlayControlArea
import com.skysoft.gui.OverlayControlCycle

internal object BazaarDisplayState {
    var mode = TrackerDisplayMode.SESSION
        private set
    var hoveredControlArea: OverlayControlArea<TrackerControl>? = null

    fun clearInteraction() {
        hoveredControlArea = null
    }

    fun cycleMode(backwards: Boolean) {
        mode = OverlayControlCycle.next(displayModeCycle, mode, backwards)
    }
}

internal enum class TrackerDisplayMode(val displayName: String) {
    SESSION("Session"),
    TOTAL("Total"),
}

internal val displayModeCycle = listOf(TrackerDisplayMode.TOTAL, TrackerDisplayMode.SESSION)
