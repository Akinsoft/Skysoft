package com.skysoft.utils

import java.util.LinkedHashMap

internal fun <K, V> boundedAccessOrderMap(maximumSize: Int): LinkedHashMap<K, V> =
    object : LinkedHashMap<K, V>(maximumSize, LOAD_FACTOR, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>?): Boolean = size > maximumSize
    }

private const val LOAD_FACTOR = 0.75f
