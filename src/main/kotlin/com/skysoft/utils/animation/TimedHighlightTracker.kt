package com.skysoft.utils.animation

internal class TimedHighlightTracker<K>(
    private val durationMillis: Long = DEFAULT_HIGHLIGHT_DURATION_MILLIS,
) {
    private val expirations = mutableMapOf<K, Long>()

    fun highlight(key: K) {
        val now = System.currentTimeMillis()
        expirations.entries.removeIf { (_, expiresAt) -> now >= expiresAt }
        expirations[key] = now + durationMillis
    }

    fun isHighlighted(key: K): Boolean {
        val expiresAt = expirations[key] ?: return false
        if (System.currentTimeMillis() < expiresAt) return true
        expirations.remove(key)
        return false
    }

    fun remove(key: K) {
        expirations.remove(key)
    }

    fun clear() = expirations.clear()
}

private const val DEFAULT_HIGHLIGHT_DURATION_MILLIS = 3_000L
