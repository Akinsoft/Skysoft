package com.skysoft.gui

import com.skysoft.utils.ChangeResult
import com.skysoft.utils.SnapshotHistory

internal class HudEditorHistory(
    private val clockNanos: () -> Long = System::nanoTime,
) {
    private val history = SnapshotHistory<HudEditorSnapshot>(MAXIMUM_STEPS) { first, second ->
        first.hasSameValue(second)
    }
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
        return history.undo(HudEditorSnapshot::restore)
    }

    fun redo(): ChangeResult {
        flushPending()
        return history.redo(HudEditorSnapshot::restore)
    }

    private fun commit(before: HudEditorSnapshot, after: HudEditorSnapshot) {
        history.record(before, after)
    }

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
