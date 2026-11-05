package com.skysoft.features.event.diana

internal object DianaDugMobParser {
    fun parse(message: String): String? {
        if (message.trimStart().startsWith(RARE_DROP_PREFIX, ignoreCase = true)) return null
        val label = dugOutPattern.find(message)
            ?.groups
            ?.get("mob")
            ?.value
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?: return null
        if (label.any(Char::isDigit)) return null
        if (ignoredLabels.any { label.equals(it, ignoreCase = true) }) return null
        if (ignoredWords.any { label.contains(it, ignoreCase = true) }) return null
        return label
    }

    private const val RARE_DROP_PREFIX = "RARE DROP!"
    private val ignoredLabels = setOf("Griffin Burrow")
    private val ignoredWords = setOf("coin")
    private val dugOutPattern = Regex("""You dug out (?:a |an )?(?<mob>[^!§(]+)!""", RegexOption.IGNORE_CASE)
}
