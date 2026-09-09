package com.skysoft.features.profit

import com.skysoft.config.ProfitTrackerPriceSource
import com.skysoft.data.ProfileStorage
import com.skysoft.data.ProfileStorageApi
import com.skysoft.data.ProfileStorageView

internal object ProfitTrackerItemCustomizations {
    fun data(target: ProfitTrackerTarget): ProfileStorageView.ProfitTrackerItemCustomizations? =
        target.custom?.let { custom ->
            ProfileStorage.ProfitTrackerItemCustomizations(
                customItems = custom.items,
                priceSources = custom.priceSources,
            )
        } ?: ProfileStorageApi.storage.profitTracker.itemCustomizations[target.storageKey]

    fun isExcluded(target: ProfitTrackerTarget, itemId: String): Boolean =
        itemId in data(target)?.excludedItems.orEmpty()

    fun customItems(target: ProfitTrackerTarget): Set<String> = data(target)?.customItems.orEmpty().toSet()

    fun priceSource(target: ProfitTrackerTarget, itemId: String): ProfitTrackerPriceSource =
        priceSourceOverride(target, itemId) ?: target.config.settings.priceSource

    fun priceSourceOverride(target: ProfitTrackerTarget, itemId: String): ProfitTrackerPriceSource? =
        data(target)?.priceSources?.get(itemId)
            ?.let { stored -> ProfitTrackerPriceSource.entries.firstOrNull { it.name == stored } }

    fun exclude(target: ProfitTrackerTarget, itemId: String) = update(target) { customizations ->
        if (target.custom != null) customizations.customItems.remove(itemId)
        else if (itemId !in customizations.excludedItems) customizations.excludedItems += itemId
    }

    fun restore(target: ProfitTrackerTarget, itemId: String) = update(target) { customizations ->
        customizations.excludedItems.remove(itemId)
    }

    fun addCustomItem(target: ProfitTrackerTarget, itemId: String) = update(target) { customizations ->
        if (itemId !in customizations.customItems) customizations.customItems += itemId
        customizations.excludedItems.remove(itemId)
    }

    fun removeCustomItem(target: ProfitTrackerTarget, itemId: String) = update(target) { customizations ->
        customizations.customItems.remove(itemId)
        customizations.excludedItems.remove(itemId)
        customizations.priceSources.remove(itemId)
    }

    fun setPriceSource(target: ProfitTrackerTarget, itemId: String, source: ProfitTrackerPriceSource?) =
        update(target) { customizations ->
            if (source == null) customizations.priceSources.remove(itemId)
            else customizations.priceSources[itemId] = source.name
        }

    fun reset(target: ProfitTrackerTarget) = update(target) { customizations ->
        customizations.excludedItems.clear()
        customizations.customItems.clear()
        customizations.priceSources.clear()
    }

    private fun update(
        target: ProfitTrackerTarget,
        action: (ProfileStorage.ProfitTrackerItemCustomizations) -> Unit,
    ) {
        val custom = target.custom
        if (custom != null) {
            action(ProfileStorage.ProfitTrackerItemCustomizations(customItems = custom.items, priceSources = custom.priceSources))
            com.skysoft.config.SkysoftConfigGui.config().saveNow()
        } else {
            ProfileStorageApi.updateProfile { profile ->
                val customizations = profile.profitTracker.itemCustomizations.getOrPut(target.storageKey) {
                    ProfileStorage.ProfitTrackerItemCustomizations()
                }
                action(customizations)
            }
        }
    }
}
