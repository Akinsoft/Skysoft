package com.skysoft.data.skyblock

import com.skysoft.utils.NumberUtilities.formatIntOrNull
import com.skysoft.utils.RegexUtilities.group

object SkyBlockSupercrafts {
    private val listeners = ParsedChatListeners("SkyBlock Supercrafts", ::parseSkyBlockSupercraft)

    fun onCraft(
        boundary: String,
        isActive: () -> Boolean,
        listener: (SkyBlockSupercraft) -> Unit,
    ) {
        listeners.register(boundary, isActive, listener)
    }
}

data class SkyBlockSupercraft(
    val displayName: String,
    val amount: Int,
)

private fun parseSkyBlockSupercraft(message: String): SkyBlockSupercraft? {
    val match = SUPERCRAFT_PATTERN.matchEntire(message) ?: return null
    val displayName = match.group("item").trim()
    val amount = match.groups["amount"]?.value?.let { it.formatIntOrNull() ?: return null } ?: 1
    return SkyBlockSupercraft(displayName, amount).takeIf { displayName.isNotEmpty() && amount > 0 }
}

private val SUPERCRAFT_PATTERN = Regex("^You Supercrafted (?<item>.+?)(?: x(?<amount>[\\d,]+))?!$")
