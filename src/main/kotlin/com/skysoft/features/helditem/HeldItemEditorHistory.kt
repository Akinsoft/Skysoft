package com.skysoft.features.helditem

import com.skysoft.config.HeldItemConfig
import com.skysoft.config.HeldItemCustomizationSnapshot
import com.skysoft.utils.ChangeResult
import com.skysoft.utils.SnapshotHistory
import java.util.Locale

internal data class HeldItemHistoryKey(val itemId: String?) {
    companion object {
        val GLOBAL = HeldItemHistoryKey(null)

        fun item(itemId: String): HeldItemHistoryKey {
            val normalizedId = itemId.trim().uppercase(Locale.US)
            require(normalizedId.isNotEmpty()) { "Held item history requires a non-empty item ID" }
            return HeldItemHistoryKey(normalizedId)
        }
    }
}

internal data class HeldItemHistoryEdit(
    val key: HeldItemHistoryKey,
    val before: HeldItemCustomizationSnapshot,
)

internal class HeldItemHistoryStore(
    private val clockNanos: () -> Long = System::nanoTime,
) {
    private val histories = mutableMapOf<HeldItemHistoryKey, ContextHistory>()

    fun begin(config: HeldItemConfig, key: HeldItemHistoryKey): HeldItemHistoryEdit {
        expireInactiveHistories()
        return HeldItemHistoryEdit(key, config.snapshotCustomization(key.itemId))
    }

    fun commit(config: HeldItemConfig, edit: HeldItemHistoryEdit): ChangeResult {
        expireInactiveHistories()
        val after = config.snapshotCustomization(edit.key.itemId)
        if (after == edit.before) return ChangeResult.UNCHANGED
        val history = histories.getOrPut(edit.key) { ContextHistory() }
        history.snapshots.record(edit.before, after)
        history.lastTouchedAtNanos = clockNanos()
        return ChangeResult.CHANGED
    }

    fun canUndo(key: HeldItemHistoryKey): Boolean {
        expireInactiveHistories()
        return histories[key]?.snapshots?.canUndo == true
    }

    fun canRedo(key: HeldItemHistoryKey): Boolean {
        expireInactiveHistories()
        return histories[key]?.snapshots?.canRedo == true
    }

    fun undo(config: HeldItemConfig, key: HeldItemHistoryKey): ChangeResult {
        expireInactiveHistories()
        val history = histories[key] ?: return ChangeResult.UNCHANGED
        val result = history.snapshots.undo { snapshot -> config.restoreCustomization(key.itemId, snapshot) }
        if (result == ChangeResult.CHANGED) history.lastTouchedAtNanos = clockNanos()
        return result
    }

    fun redo(config: HeldItemConfig, key: HeldItemHistoryKey): ChangeResult {
        expireInactiveHistories()
        val history = histories[key] ?: return ChangeResult.UNCHANGED
        val result = history.snapshots.redo { snapshot -> config.restoreCustomization(key.itemId, snapshot) }
        if (result == ChangeResult.CHANGED) history.lastTouchedAtNanos = clockNanos()
        return result
    }

    private fun expireInactiveHistories() {
        val now = clockNanos()
        histories.entries.removeIf { (_, history) ->
            now - history.lastTouchedAtNanos >= HistoryLimits.EXPIRATION_NANOS
        }
    }

    private data class ContextHistory(
        val snapshots: SnapshotHistory<HeldItemCustomizationSnapshot> = SnapshotHistory(HistoryLimits.MAX_STEPS),
        var lastTouchedAtNanos: Long = 0L,
    )
}

internal class HeldItemHistoryController(
    private val config: HeldItemConfig,
    private val store: HeldItemHistoryStore,
    private val keyProvider: () -> HeldItemHistoryKey?,
    private val clockNanos: () -> Long = System::nanoTime,
) {
    private var gestureEdit: HeldItemHistoryEdit? = null
    private var scrollEdit: PendingScrollEdit? = null

    fun beginGesture() {
        commitScroll()
        gestureEdit = keyProvider()?.let { store.begin(config, it) }
    }

    fun commitGesture(): ChangeResult {
        val edit = gestureEdit ?: return ChangeResult.UNCHANGED
        gestureEdit = null
        return store.commit(config, edit)
    }

    fun mutate(action: () -> Unit): ChangeResult {
        flushPending()
        val key = keyProvider() ?: return ChangeResult.UNCHANGED
        val edit = store.begin(config, key)
        action()
        return store.commit(config, edit)
    }

    fun mutateScroll(field: TransformField, action: () -> Unit): ChangeResult {
        val key = keyProvider() ?: return ChangeResult.UNCHANGED
        val now = clockNanos()
        val pending = scrollEdit
        if (pending == null || pending.edit.key != key || pending.field != field || pending.hasExpired(now)) {
            commitScroll()
            scrollEdit = PendingScrollEdit(store.begin(config, key), field, now)
        }
        val before = config.snapshotCustomization(key.itemId)
        action()
        scrollEdit?.lastChangedAtNanos = now
        return ChangeResult.from(before != config.snapshotCustomization(key.itemId))
    }

    fun commitIdleScroll() {
        val pending = scrollEdit ?: return
        if (pending.hasExpired(clockNanos())) commitScroll()
    }

    fun flushPending() {
        commitGesture()
        commitScroll()
    }

    fun canUndo(): Boolean {
        val key = keyProvider() ?: return false
        return hasPendingChange(key) || store.canUndo(key)
    }

    fun canRedo(): Boolean {
        val key = keyProvider() ?: return false
        if (hasPendingChange(key)) return false
        return store.canRedo(key)
    }

    fun undo(): ChangeResult {
        flushPending()
        val key = keyProvider() ?: return ChangeResult.UNCHANGED
        return store.undo(config, key)
    }

    fun redo(): ChangeResult {
        flushPending()
        val key = keyProvider() ?: return ChangeResult.UNCHANGED
        return store.redo(config, key)
    }

    private fun commitScroll(): ChangeResult {
        val edit = scrollEdit?.edit ?: return ChangeResult.UNCHANGED
        scrollEdit = null
        return store.commit(config, edit)
    }

    private fun hasPendingChange(key: HeldItemHistoryKey): Boolean {
        val edit = gestureEdit?.takeIf { it.key == key } ?: scrollEdit?.edit?.takeIf { it.key == key } ?: return false
        return edit.before != config.snapshotCustomization(key.itemId)
    }

    private data class PendingScrollEdit(
        val edit: HeldItemHistoryEdit,
        val field: TransformField,
        var lastChangedAtNanos: Long,
    ) {
        fun hasExpired(now: Long): Boolean = now - lastChangedAtNanos >= HistoryLimits.SCROLL_COALESCE_NANOS
    }
}

private object HistoryLimits {
    const val MAX_STEPS = 16
    const val EXPIRATION_NANOS = 15L * 60L * 1_000_000_000L
    const val SCROLL_COALESCE_NANOS = 350_000_000L
}
