package com.skysoft.gui

import com.skysoft.utils.ChangeResult
import java.util.ArrayDeque

internal class HudEditorHistory(
    private val clockNanos: () -> Long = System::nanoTime,
) {
    private val undo = ArrayDeque<Edit>()
    private val redo = ArrayDeque<Edit>()
    private var pendingScroll: PendingScroll? = null

    fun record(before: HudEditorSnapshot, after: HudEditorSnapshot) {
        flushPending()
        commit(before, after)
    }

    fun recordScroll(key: String, before: HudEditorSnapshot, after: HudEditorSnapshot) {
        if (before.hasSameValue(after)) return
        val now = clockNanos()
        val pending = pendingScroll
        if (pending == null || pending.key != key || now - pending.lastChangedAtNanos >= SCROLL_COALESCE_NANOS) {
            flushPending()
            pendingScroll = PendingScroll(key, before, after, now)
        } else {
            pending.after = after
            pending.lastChangedAtNanos = now
        }
    }

    fun flushIdleScroll() {
        val pending = pendingScroll ?: return
        if (clockNanos() - pending.lastChangedAtNanos >= SCROLL_COALESCE_NANOS) flushPending()
    }

    fun flushPending() {
        val pending = pendingScroll ?: return
        pendingScroll = null
        commit(pending.before, pending.after)
    }

    fun undo(): ChangeResult {
        flushPending()
        val edit = undo.pollLast() ?: return ChangeResult.UNCHANGED
        edit.before.restore()
        redo.addLast(edit)
        redo.trimToLimit()
        return ChangeResult.CHANGED
    }

    fun redo(): ChangeResult {
        flushPending()
        val edit = redo.pollLast() ?: return ChangeResult.UNCHANGED
        edit.after.restore()
        undo.addLast(edit)
        undo.trimToLimit()
        return ChangeResult.CHANGED
    }

    private fun commit(before: HudEditorSnapshot, after: HudEditorSnapshot) {
        if (before.hasSameValue(after)) return
        undo.addLast(Edit(before, after))
        undo.trimToLimit()
        redo.clear()
    }

    private fun <T> ArrayDeque<T>.trimToLimit() {
        while (size > MAXIMUM_STEPS) removeFirst()
    }

    private data class Edit(val before: HudEditorSnapshot, val after: HudEditorSnapshot)

    private data class PendingScroll(
        val key: String,
        val before: HudEditorSnapshot,
        var after: HudEditorSnapshot,
        var lastChangedAtNanos: Long,
    )

    private companion object {
        const val MAXIMUM_STEPS = 32
        const val SCROLL_COALESCE_NANOS = 350_000_000L
    }
}
