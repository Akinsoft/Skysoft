package com.skysoft.features.event.diana

import com.skysoft.utils.chat.ChatMessage
import com.skysoft.utils.chat.ChatMessageVisibility
import com.skysoft.utils.chat.SkysoftPartyShare

internal object DianaLootshareReadyMessage {
    fun broadcast() {
        SkysoftPartyShare.sendParty(MESSAGE)
    }

    fun isMessage(body: String): Boolean =
        body.equals(MESSAGE, ignoreCase = true)

    fun handlePartyMessage(
        message: ChatMessage,
        localPlayerName: String?,
        now: Long,
        showMarker: Boolean,
    ): ChatMessageVisibility {
        val sender = DianaRareMobRuntime.senderFor(message, MESSAGE)
            ?: return ChatMessageVisibility.SHOW
        if (DianaRareMobPartyEcho.shouldHideRecentlySent(message, sender, localPlayerName, now)) {
            return ChatMessageVisibility.HIDE
        }
        if (showMarker && !sender.isLocalPlayer(localPlayerName)) {
            DianaLootshareReadyMarkers.mark(sender.name, now)
        }
        return ChatMessageVisibility.HIDE
    }

    const val MESSAGE = "Loot share secured!"
}
