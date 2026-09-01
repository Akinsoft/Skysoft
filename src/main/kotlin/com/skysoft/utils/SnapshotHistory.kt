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

    fun record(before: S, after: S): ChangeResult {
        if (areEquivalent(before, after)) return ChangeResult.UNCHANGED
        undo.addLast(Edit(before, after))
        undo.trimStartToSize(maximumSteps)
        redo.clear()
        return ChangeResult.CHANGED
    }

    fun undo(restore: (S) -> Unit): ChangeResult {
        val edit = undo.pollLast() ?: return ChangeResult.UNCHANGED
        restore(edit.before)
        redo.addLast(edit)
        redo.trimStartToSize(maximumSteps)
        return ChangeResult.CHANGED
    }

    fun redo(restore: (S) -> Unit): ChangeResult {
        val edit = redo.pollLast() ?: return ChangeResult.UNCHANGED
        restore(edit.after)
        undo.addLast(edit)
        undo.trimStartToSize(maximumSteps)
        return ChangeResult.CHANGED
    }

    private data class Edit<S>(val before: S, val after: S)
}
