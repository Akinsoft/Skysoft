package com.skysoft.features.misc.bettertab

import com.skysoft.config.BetterTabPlayerSortMode
import com.skysoft.config.BetterTabProfileType
import com.skysoft.data.hypixel.TabListEntry

internal data class BetterTabPlayerSort(
    val mode: BetterTabPlayerSortMode,
    val profileTypeOrder: List<BetterTabProfileType>,
    val isHighestLevelFirst: Boolean,
    val isSelfPinned: Boolean = false,
    val selfPlayerName: String? = null,
)

internal object BetterTabPlayerSorter {
    fun sort(entries: List<TabListEntry>, rowsPerColumn: Int, sort: BetterTabPlayerSort): List<TabListEntry> {
        if (sort.mode == BetterTabPlayerSortMode.HYPIXEL) return entries
        val slots = playerSlots(entries, rowsPerColumn)
        if (slots.size < 2) return entries
        val sorted = slots.map { PlayerKey(entries[it], sort) }.sortedWith(comparator(sort))
        return entries.toMutableList().also { result ->
            slots.forEachIndexed { index, slot -> result[slot] = sorted[index].entry }
        }
    }

    private fun playerSlots(entries: List<TabListEntry>, rowsPerColumn: Int): List<Int> =
        (entries.indices step rowsPerColumn)
            .filter { start -> playerColumnPattern.matches(entries[start].cleanDisplayName) }
            .flatMap { start -> (start + 1) until minOf(start + rowsPerColumn, entries.size) }
            .filter { entries[it].skyBlockPlayerName != null }

    private fun comparator(sort: BetterTabPlayerSort): Comparator<PlayerKey> {
        val byLevel = compareBy<PlayerKey> { key ->
            key.level?.let { if (sort.isHighestLevelFirst) -it else it }
        }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }
        val ordered = if (sort.mode != BetterTabPlayerSortMode.PROFILE_TYPE) {
            byLevel
        } else {
            compareBy<PlayerKey> { key ->
                sort.profileTypeOrder.indexOf(key.profileType).takeIf { it >= 0 } ?: sort.profileTypeOrder.size
            }.then(byLevel)
        }
        if (!sort.isSelfPinned) return ordered
        return compareByDescending<PlayerKey> { it.isSelf }.then(ordered)
    }

    private class PlayerKey(val entry: TabListEntry, sort: BetterTabPlayerSort) {
        val name: String = entry.skyBlockPlayerName.orEmpty()
        val level: Int? = entry.skyBlockLevel
        val isSelf: Boolean = sort.selfPlayerName?.equals(name, ignoreCase = true) == true
        val profileType: BetterTabProfileType = BetterTabProfileType.entries.firstOrNull { type ->
            type.badge != null && type.badge in entry.cleanDisplayName
        } ?: BetterTabProfileType.NORMAL
    }
}
