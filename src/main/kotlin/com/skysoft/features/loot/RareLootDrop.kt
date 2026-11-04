package com.skysoft.features.loot

import com.skysoft.data.skyblock.SkyBlockItemNames
import com.skysoft.utils.NumberUtilities.romanNumeral
import com.skysoft.utils.RegexUtilities.whitespacePattern
import com.skysoft.utils.TextUtilities.cleanSkyBlockText
import java.util.Locale

internal data class RareLootDrop(
    val itemId: String?,
    val displayName: String,
    val amount: Int = 1,
    val context: String? = null,
)

internal fun RareLootChatDrop.toRareLootDrop(): RareLootDrop {
    val resolvedItemId = bestItemId()
    return RareLootDrop(
        itemId = resolvedItemId,
        displayName = rareLootDisplayName(resolvedItemId, displayName),
        amount = amount,
        context = context,
    )
}

internal fun RareLootChatDrop.bestItemId(): String? {
    val candidates = buildList {
        addAll(itemIdCandidates)
        itemIdFromDisplayName(displayName)?.let(::add)
        SkyBlockItemNames.resolveItemId(displayName)?.let(::add)
    }.distinct()
    return candidates.firstOrNull { itemId ->
        RareLootValueResolver.resolve(itemId, amount) != null
    } ?: candidates.firstOrNull()
}

internal fun String.titleCaseWords(): String =
    lowercase(Locale.US)
        .split(whitespacePattern)
        .filter { it.isNotBlank() }
        .joinToString(" ") { word -> word.replaceFirstChar { it.titlecase(Locale.US) } }

internal fun String.toItemIdKey(): String =
    uppercase(Locale.US)
        .replace(nonItemIdCharactersPattern, "_")
        .trim('_')

internal fun String.trimParentheses(): String =
    trim().removePrefix("(").removeSuffix(")").trim()

private fun itemIdFromDisplayName(displayName: String): String? =
    displayName
        .replace(formattingCodePattern, "")
        .toItemIdKey()
        .takeIf { it.isNotBlank() }
        ?.let { itemId -> displayNameItemIdOverrides[itemId] ?: itemId }

private fun rareLootDisplayName(itemId: String?, fallback: String): String {
    val cleanFallback = fallback.cleanSkyBlockText().takeIf { it.isNotBlank() } ?: "Unknown Drop"
    val cleanItemId = itemId?.trim()?.takeIf { it.isNotEmpty() } ?: return cleanFallback
    enchantmentDisplayName(cleanItemId)?.let { return it }
    return SkyBlockItemNames.displayName(cleanItemId)
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
        .titleCaseWords()

private val nonItemIdCharactersPattern = Regex("""[^A-Z0-9]+""")
private val formattingCodePattern = Regex("""§.""")
private val enchantmentPattern = Regex("""^ENCHANTMENT_(?:ULTIMATE_)?(?<name>.+)_(?<level>\d+)$""")
private val displayNameItemIdOverrides = mapOf(
    "CARROT" to "CARROT_ITEM",
    "CARROTS" to "CARROT_ITEM",
    "POTATO" to "POTATO_ITEM",
    "POTATOES" to "POTATO_ITEM",
)
