package com.skysoft.features.combat

internal data class CocoonMessage(
    val mobName: String,
    val marker: String,
)

internal object CocoonMessageParser {
    fun parseLocal(message: String): CocoonMessage? {
        val match = localCocoonPattern.matchEntire(message) ?: return null
        val mobName = match.groups["mob"]?.value?.trim().orEmpty()
        if (mobName.isEmpty()) return null
        return CocoonMessage(mobName, match.value)
    }

    private val localCocoonPattern = Regex(
        """^CAUGHT! You cocooned an? (?<mob>[^!]+)!$""",
        RegexOption.IGNORE_CASE,
    )
}
