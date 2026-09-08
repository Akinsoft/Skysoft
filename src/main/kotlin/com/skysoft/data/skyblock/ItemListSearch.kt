package com.skysoft.data.skyblock

import com.skysoft.utils.TextUtilities.removeColor
import java.util.Locale

object ItemListSearch {
    fun filter(entries: List<ItemListEntry>, query: String): List<ItemListEntry> {
        val tokens = query.trim().lowercase(Locale.ROOT).split(whitespace).filter(String::isNotBlank)
        if (tokens.isEmpty()) return entries
        return entries.asSequence()
            .filter { entry -> tokens.all { token -> matches(entry, token) } }
            .sortedWith(compareBy<ItemListEntry> { rank(it, tokens) }.thenBy { it.displayName.lowercase(Locale.ROOT) })
            .toList()
    }

    private fun matches(entry: ItemListEntry, token: String): Boolean = when {
        token.startsWith('@') -> token.drop(1) in entry.source.lowercase(Locale.ROOT)
        token.startsWith('#') -> entry.tags.any { token.drop(1) in it.lowercase(Locale.ROOT) }
        else -> token in entry.searchableText
    }

    private fun rank(entry: ItemListEntry, tokens: List<String>): Int {
        val plain = tokens.filterNot { it.startsWith('@') || it.startsWith('#') }
        if (plain.isEmpty()) return 2
        val name = entry.displayName.lowercase(Locale.ROOT)
        return when {
            plain.all { name == it } -> 0
            plain.all { name.startsWith(it) || " $it" in name } -> 1
            else -> 2
        }
    }

    private val whitespace = Regex("\\s+")
}

internal fun itemListSearchableText(displayName: String, id: String, lore: List<String>): String =
    buildString {
        append(displayName).append(' ').append(id).append(' ')
        lore.forEach { append(it.removeColor()).append(' ') }
    }.lowercase(Locale.ROOT)
