package com.skysoft.features.waypoints

import com.skysoft.gui.OverlayControlMouse
import com.skysoft.utils.input.InputUtilities
import kotlin.math.abs
import net.minecraft.client.Minecraft

internal object WaypointPointDrag {
    private var selection: WaypointSelection? = null
    private var startY = 0
    private var lastScroll = 0L
    var targetIndex: Int? = null
        private set

    fun begin(pointId: String, y: Int) {
        selection = WaypointSelection(Waypoints.selection.groupId, pointId)
        startY = y
        targetIndex = null
        lastScroll = 0L
    }

    fun didDrag(y: Int): Boolean {
        val selected = selection ?: return false
        if (abs(y - startY) < DRAG_THRESHOLD && targetIndex == null) return true
        val layout = WaypointPanel.layout ?: return true
        val count = Waypoints.group?.points?.size ?: return true
        if (count == 0 || Waypoints.selection.groupId != selected.groupId) return true
        Waypoints.selection = selected
        val row = ((y - layout.list.y) / WaypointPanelLayout.ROW_HEIGHT).coerceIn(0, layout.visibleRows - 1)
        targetIndex = (WaypointPanel.scrollOffset + row).coerceIn(0, count - 1)
        return true
    }

    fun tick() {
        if (targetIndex == null) return
        val mouse = InputUtilities.scaledMousePosition(Minecraft.getInstance())
        val pointerY = OverlayControlMouse.normalPoint(mouse.x, mouse.y).second
        val layout = WaypointPanel.layout ?: return
        val now = System.nanoTime()
        val direction = when {
            pointerY < layout.list.y -> -1
            pointerY >= layout.list.y + layout.list.height -> 1
            else -> 0
        }
        if (direction != 0 && now - lastScroll >= SCROLL_INTERVAL) {
            val maximum = ((Waypoints.group?.points?.size ?: 0) - layout.visibleRows).coerceAtLeast(0)
            WaypointPanel.scrollOffset = (WaypointPanel.scrollOffset + direction).coerceIn(0, maximum)
            lastScroll = now
        }
        didDrag(pointerY)
    }

    fun didFinish(): Boolean {
        val selected = selection ?: return false
        val target = targetIndex
        cancel()
        if (selected == Waypoints.selection && target != null) WaypointPanel.perform { WaypointEditing.reorderPoint(target) }
        return true
    }

    fun cancel() {
        selection = null
        targetIndex = null
    }

    private const val DRAG_THRESHOLD = 3
    private const val SCROLL_INTERVAL = 100_000_000L
}
