package com.skysoft.utils

object RegexUtilities {
    val whitespacePattern = Regex("\\s+")

    fun MatchResult.group(name: String): String =
        groups[name]?.value.orEmpty()

    fun MatchResult.groupOrNull(name: String): String? =
        runCatching { groups[name]?.value }.getOrNull()

    fun MatchResult.worldVecOrNull(): WorldVec? =
        WorldVec(
            groups["x"]?.value?.toDoubleOrNull() ?: return null,
            groups["y"]?.value?.toDoubleOrNull() ?: return null,
            groups["z"]?.value?.toDoubleOrNull() ?: return null,
        )
}
