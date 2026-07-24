package com.skysoft.features.profit

import com.google.gson.Gson
import com.skysoft.data.skyblock.SkyBlockSlayerType

internal object ProfitTrackerPresets {
    private val presets: Map<ProfitTrackerPreset, ProfitPreset> by lazy(::load)

    fun get(type: ProfitTrackerPreset): ProfitPreset = requireNotNull(presets[type]) {
        "Profit Tracker is missing the ${type.name} preset"
    }

    fun forLocation(
        island: String?,
        area: String?,
        preferred: ProfitTrackerPreset? = null,
    ): ProfitTrackerPreset? {
        if (island == null) return null
        val matches = presets.filterValues { preset ->
            island in preset.islands &&
                (preset.areas.isEmpty() || area in preset.areas) &&
                (preset.islandAreas[island]?.let { area in it } != false)
        }
        return preferred?.takeIf(matches::containsKey)
            ?: matches.keys.singleOrNull { it.slayerType == null }
            ?: matches.keys.singleOrNull()
    }

    private fun load(): Map<ProfitTrackerPreset, ProfitPreset> {
        val stream = requireNotNull(ProfitTrackerPresets::class.java.getResourceAsStream(PRESET_RESOURCE)) {
            "Profit Tracker presets resource is missing"
        }
        val resource = stream.bufferedReader().use { reader -> Gson().fromJson(reader, PresetResource::class.java) }
        return ProfitTrackerPreset.entries.associateWith { type ->
            val data = requireNotNull(resource.data(type)) { "Profit Tracker is missing the ${type.name} preset" }
            ProfitPreset(
                islands = data.islands.filter(String::isNotBlank).toSet().also { require(it.isNotEmpty()) },
                areas = data.areas.filter(String::isNotBlank).toSet(),
                islandAreas = data.islandAreas.mapValues { (_, areas) -> areas.filter(String::isNotBlank).toSet() },
                additionalItems = data.items.filter(String::isNotBlank).toSet(),
            )
        }
    }

    private data class PresetResource(
        val slayer: Map<String, PresetData> = emptyMap(),
        val farming: PresetData? = null,
        val foraging: PresetData? = null,
        val mining: PresetData? = null,
    ) {
        fun data(type: ProfitTrackerPreset): PresetData? = when (type) {
            ProfitTrackerPreset.FARMING -> farming
            ProfitTrackerPreset.FORAGING -> foraging
            ProfitTrackerPreset.MINING -> mining
            else -> type.slayerType?.let { slayer[it.name] }
        }
    }

    private data class PresetData(
        val islands: List<String> = emptyList(),
        val areas: List<String> = emptyList(),
        val islandAreas: Map<String, List<String>> = emptyMap(),
        val items: List<String> = emptyList(),
    )
}

internal enum class ProfitTrackerPreset(
    val displayName: String,
    val slayerType: SkyBlockSlayerType? = null,
    val coinLabel: String = "Mob Kill Coins",
    val actionLabel: String = "Bosses Killed",
) {
    ZOMBIE("Zombie Slayer", SkyBlockSlayerType.ZOMBIE),
    SPIDER("Spider Slayer", SkyBlockSlayerType.SPIDER),
    WOLF("Wolf Slayer", SkyBlockSlayerType.WOLF),
    ENDERMAN("Enderman Slayer", SkyBlockSlayerType.ENDERMAN),
    BLAZE("Blaze Slayer", SkyBlockSlayerType.BLAZE),
    VAMPIRE("Vampire Slayer", SkyBlockSlayerType.VAMPIRE),
    FARMING("Farming", coinLabel = "Bountiful Coins", actionLabel = "Pests Vacuumed"),
    FORAGING("Foraging"),
    MINING("Mining"),
    ;

    companion object {
        fun fromSlayer(type: SkyBlockSlayerType): ProfitTrackerPreset = valueOf(type.name)
    }
}

internal data class ProfitPreset(
    val islands: Set<String>,
    val areas: Set<String>,
    val islandAreas: Map<String, Set<String>>,
    val additionalItems: Set<String>,
)

private const val PRESET_RESOURCE = "/assets/skysoft/data/profit_tracker_presets.json"
