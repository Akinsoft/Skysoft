package com.skysoft.features.event.diana

import com.skysoft.config.SkysoftConfigGui
import com.skysoft.utils.chat.ChatMessage
import com.skysoft.utils.chat.ChatMessageVisibility
import com.skysoft.utils.chat.SkysoftPartyShare

internal object DianaLootshareReadyMessage {
    fun broadcast() {
        val config = SkysoftConfigGui.config().events.diana.lootshare
        if (!config.enabled) return
        if (config.settings.partyCheckmarks) {
            DianaRareMobRuntime.localPlayerName()?.let { playerName ->
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
        val sender = DianaRareMobRuntime.senderFor(message, MESSAGE)
            ?: return ChatMessageVisibility.SHOW
        if (DianaRareMobPartyEcho.shouldHideRecentlySent(message, sender, localPlayerName, now)) {
            return visibility(showMessage)
        }
        if (showMarker && !sender.isLocalPlayer(localPlayerName)) {
            DianaLootshareReadyMarkers.mark(sender.name, now)
        }
        return visibility(showMessage)
    }

    private fun visibility(showMessage: Boolean): ChatMessageVisibility =
        if (showMessage) ChatMessageVisibility.SHOW else ChatMessageVisibility.HIDE

    const val MESSAGE = "Loot share secured!"
}
