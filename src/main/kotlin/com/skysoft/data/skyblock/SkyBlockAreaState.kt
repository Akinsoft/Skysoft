package com.skysoft.data.skyblock

import com.skysoft.data.hypixel.HypixelLocationState
import com.skysoft.utils.SidebarScoreboard
import com.skysoft.utils.SkysoftClientEvents

object SkyBlockAreaState {
    var currentArea: String? = null
        private set

    fun register() {
        SkysoftClientEvents.onEndTick(
            "SkyBlock area state",
            isActive = { HypixelLocationState.inSkyBlock || currentArea != null },
        ) {
            currentArea = if (HypixelLocationState.inSkyBlock) {
                parseSkyBlockScoreboardArea(SidebarScoreboard.currentLines())
            } else {
                null
            }
        }
        SkysoftClientEvents.onDisconnect("SkyBlock area state reset") { currentArea = null }
    }
}

internal fun parseSkyBlockScoreboardArea(lines: List<String>): String? = lines.firstNotNullOfOrNull { line ->
    line.trim()
        .takeIf { it.firstOrNull() in LOCATION_MARKERS }
        ?.drop(1)
        ?.trim()
        ?.takeIf(String::isNotEmpty)
}

private val LOCATION_MARKERS = setOf('⏣', '\uE067')
