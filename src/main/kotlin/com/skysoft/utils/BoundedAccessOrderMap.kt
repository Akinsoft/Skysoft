package com.skysoft.utils

import java.util.LinkedHashMap

internal fun <K, V> boundedAccessOrderMap(
    maximumSize: Int,
    onEviction: (K, V) -> Unit = { _, _ -> },
): LinkedHashMap<K, V> =
    object : LinkedHashMap<K, V>(maximumSize, LOAD_FACTOR, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>?): Boolean {
            val remove = size > maximumSize
            if (remove && eldest != null) onEviction(eldest.key, eldest.value)
            return remove
        }
    }

private const val LOAD_FACTOR = 0.75f
