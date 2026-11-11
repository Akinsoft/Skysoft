package com.skysoft.features.misc.bettertab

import com.skysoft.config.BetterTabPlayerSortMode
import com.skysoft.config.BetterTabProfileType
import com.skysoft.data.hypixel.TabListEntry

internal data class BetterTabPlayerSort(
    val mode: BetterTabPlayerSortMode,
    val profileTypeOrder: List<BetterTabProfileType>,
    val isHighestLevelFirst: Boolean,
)

internal object BetterTabPlayerSorter {
    private val playerColumnTitlePattern = Regex("Players \\(\\d+\\)")
    private val levelPattern = Regex("^\\[(?<level>\\d+)]")

    fun sort(entries: List<TabListEntry>, rowsPerColumn: Int, sort: BetterTabPlayerSort): List<TabListEntry> {
        if (sort.mode == BetterTabPlayerSortMode.HYPIXEL) return entries
        val slots = playerSlots(entries, rowsPerColumn)
        if (slots.size < 2) return entries
        val sorted = slots.map { PlayerKey(entries[it]) }.sortedWith(comparator(sort))
        return entries.toMutableList().also { result ->
            slots.forEachIndexed { index, slot -> result[slot] = sorted[index].entry }
        }
    }

    private fun playerSlots(entries: List<TabListEntry>, rowsPerColumn: Int): List<Int> =
        (entries.indices step rowsPerColumn)
            .filter { start -> playerColumnTitlePattern.matches(entries[start].cleanDisplayName) }
            .flatMap { start -> (start + 1) until minOf(start + rowsPerColumn, entries.size) }
            .filter { entries[it].skyBlockPlayerName != null }

    private fun comparator(sort: BetterTabPlayerSort): Comparator<PlayerKey> {
        val byLevel = compareBy<PlayerKey> { it.level == null }
            .thenBy { key -> key.level?.let { if (sort.isHighestLevelFirst) -it else it } }
            .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }
        if (sort.mode != BetterTabPlayerSortMode.PROFILE_TYPE) return byLevel
        // Groups are not given heading rows; per-group headings such as "Ironman (3)" could be inserted
        // in BetterTabLayoutBuilder.parseEntryBlocks by detecting profile type changes between rows.
        return compareBy<PlayerKey> { key ->
            sort.profileTypeOrder.indexOf(key.profileType).takeIf { it >= 0 } ?: sort.profileTypeOrder.size
        }.then(byLevel)
    }

    private class PlayerKey(val entry: TabListEntry) {
        val name: String = entry.skyBlockPlayerName.orEmpty()
        val level: Int? = levelPattern.find(entry.cleanDisplayName)?.groups?.get("level")?.value?.toIntOrNull()
        val profileType: BetterTabProfileType = BetterTabProfileType.entries.firstOrNull { type ->
            type.badge != null && type.badge in entry.cleanDisplayName
        } ?: BetterTabProfileType.NORMAL
    }
}
