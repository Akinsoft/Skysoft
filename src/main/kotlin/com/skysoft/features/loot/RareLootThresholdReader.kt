package com.skysoft.features.loot

import com.skysoft.utils.NumberUtilities.parseCompactNumberOrNull
import com.skysoft.utils.SkysoftChat

internal class RareLootThresholdReader(
    private val settingName: String,
) {
    private var lastInvalidValue: String? = null

    fun read(raw: String): Double? {
        val trimmed = raw.trim()
        val coins = trimmed.parseCompactNumberOrNull()?.value
        val problem = when {
            trimmed.isEmpty() -> "Enter a coin value."
            coins == null -> "Use a number like 1m or 1,000,000."
            coins.isNaN() || coins.isInfinite() -> "Use a finite coin value."
            coins < 0.0 -> "Use zero or more coins."
            else -> null
        }
        if (problem == null) {
            lastInvalidValue = null
            return coins
        }
        if (lastInvalidValue != raw) {
            lastInvalidValue = raw
            SkysoftChat.error("Invalid $settingName: $raw ($problem)")
        }
        return null
    }

    fun clear() {
        lastInvalidValue = null
    }
}
