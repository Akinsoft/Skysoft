package com.skysoft.features.combat

import com.skysoft.utils.NumberUtilities.parseCompactNumberOrNull

internal data class SkyBlockMobHealth(
    val current: Long,
    val max: Long?,
)

internal object SkyBlockMobTextParser {
    fun parseHealth(text: String): SkyBlockMobHealth? =
        healthMatch(text)?.health

    fun parseName(text: String): String? {
        val match = healthMatch(text) ?: return null
        return levelPrefixPattern
            .replace(text.take(match.start).trim(), "")
            .trimStart { !it.isLetterOrDigit() }
            .trim()
            .takeIf(String::isNotEmpty)
    }

    private fun healthMatch(text: String): HealthMatch? {
        fullHealthPattern.find(text)?.let { match ->
            val current = match.groups["current"]?.value?.parseCompactNumber()
            val max = match.groups["max"]?.value?.parseCompactNumber()
            if (current != null && max != null) return HealthMatch(match.range.first, SkyBlockMobHealth(current, max))
        }
        return currentHealthPattern.find(text)?.let { match ->
            match.groups["current"]?.value?.parseCompactNumber()?.let { current ->
                HealthMatch(match.range.first, SkyBlockMobHealth(current, null))
            }
        }
    }

    private fun String.parseCompactNumber(): Long? {
        val value = replace(",", "").trim()
        if (value.isEmpty()) return null
        return value.parseCompactNumberOrNull()?.value?.toLong()
    }

    private data class HealthMatch(
        val start: Int,
        val health: SkyBlockMobHealth,
    )

    private val fullHealthPattern = Regex(
        """(?<current>[0-9,.]+[KMBkmb]?)(?:§.)?\s*/\s*(?<max>[0-9,.]+[KMBkmb]?)""",
    )
    private val currentHealthPattern = Regex("""(?<current>[0-9,.]+[KMBkmb]?)(?:§.)?❤""")
    private val levelPrefixPattern = Regex("""^\[Lv\d+]\s*""", RegexOption.IGNORE_CASE)
}
