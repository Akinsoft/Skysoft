package com.skysoft.features.profit

import com.skysoft.data.skyblock.ItemListEntryKind
import com.skysoft.data.skyblock.SkyBlockDataRepository
import com.skysoft.data.skyblock.SkyBlockRecipe
import com.skysoft.data.skyblock.SkyBlockSlayerType

internal class ProfitDropCatalog {
    private var dropCatalogVersion = -1L
    private var trackedItems = emptyMap<ProfitTrackerPreset, Set<String>>()

    fun items(preset: ProfitTrackerPreset?): Set<String> {
        if (dropCatalogVersion != SkyBlockDataRepository.snapshotVersion) rebuildDropCatalog()
        return trackedItems[preset].orEmpty()
    }

    private fun rebuildDropCatalog() {
        trackedItems = ProfitTrackerPreset.entries.associateWith { preset ->
            val slayerType = preset.slayerType
            val directDrops = SkyBlockDataRepository.entries.asSequence()
                .filter { entry -> entry.key.kind == ItemListEntryKind.SKYBLOCK }
                .filter { entry ->
                    SkyBlockDataRepository.info(entry.key)?.dropSources.orEmpty().any { source ->
                        val entityType = SkyBlockDataRepository.entity(source.entityId)?.type
                        when (preset) {
                            ProfitTrackerPreset.FISHING ->
                                entityType.equals(SEA_CREATURE_ENTITY_TYPE, ignoreCase = true)
                            ProfitTrackerPreset.MYTHOLOGICAL_RITUAL ->
                                entityType.equals(MYTHOLOGICAL_CREATURE_ENTITY_TYPE, ignoreCase = true)
                            else ->
                                slayerType != null &&
                                    SkyBlockSlayerType.fromBossEntityId(source.entityId)?.first == slayerType
                        }
                    }
                }
                .map { entry -> entry.key.id }
                .toSet()
            val presetItems = directDrops + ProfitTrackerPresets.get(preset).additionalItems
            val compactedDrops = SkyBlockDataRepository.entries.asSequence()
                .filter { entry -> entry.key.kind == ItemListEntryKind.SKYBLOCK }
                .filter { entry ->
                    SkyBlockDataRepository.recipesFor(entry.key)
                        .filterIsInstance<SkyBlockRecipe.Crafting>()
                        .any { recipe -> recipe.ingredients.map { it.id }.distinct().singleOrNull() in presetItems }
                }
                .map { entry -> entry.key.id }
            presetItems + compactedDrops
        }
        dropCatalogVersion = SkyBlockDataRepository.snapshotVersion
    }

}

private const val SEA_CREATURE_ENTITY_TYPE = "Sea Creature"

private const val MYTHOLOGICAL_CREATURE_ENTITY_TYPE = "Mythological Creature"
