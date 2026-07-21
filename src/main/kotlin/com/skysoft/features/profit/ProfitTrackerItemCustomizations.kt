package com.skysoft.features.profit

import com.skysoft.config.ProfitTrackerPriceSource
import com.skysoft.data.ProfileStorage
import com.skysoft.data.ProfileStorageApi

internal object ProfitTrackerItemCustomizations {
    fun data(preset: ProfitTrackerPreset): ProfileStorage.ProfitTrackerItemCustomizations? =
        ProfileStorageApi.storage.profitTracker.itemCustomizations[preset.name]

    fun isExcluded(preset: ProfitTrackerPreset, itemId: String): Boolean =
        itemId in data(preset)?.excludedItems.orEmpty()

    fun customItems(preset: ProfitTrackerPreset): Set<String> = data(preset)?.customItems.orEmpty().toSet()

    fun priceSource(preset: ProfitTrackerPreset, itemId: String): ProfitTrackerPriceSource =
        priceSourceOverride(preset, itemId) ?: presetConfig(preset).settings.priceSource

    fun priceSourceOverride(preset: ProfitTrackerPreset, itemId: String): ProfitTrackerPriceSource? =
        data(preset)?.priceSources?.get(itemId)
            ?.let { stored -> ProfitTrackerPriceSource.entries.firstOrNull { it.name == stored } }

    fun exclude(preset: ProfitTrackerPreset, itemId: String) = update(preset) { customizations ->
        if (itemId !in customizations.excludedItems) customizations.excludedItems += itemId
    }

    fun restore(preset: ProfitTrackerPreset, itemId: String) = update(preset) { customizations ->
        customizations.excludedItems.remove(itemId)
    }

    fun addCustomItem(preset: ProfitTrackerPreset, itemId: String) = update(preset) { customizations ->
        if (itemId !in customizations.customItems) customizations.customItems += itemId
        customizations.excludedItems.remove(itemId)
    }

    fun removeCustomItem(preset: ProfitTrackerPreset, itemId: String) = update(preset) { customizations ->
        customizations.customItems.remove(itemId)
        customizations.excludedItems.remove(itemId)
        customizations.priceSources.remove(itemId)
    }

    fun setPriceSource(preset: ProfitTrackerPreset, itemId: String, source: ProfitTrackerPriceSource?) =
        update(preset) { customizations ->
            if (source == null) customizations.priceSources.remove(itemId)
            else customizations.priceSources[itemId] = source.name
        }

    fun reset(preset: ProfitTrackerPreset) = update(preset) { customizations ->
        customizations.excludedItems.clear()
        customizations.customItems.clear()
        customizations.priceSources.clear()
    }

    private fun update(
        preset: ProfitTrackerPreset,
        action: (ProfileStorage.ProfitTrackerItemCustomizations) -> Unit,
    ) {
        val customizations = ProfileStorageApi.storage.profitTracker.itemCustomizations
            .getOrPut(preset.name) { ProfileStorage.ProfitTrackerItemCustomizations() }
        action(customizations)
        ProfileStorageApi.markDirty()
        ProfileStorageApi.saveNow()
    }
}
