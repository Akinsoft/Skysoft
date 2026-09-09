package com.skysoft.utils.animation

import com.skysoft.utils.ElapsedTimeMark
import kotlin.time.Duration.Companion.milliseconds

internal class TimedHighlightTracker<K>(
    durationMillis: Long = DEFAULT_HIGHLIGHT_DURATION_MILLIS,
) {
    private val duration = durationMillis.milliseconds
    private val highlights = mutableMapOf<K, ElapsedTimeMark>()

    fun highlight(key: K) {
        highlights.entries.removeIf { (_, startedAt) -> startedAt.passedSince() >= duration }
        highlights[key] = ElapsedTimeMark.now()
    }

    fun isHighlighted(key: K): Boolean {
        val startedAt = highlights[key] ?: return false
        if (startedAt.passedSince() < duration) return true
        highlights.remove(key)
        return false
    }

    fun remove(key: K) {
        highlights.remove(key)
    }

    fun clear() = highlights.clear()
}

private const val DEFAULT_HIGHLIGHT_DURATION_MILLIS = 3_000L
