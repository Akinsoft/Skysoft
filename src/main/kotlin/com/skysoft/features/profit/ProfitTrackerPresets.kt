package com.skysoft.features.profit

import com.google.gson.Gson
import com.skysoft.data.skyblock.SkyBlockSlayerType

internal object ProfitTrackerPresets {
    private val presets: Map<SkyBlockSlayerType, SlayerProfitPreset> by lazy(::load)

    fun slayer(type: SkyBlockSlayerType): SlayerProfitPreset = requireNotNull(presets[type]) {
        "Profit Tracker is missing the ${type.name} Slayer preset"
    }

    fun slayerForLocation(island: String?, area: String?): SkyBlockSlayerType? {
        if (island == null || area == null) return null
        return presets.entries.singleOrNull { (_, preset) -> island in preset.islands && area in preset.areas }?.key
    }

    private fun load(): Map<SkyBlockSlayerType, SlayerProfitPreset> {
        val stream = requireNotNull(ProfitTrackerPresets::class.java.getResourceAsStream(PRESET_RESOURCE)) {
            "Profit Tracker presets resource is missing"
        }
        val resource = stream.bufferedReader().use { reader -> Gson().fromJson(reader, PresetResource::class.java) }
        return SkyBlockSlayerType.entries.associateWith { type ->
            val preset = requireNotNull(resource.slayer[type.name]) {
                "Profit Tracker is missing the ${type.name} Slayer preset"
            }
            SlayerProfitPreset(
                islands = preset.islands.filter(String::isNotBlank).toSet().also { require(it.isNotEmpty()) },
                areas = preset.areas.filter(String::isNotBlank).toSet().also { require(it.isNotEmpty()) },
                additionalItems = preset.items.filter(String::isNotBlank).toSet(),
            )
        }
    }

    private data class PresetResource(val slayer: Map<String, PresetData> = emptyMap())
    private data class PresetData(
        val islands: List<String> = emptyList(),
        val areas: List<String> = emptyList(),
        val items: List<String> = emptyList(),
    )
}

internal data class SlayerProfitPreset(
    val islands: Set<String>,
    val areas: Set<String>,
    val additionalItems: Set<String>,
)

private const val PRESET_RESOURCE = "/assets/skysoft/data/profit_tracker_presets.json"
