package com.skysoft.config

import com.google.gson.annotations.Expose
import com.skysoft.data.SkyBlockIsland
import java.util.Locale
import java.util.UUID

class CustomProfitTrackerConfig(
    @JvmField @field:Expose var id: String = UUID.randomUUID().toString(),
    @JvmField @field:Expose var name: String = DEFAULT_CUSTOM_TRACKER_NAME,
    @JvmField @field:Expose val locations: CustomProfitTrackerLocations = CustomProfitTrackerLocations(),
    @JvmField @field:Expose val items: MutableList<String> = mutableListOf(),
    @JvmField @field:Expose val priceSources: MutableMap<String, String> = mutableMapOf(),
    @JvmField @field:Expose var trackCoins: Boolean = false,
    @JvmField @field:Expose val config: ProfitTrackerConfig = ProfitTrackerConfig(RESOURCE_TRACKER_SUMMARY_LINES),
) {
    val storageKey: String
        get() = "$CUSTOM_TRACKER_STORAGE_PREFIX$id"

    fun matches(island: SkyBlockIsland?, area: String?): Boolean {
        if (!config.enabled || island == null) return false
        if (locations.anyIsland) return true
        val location = locations.entries.firstOrNull { it.island == island.name } ?: return false
        return location.areas.isEmpty() || area in location.areas
    }

    internal fun repairLoadedValues(usedIds: MutableSet<String>, usedNames: MutableSet<String>) {
        if (id.isBlank() || !usedIds.add(id)) {
            id = generateSequence { UUID.randomUUID().toString() }.first(usedIds::add)
        }
        name = uniqueCustomTrackerName(name, usedNames)
        locations.repairLoadedValues()
        repairItemIds(items)
        val validSources = ProfitTrackerPriceSource.entries.mapTo(mutableSetOf()) { it.name }
        priceSources.entries.removeIf { (itemId, source) -> itemId !in items || source !in validSources }
    }
}

class CustomProfitTrackerLocations(
    @JvmField @field:Expose var anyIsland: Boolean = false,
    @JvmField @field:Expose val entries: MutableList<CustomProfitTrackerLocation> = mutableListOf(),
) {
    internal fun repairLoadedValues() {
        val repaired = linkedMapOf<String, MutableList<String>>()
        entries.forEach { location ->
            val island = SkyBlockIsland.entries.firstOrNull { it.name == location.island } ?: return@forEach
            repaired.getOrPut(island.name, ::mutableListOf).addAll(location.areas)
        }
        entries.clear()
        repaired.forEach { (island, areas) ->
            repairAreaNames(areas)
            entries += CustomProfitTrackerLocation(island, areas)
        }
    }
}

class CustomProfitTrackerLocation(
    @JvmField @field:Expose var island: String = SkyBlockIsland.HUB.name,
    @JvmField @field:Expose val areas: MutableList<String> = mutableListOf(),
)

internal fun normalizedCustomTrackerName(name: String): String =
    name.replace(LEGACY_FORMATTING_PATTERN, "")
        .filterNot(Char::isISOControl)
        .trim()
        .ifBlank { DEFAULT_CUSTOM_TRACKER_NAME }
        .take(CUSTOM_TRACKER_NAME_LENGTH)

private fun uniqueCustomTrackerName(name: String, usedNames: MutableSet<String>): String {
    val base = normalizedCustomTrackerName(name)
    var candidate = base
    var suffix = 2
    while (!usedNames.add(candidate.lowercase(Locale.ROOT))) {
        val ending = " $suffix"
        candidate = base.take(CUSTOM_TRACKER_NAME_LENGTH - ending.length) + ending
        suffix++
    }
    return candidate
}

private fun repairItemIds(items: MutableList<String>) {
    val repaired = items.asSequence().map(String::trim).filter(String::isNotEmpty).distinct().toList()
    items.clear()
    items.addAll(repaired)
}

private fun repairAreaNames(areas: MutableList<String>) {
    val repaired = areas.asSequence().map(String::trim).filter(String::isNotEmpty).distinct().toList()
    areas.clear()
    areas.addAll(repaired)
}

internal const val CUSTOM_TRACKER_STORAGE_PREFIX = "CUSTOM_"
internal const val CUSTOM_TRACKER_NAME_LENGTH = 32
private const val DEFAULT_CUSTOM_TRACKER_NAME = "Custom Tracker"
private val LEGACY_FORMATTING_PATTERN = Regex("§.")
