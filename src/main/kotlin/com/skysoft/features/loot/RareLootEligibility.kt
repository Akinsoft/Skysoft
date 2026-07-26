package com.skysoft.features.loot

internal object RareLootEligibility {
    fun hasMinimumValue(threshold: RareLootThreshold, value: RareLootValue?): Boolean =
        value?.coins?.let { it >= threshold.coins } == true

    fun shouldShare(threshold: RareLootThreshold, value: RareLootValue?): Boolean =
        threshold.coins == 0.0 || hasMinimumValue(threshold, value)
}
