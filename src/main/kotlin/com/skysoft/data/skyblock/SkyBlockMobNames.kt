package com.skysoft.data.skyblock

internal fun String.withoutSkyBlockMobModifierPrefix(ignoreCase: Boolean = false): String {
    val prefix = SKYBLOCK_MOB_MODIFIER_PREFIXES.firstOrNull { startsWith("$it ", ignoreCase) } ?: return this
    return substring(prefix.length + 1)
}

private val SKYBLOCK_MOB_MODIFIER_PREFIXES =
    setOf("Empyrean", "Exalted", "Runic", "Venerable", "Stalwart", "Blessed")
