package com.skysoft.features.event.diana

import com.skysoft.config.SkysoftConfigGui
import com.skysoft.utils.PlayerUtilities.localPlayerName
import com.skysoft.utils.chat.ChatMessage
import com.skysoft.utils.chat.ChatMessageVisibility
import com.skysoft.utils.chat.ChatSenderParser
import com.skysoft.utils.chat.SkysoftPartyShare
import com.skysoft.utils.chat.isLocalPlayer

internal object DianaLootshareReadyMessage {
    fun broadcast() {
        val config = SkysoftConfigGui.config().events.diana.lootshare
        if (!config.enabled) return
        if (config.settings.partyCheckmarks) {
            localPlayerName()?.let { playerName ->
                DianaLootshareReadyMarkers.mark(playerName, System.currentTimeMillis())
            }
        }
        if (config.settings.shareSecuredMessage) SkysoftPartyShare.sendParty(MESSAGE)
    }

    fun isMessage(body: String): Boolean =
        body.equals(MESSAGE, ignoreCase = true)

    fun handlePartyMessage(
        message: ChatMessage,
        localPlayerName: String?,
        now: Long,
        showMarker: Boolean,
        showMessage: Boolean,
    ): ChatMessageVisibility {
        val sender = ChatSenderParser.senderBefore(message, MESSAGE) ?: return ChatMessageVisibility.SHOW
        val isOwnEcho = isOwnPartyEcho(message, sender, localPlayerName, now)
        if (!isOwnEcho && showMarker && !sender.isLocalPlayer(localPlayerName)) {
            DianaLootshareReadyMarkers.mark(sender.name, now)
        }
        return partyMessageVisibility(showMessage)
    }

    const val MESSAGE = "Loot share secured!"
}
