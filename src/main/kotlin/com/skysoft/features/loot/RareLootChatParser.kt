package com.skysoft.features.loot

import com.skysoft.data.skyblock.SkyBlockStatGlyph
import com.skysoft.utils.NumberUtilities.romanNumeral
import com.skysoft.utils.NumberUtilities.romanToDecimal
import com.skysoft.utils.RegexUtilities.group
import java.util.Locale

internal object RareLootChatParser {
    fun parse(message: String): RareLootChatDrop? {
        val clean = message.trim()
        if (clean.startsWith(PARTY_PREFIX)) return null
        dugOutPattern.matchEntire(clean)?.let { return chatDrop(it.group("drop"), context = null) }
        val directDrop = directRareDropPattern.matchEntire(clean) ?: return null
        val body = directDrop.group("drop").trim()
        val contextMatch = contextPattern.matchEntire(body) ?: return chatDrop(body, context = null)
        return chatDrop(contextMatch.group("drop").trim(), contextMatch.group("context").normalizeContext())
    }

    private fun chatDrop(dropText: String, context: String?): RareLootChatDrop? {
        val drop = parseDrop(cleanDropName(dropText) ?: return null)
        return RareLootChatDrop(drop.displayName, drop.amount, context, drop.itemIdCandidates)
    }

    private fun cleanDropName(text: String): String? {
        val clean = text.trim()
            .removeSuffix("!")
            .trim()
            .removeSurrounding("(", ")")
            .trim()
            .removePrefix("a ")
            .removePrefix("an ")
            .trim()
        if (clean.isBlank()) return null
        if (clean.equals("Griffin Burrow", ignoreCase = true)) return null
        if (clean.contains("coin", ignoreCase = true)) return null
        return clean
    }

    private fun parseDrop(name: String): ParsedChatDrop {
        val amountDrop = parseAmount(name)
        return parseEnchantedBook(amountDrop.displayName)?.copy(amount = amountDrop.amount) ?: amountDrop
    }

    private fun parseAmount(name: String): ParsedChatDrop {
        val match = amountPattern.matchEntire(name)
        val amount = match?.group("amount")?.toIntOrNull()?.coerceAtLeast(1)
        val displayName = match?.group("drop")?.trim()?.takeIf { it.isNotEmpty() }
        return if (amount != null && displayName != null) ParsedChatDrop(displayName, amount) else ParsedChatDrop(name, 1)
    }

    private fun parseEnchantedBook(name: String): ParsedChatDrop? {
        val match = enchantedBookPattern.matchEntire(name) ?: return null
        val enchantName = match.groups["enchant"]?.value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val level = match.groups["level"]?.value?.parseEnchantLevel() ?: return null
        val enchantKey = enchantName.toItemIdKey()
        return ParsedChatDrop(
            displayName = "${enchantName.titleCaseWords()} ${level.romanNumeral()}",
            amount = 1,
            itemIdCandidates = listOf(
                "ENCHANTMENT_${enchantKey}_$level",
                "ENCHANTMENT_ULTIMATE_${enchantKey}_$level",
            ).distinct(),
        )
    }

    private fun String.normalizeContext(): String =
        trimParentheses()
            .let(SkyBlockStatGlyph::normalizeForRendering)
            .replace(magicFindPattern, "MF")

    private fun String.parseEnchantLevel(): Int? =
        toIntOrNull() ?: uppercase(Locale.US).romanToDecimal().takeIf { it > 0 }

    private const val PARTY_PREFIX = "Party >"
    private val dugOutPattern = Regex(
        """^(?:(?:(?:VERY|CRAZY)\s+)?RARE DROP!\s+|Wow!\s+)?You dug out(?: an? )?(?<drop>.+?)!(?: .*)?$""",
        RegexOption.IGNORE_CASE,
    )
    private val directRareDropPattern = Regex(
        """^(?:(?:VERY|CRAZY)\s+)?RARE DROP!\s+(?<drop>.+)$""",
        RegexOption.IGNORE_CASE,
    )
    private val contextPattern = Regex(
        """^(?<drop>.+?)\s+(?<context>\([^)]*(?:Magic Find|MF)[^)]*\))(?:\s+.*)?$""",
        RegexOption.IGNORE_CASE,
    )
    private val magicFindPattern = Regex("""Magic Find""", RegexOption.IGNORE_CASE)
    private val amountPattern = Regex("""^(?<amount>\d+)\s*x\s+(?<drop>.+)$""", RegexOption.IGNORE_CASE)
    private val enchantedBookPattern = Regex("""^Enchanted Book \((?<enchant>.+?) (?<level>\d+|[IVXLCDM]+)\)$""", RegexOption.IGNORE_CASE)
}

internal data class RareLootChatDrop(
    val displayName: String,
    val amount: Int,
    val context: String?,
    val itemIdCandidates: List<String> = emptyList(),
)

private data class ParsedChatDrop(
    val displayName: String,
    val amount: Int,
    val itemIdCandidates: List<String> = emptyList(),
)
