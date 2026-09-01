package com.skysoft.utils

import java.util.ArrayDeque

internal fun <T> ArrayDeque<T>.trimStartToSize(maximumSize: Int) {
    require(maximumSize >= 0) { "Maximum deque size must not be negative" }
    while (size > maximumSize) removeFirst()
}
