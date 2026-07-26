package com.skysoft.features.loot

import com.skysoft.utils.SkysoftChat

internal class RareLootThresholdReader(
    private val settingName: String,
) {
    private var lastInvalidValue: String? = null

    fun read(raw: String): RareLootThreshold? =
        when (val result = RareLootThreshold.parse(raw)) {
            is RareLootThresholdParseResult.Valid -> {
                lastInvalidValue = null
                result.threshold
            }
            is RareLootThresholdParseResult.Invalid -> {
                warn(result)
                null
            }
        }

    fun clear() {
        lastInvalidValue = null
    }

    private fun warn(result: RareLootThresholdParseResult.Invalid) {
        if (lastInvalidValue == result.raw) return
        lastInvalidValue = result.raw
        SkysoftChat.error("Invalid $settingName: ${result.raw} (${result.reason})")
    }
}
