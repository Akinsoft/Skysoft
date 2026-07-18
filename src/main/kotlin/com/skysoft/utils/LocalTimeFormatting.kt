package com.skysoft.utils

import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

private val localTimeFormatters = ConcurrentHashMap<String, DateTimeFormatter>()

internal fun formatLocalTime(time: LocalTime, pattern: String): String =
    localTimeFormatters.computeIfAbsent(pattern) { DateTimeFormatter.ofPattern(it, Locale.ENGLISH) }.format(time)
