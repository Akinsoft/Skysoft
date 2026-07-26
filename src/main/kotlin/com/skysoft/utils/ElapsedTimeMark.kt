package com.skysoft.utils

import kotlin.time.Duration
import kotlin.time.Duration.Companion.nanoseconds

@JvmInline
value class ElapsedTimeMark(private val nanos: Long) {
    fun passedSince(): Duration =
        if (nanos == FAR_PAST_NANOS) Duration.INFINITE else (System.nanoTime() - nanos).nanoseconds

    companion object {
        private const val FAR_PAST_NANOS = Long.MIN_VALUE

        fun now(): ElapsedTimeMark = ElapsedTimeMark(System.nanoTime())
        fun farPast(): ElapsedTimeMark = ElapsedTimeMark(FAR_PAST_NANOS)
    }
}
