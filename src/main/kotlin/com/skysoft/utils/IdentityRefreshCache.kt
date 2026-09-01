package com.skysoft.utils

internal class IdentityRefreshCache<C, V>(
    private val refreshIntervalMillis: Long,
) {
    private var entry: Entry<C, V>? = null

    init {
        require(refreshIntervalMillis > 0L) { "Identity refresh cache interval must be positive" }
    }

    fun value(identity: Any?, criteria: C, nowMillis: Long, loader: () -> V): V {
        val refreshBucket = Math.floorDiv(nowMillis, refreshIntervalMillis)
        val current = entry
        if (
            current != null &&
            current.identity === identity &&
            current.criteria == criteria &&
            current.refreshBucket == refreshBucket
        ) {
            return current.value
        }
        return loader().also { value ->
            entry = Entry(identity, criteria, refreshBucket, value)
        }
    }


    private data class Entry<C, V>(
        val identity: Any?,
        val criteria: C,
        val refreshBucket: Long,
        val value: V,
    )
}
