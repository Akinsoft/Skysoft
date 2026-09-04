package com.skysoft.data.skyblock

import com.skysoft.utils.ActiveListenerRegistry
import com.skysoft.utils.chat.ChatEvents
import com.skysoft.utils.chat.ChatMessageVisibility

object SkyBlockSupercrafts {
    private val listeners = ActiveListenerRegistry<(SkyBlockSupercraft) -> Unit>()
    private var registered = false

    fun onCraft(
        boundary: String,
        isActive: () -> Boolean,
        listener: (SkyBlockSupercraft) -> Unit,
    ) {
        if (!registered) register()
        listeners.register(boundary, isActive, listener)
    }

    private fun register() {
        registered = true
        ChatEvents.onVisibleMessage("SkyBlock Supercrafts", { listeners.hasActiveListeners }) { message ->
            parseSkyBlockSupercraft(message.cleanText)?.let { supercraft ->
                listeners.forEachActive { listener -> listener(supercraft) }
            }
            ChatMessageVisibility.SHOW
        }
    }
}

data class SkyBlockSupercraft(
    val displayName: String,
    val amount: Int,
)

internal fun parseSkyBlockSupercraft(message: String): SkyBlockSupercraft? {
    val match = SUPERCRAFT_PATTERN.matchEntire(message) ?: return null
    val displayName = match.groups["item"]?.value?.trim().orEmpty()
    val amount = match.groups["amount"]?.value?.replace(",", "")?.toIntOrNull() ?: 1
    return SkyBlockSupercraft(displayName, amount).takeIf { displayName.isNotEmpty() && amount > 0 }
}

private val SUPERCRAFT_PATTERN = Regex("^You Supercrafted (?<item>.+?)(?: x(?<amount>[\\d,]+))?!$")
