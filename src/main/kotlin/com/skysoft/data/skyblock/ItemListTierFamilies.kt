package com.skysoft.data.skyblock

import java.util.Locale

internal object ItemListTierFamilies {
    fun build(entries: List<ItemListEntry>): TierFamilyIndex {
        val candidates = entries.mapNotNull(::candidate)
        val families = candidates.groupBy(TierCandidate::familyId).mapNotNull { (familyId, matches) ->
            if (matches.size < 2) return@mapNotNull null
            val ordered = matches.sortedBy(TierCandidate::tier)
            val first = ordered.first()
            familyId to ItemListTierFamily(
                id = familyId,
                displayName = first.familyName,
                kind = first.kind,
                tiers = ordered.map { it.entry.key },
            )
        }.toMap()
        val byItem = families.values.flatMap { family -> family.tiers.map { it to family.id } }.toMap()
        return TierFamilyIndex(families, byItem)
    }

    fun groupedEntries(
        entries: List<ItemListEntry>,
        index: TierFamilyIndex,
        entriesByKey: Map<ItemListEntryKey, ItemListEntry>,
    ): List<ItemListEntry> {
        val emittedFamilies = mutableSetOf<String>()
        return entries.mapNotNull { entry ->
            val familyId = index.byItem[entry.key] ?: return@mapNotNull entry
            if (!emittedFamilies.add(familyId)) return@mapNotNull null
            val family = index.families.getValue(familyId)
            val representative = entriesByKey[family.tiers.first()]
                ?: return@mapNotNull null
            representative.copy(
                displayName = family.displayName,
                searchableText = buildString {
                    append(family.displayName).append(' ')
                    family.tiers.forEach { append(it.id).append(' ') }
                }.lowercase(Locale.ROOT),
            )
        }
    }

    private fun candidate(entry: ItemListEntry): TierCandidate? {
        if (entry.key.kind != ItemListEntryKind.SKYBLOCK) return null
        return tierPatterns.firstNotNullOfOrNull { (kind, pattern) ->
            pattern.matchEntire(entry.key.id)?.let { match ->
                TierCandidate(
                    entry,
                    "${kind.name.lowercase()}:${match.groupValues[1]}",
                    entry.displayName.removeTierSuffix(),
                    kind,
                    match.groupValues[2].toInt(),
                )
            }
        }
    }

    private fun String.removeTierSuffix(): String = replace(tierSuffixPattern, "").trim()

    private data class TierCandidate(
        val entry: ItemListEntry,
        val familyId: String,
        val familyName: String,
        val kind: ItemListTierFamilyKind,
        val tier: Int,
    )

    private val tierPatterns = listOf(
        ItemListTierFamilyKind.MINION to Regex("(.+)_GENERATOR_([0-9]+)"),
        ItemListTierFamilyKind.ENCHANTMENT to Regex("ENCHANTMENT_(.+)_([0-9]+)"),
    )
    private val tierSuffixPattern = Regex(" (?:[IVXLCDM]+|[0-9]+)$")
}

internal data class TierFamilyIndex(
    val families: Map<String, ItemListTierFamily>,
    val byItem: Map<ItemListEntryKey, String>,
)
