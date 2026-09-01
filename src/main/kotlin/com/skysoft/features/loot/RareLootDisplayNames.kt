package com.skysoft.features.loot

import com.skysoft.data.skyblock.pets.PetRepository
import com.skysoft.utils.NumberUtilities.romanNumeral
import com.skysoft.utils.TextUtilities.cleanSkyBlockText
import java.util.Locale

internal object RareLootDisplayNames {
    private val enchantmentPattern = Regex("""^ENCHANTMENT_(?:ULTIMATE_)?(?<name>.+)_(?<level>\d+)$""")

    fun resolve(itemId: String?, fallback: String): String {
        val cleanFallback = fallback.cleanSkyBlockText().takeIf { it.isNotBlank() } ?: "Unknown Drop"
        val cleanItemId = itemId?.trim()?.takeIf { it.isNotEmpty() } ?: return cleanFallback
        enchantmentDisplayName(cleanItemId)?.let { return it }
        return PetRepository.itemName(cleanItemId)
            ?.cleanSkyBlockText()
            ?.takeIf { it.isNotBlank() }
            ?: cleanItemId.genericDisplayName()
    }

    private fun enchantmentDisplayName(itemId: String): String? {
        val match = enchantmentPattern.matchEntire(itemId) ?: return null
        val name = match.groups["name"]?.value?.genericDisplayName() ?: return null
        val level = match.groups["level"]?.value?.toIntOrNull() ?: return name
        return "$name ${level.romanNumeral()}"
    }

    private fun String.genericDisplayName(): String =
        replace(';', '_')
            .replace('_', ' ')
            .replace('-', ' ')
            .lowercase(Locale.US)
            .split(Regex("""\s+"""))
            .filter { it.isNotBlank() }
            .joinToString(" ") { word -> word.replaceFirstChar { it.titlecase(Locale.US) } }

}
