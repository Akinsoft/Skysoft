package com.skysoft.features.waypoints

internal class WaypointHistory {
    private val undo = ArrayDeque<Entry>()
    private val redo = ArrayDeque<Entry>()
    val canUndo: Boolean get() = undo.isNotEmpty()
    val canRedo: Boolean get() = redo.isNotEmpty()

    fun record(before: List<WaypointGroup>, selection: WaypointSelection) {
        undo.addLast(Entry(before, selection))
        if (undo.size > MAX_HISTORY) undo.removeFirst()
        redo.clear()
    }

    fun undo(selection: WaypointSelection): WaypointSelection = restore(undo, redo, selection)

    fun redo(selection: WaypointSelection): WaypointSelection = restore(redo, undo, selection)

    fun clear() {
        undo.clear()
        redo.clear()
    }

    private fun restore(from: ArrayDeque<Entry>, to: ArrayDeque<Entry>, selection: WaypointSelection): WaypointSelection {
        val entry = from.lastOrNull() ?: return selection
        val current = Entry(WaypointLibrary.groups, selection)
        WaypointLibrary.replace(entry.groups)
        from.removeLast()
        to.addLast(current)
        return entry.selection
    }

    private data class Entry(val groups: List<WaypointGroup>, val selection: WaypointSelection)

    private companion object {
        const val MAX_HISTORY = 64
    }
}
