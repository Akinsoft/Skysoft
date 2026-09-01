package com.skysoft.utils

internal class DurationParts private constructor(
    val totalSeconds: Long,
) {
    val totalMinutes: Long get() = totalSeconds / SECONDS_PER_MINUTE
    val totalHours: Long get() = totalMinutes / MINUTES_PER_HOUR
    val days: Long get() = totalHours / HOURS_PER_DAY
    val hours: Long get() = totalHours % HOURS_PER_DAY
    val minutes: Long get() = totalMinutes % MINUTES_PER_HOUR
    val seconds: Long get() = totalSeconds % SECONDS_PER_MINUTE

    companion object {
        fun fromSeconds(seconds: Long): DurationParts = DurationParts(seconds.coerceAtLeast(0L))

        fun fromMilliseconds(milliseconds: Long, roundUp: Boolean = false): DurationParts {
            val nonNegative = milliseconds.coerceAtLeast(0L)
            val seconds = if (roundUp) Math.ceilDiv(nonNegative, MILLIS_PER_SECOND) else {
                nonNegative / MILLIS_PER_SECOND
            }
            return fromSeconds(seconds)
        }

        private const val MILLIS_PER_SECOND = 1_000L
        private const val SECONDS_PER_MINUTE = 60L
        private const val MINUTES_PER_HOUR = 60L
        private const val HOURS_PER_DAY = 24L
    }
}
