package com.skysoft.features.waypoints

import com.skysoft.utils.MinecraftClient
import com.skysoft.utils.input.InputUtilities

internal object WaypointKeybinds {
    private var held = emptySet<Int>()

    fun tick() {
        val settings = Waypoints.config.settings
        val bindings = WaypointBinding.entries.map { it.value() }
        val down = bindings.filter(InputUtilities::isActionBindingDown).toSet()
        val pressed = down - held
        held = down
        if (MinecraftClient.screen() != null || Waypoints.island == null) return
        if (settings.editorKey in pressed) {
            WaypointPanel.open()
        } else if (Waypoints.config.enabled) {
            WaypointPanel.perform {
                when {
                    settings.feetKey in pressed -> WaypointPlacement.feet?.let { WaypointEditing.add(it) }
                    settings.crosshairKey in pressed -> {
                        WaypointPlacement.captureAim()
                        val position = WaypointPlacement.crosshair ?: error("Look at a block within 128 blocks first.")
                        WaypointEditing.add(position)
                    }
                    settings.followKey in pressed -> Waypoints.toggleFollowing()
                    settings.nextKey in pressed -> Waypoints.step(1)
                    settings.previousKey in pressed -> Waypoints.step(-1)
                    settings.stopKey in pressed -> Waypoints.route.stop()
                }
            }
        }
    }
}
