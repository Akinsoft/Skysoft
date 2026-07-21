package com.skysoft.features.profit

import com.google.gson.Gson
import com.skysoft.data.skyblock.SkyBlockSlayerType

internal object ProfitTrackerPresets {
    private val presets: Map<ProfitTrackerPreset, ProfitPreset> by lazy(::load)

    fun get(type: ProfitTrackerPreset): ProfitPreset = requireNotNull(presets[type]) {
        "Profit Tracker is missing the ${type.name} preset"
    }

    fun forLocation(island: String?, area: String?): ProfitTrackerPreset? {
        if (island == null) return null
        return presets.entries.singleOrNull { (_, preset) ->
            island in preset.islands && (preset.areas.isEmpty() || area in preset.areas)
        }?.key
    }

    private fun load(): Map<ProfitTrackerPreset, ProfitPreset> {
        val stream = requireNotNull(ProfitTrackerPresets::class.java.getResourceAsStream(PRESET_RESOURCE)) {
            "Profit Tracker presets resource is missing"
        }
        val resource = stream.bufferedReader().use { reader -> Gson().fromJson(reader, PresetResource::class.java) }
        return ProfitTrackerPreset.entries.associateWith { type ->
            val data = requireNotNull(
                type.slayerType?.let { resource.slayer[it.name] } ?: resource.farming,
            ) { "Profit Tracker is missing the ${type.name} preset" }
            ProfitPreset(
                islands = data.islands.filter(String::isNotBlank).toSet().also { require(it.isNotEmpty()) },
                areas = data.areas.filter(String::isNotBlank).toSet(),
                additionalItems = data.items.filter(String::isNotBlank).toSet(),
            )
        }
    }

    private data class PresetResource(
        val slayer: Map<String, PresetData> = emptyMap(),
        val farming: PresetData? = null,
    )

    private data class PresetData(
        val islands: List<String> = emptyList(),
        val areas: List<String> = emptyList(),
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
    ;

    companion object {
        fun fromSlayer(type: SkyBlockSlayerType): ProfitTrackerPreset = valueOf(type.name)
    }
}

internal data class ProfitPreset(
    val islands: Set<String>,
    val areas: Set<String>,
    val additionalItems: Set<String>,
)

private const val PRESET_RESOURCE = "/assets/skysoft/data/profit_tracker_presets.json"
