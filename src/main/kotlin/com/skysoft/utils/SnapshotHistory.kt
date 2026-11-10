package com.skysoft.utils

import java.util.ArrayDeque

internal class SnapshotHistory<S>(
    private val maximumSteps: Int,
    private val areEquivalent: (S, S) -> Boolean = { first, second -> first == second },
) {
    private val undo = ArrayDeque<Edit<S>>()
    private val redo = ArrayDeque<Edit<S>>()

    init {
        require(maximumSteps > 0) { "Snapshot history requires at least one step" }
    }

    val canUndo: Boolean get() = undo.isNotEmpty()
    val canRedo: Boolean get() = redo.isNotEmpty()

    fun record(before: S, after: S): Boolean {
        if (areEquivalent(before, after)) return false
        undo.addLast(Edit(before, after))
        undo.trimStartToSize(maximumSteps)
        redo.clear()
        return true
    }

    fun undo(restore: (S) -> Unit): Boolean {
        val edit = undo.peekLast() ?: return false
        restore(edit.before)
        undo.removeLast()
        redo.addLast(edit)
        redo.trimStartToSize(maximumSteps)
        return true
    }

    fun redo(restore: (S) -> Unit): Boolean {
        val edit = redo.peekLast() ?: return false
        restore(edit.after)
        redo.removeLast()
        undo.addLast(edit)
        undo.trimStartToSize(maximumSteps)
        return true
    }

    private data class Edit<S>(val before: S, val after: S)
}
