package com.skysoft.data.hypixel

import com.skysoft.utils.TextUtilities.cleanSkyBlockText
import java.time.Instant
import java.time.ZoneOffset
import java.time.ZonedDateTime
import net.minecraft.network.chat.Component

enum class CookieBuffState {
    ACTIVE,
    INACTIVE,
    LOADING,
    UNKNOWN,
}

data class CookieBuffStatus(
    val state: CookieBuffState,
    val remaining: String? = null,
)

internal fun parseCookieBuffStatus(
    isTabLoaded: Boolean,
    components: List<Component>,
    footer: Component? = null,
): CookieBuffStatus {
    if (!isTabLoaded) return CookieBuffStatus(CookieBuffState.LOADING)
    parseCookieBuffLines(footer?.cookieBuffLines().orEmpty())?.let { return it }
    val lines = components.flatMap { component ->
        component.cookieBuffLines()
    }
    return parseCookieBuffLines(lines) ?: CookieBuffStatus(CookieBuffState.UNKNOWN)
}

private fun Component.cookieBuffLines(): List<String> =
    cleanSkyBlockText().lines().map(String::trim).filter(String::isNotEmpty)

private fun parseCookieBuffLines(lines: List<String>): CookieBuffStatus? {
    val header = lines.indexOfFirst { it.equals(COOKIE_BUFF_HEADER, ignoreCase = true) }
    if (header < 0) return null
    val detail = lines.getOrNull(header + 1) ?: return CookieBuffStatus(CookieBuffState.UNKNOWN)
    if (INACTIVE_COOKIE_BUFF.containsMatchIn(detail)) return CookieBuffStatus(CookieBuffState.INACTIVE)
    if (!COOKIE_DURATION.containsMatchIn(detail)) return CookieBuffStatus(CookieBuffState.UNKNOWN)
    return CookieBuffStatus(CookieBuffState.ACTIVE, detail)
}

internal fun isBoosterCookieConsumedMessage(message: String): Boolean =
    COOKIE_CONSUMED_MESSAGE.matches(message)

internal fun cookieBuffExpiryFromDuration(duration: String, now: Long): Long? {
    val matches = COOKIE_DURATION.findAll(duration).toList()
    if (matches.isEmpty()) return null
    var value = ZonedDateTime.ofInstant(Instant.ofEpochMilli(now), ZoneOffset.UTC)
    matches.forEach { match ->
        val amount = match.groupValues[1].toLongOrNull() ?: return null
        value = when (match.groupValues[2].lowercase()) {
            "year", "years" -> value.plusYears(amount)
            "month", "months" -> value.plusMonths(amount)
            "week", "weeks" -> value.plusWeeks(amount)
            "day", "days" -> value.plusDays(amount)
            "hour", "hours" -> value.plusHours(amount)
            "minute", "minutes" -> value.plusMinutes(amount)
            "second", "seconds" -> value.plusSeconds(amount)
            else -> return null
        }
    }
    return value.toInstant().toEpochMilli()
}

internal fun formatRememberedDuration(remainingMillis: Long): String {
    val totalMinutes = (remainingMillis / MILLIS_PER_MINUTE).coerceAtLeast(1L)
    val days = totalMinutes / MINUTES_PER_DAY
    val hours = totalMinutes % MINUTES_PER_DAY / MINUTES_PER_HOUR
    val minutes = totalMinutes % MINUTES_PER_HOUR
    return buildList {
        if (days > 0L) add("$days ${if (days == 1L) "Day" else "Days"}")
        if (hours > 0L) add("$hours ${if (hours == 1L) "Hour" else "Hours"}")
        if (days == 0L && minutes > 0L) add("$minutes ${if (minutes == 1L) "Minute" else "Minutes"}")
    }.joinToString(", ")
}

private const val COOKIE_BUFF_HEADER = "Cookie Buff"
private const val MILLIS_PER_MINUTE = 60_000L
private const val MINUTES_PER_HOUR = 60L
private const val HOURS_PER_DAY = 24L
private const val MINUTES_PER_DAY = HOURS_PER_DAY * MINUTES_PER_HOUR
private val INACTIVE_COOKIE_BUFF = Regex("(?:Not Active|Inactive|None)", RegexOption.IGNORE_CASE)
private val COOKIE_DURATION = Regex(
    "(\\d+)\\s+(year|month|week|day|hour|minute|second)s?",
    RegexOption.IGNORE_CASE,
)
private val COOKIE_CONSUMED_MESSAGE = Regex("^You consumed a Booster Cookie!(?: .+)?$")
