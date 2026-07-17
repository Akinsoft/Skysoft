package com.skysoft.features.event.diana

import com.skysoft.config.SkysoftConfigGui
import com.skysoft.features.loot.RareLootChatParser
import com.skysoft.features.loot.RareLootChatDrop
import com.skysoft.features.loot.RareLootContextContributor
import com.skysoft.features.loot.RareLootContextRegistry
import com.skysoft.features.loot.RareLootDropCount
import com.skysoft.utils.chat.ChatEvents
import com.skysoft.utils.chat.ChatMessage
import com.skysoft.utils.chat.ChatMessageVisibility
import com.skysoft.utils.chat.SkysoftPartyShare
import com.skysoft.utils.SkysoftClientEvents

internal object MythologicalRitualTracker {
    private val config get() = SkysoftConfigGui.config().events.diana
    private val lootShareWindow = MythologicalRitualLootShareWindow()
    private val partyCommandCooldown = MythologicalPartyCommandCooldown()
    private var ticks = 0

    fun register() {
        SkysoftClientEvents.onEndTick("Mythological Ritual Tracker tick") { onTick() }
        SkysoftClientEvents.onDisconnect("Mythological Ritual Tracker disconnect reset", ::clearSession)
        SkysoftClientEvents.onClientStopping("Mythological Ritual Tracker save") {
            MythologicalRitualTrackerRepository.saveNow()
        }
        RareLootContextRegistry.register(rareLootContextContributor)
        ChatEvents.onVisibleMessage("Mythological Ritual tracker chat") { message ->
            handleVisibleMessage(message)
            ChatMessageVisibility.SHOW
        }
        ChatEvents.onPartyMessage("Mythological Ritual party chat") { message ->
            handlePartyMessage(message)
            ChatMessageVisibility.SHOW
        }
    }

    private fun onTick() {
        if (++ticks % ACTIVE_TIME_TICK_INTERVAL != 0) return
        if (!config.enabled || !isDianaActivityContext()) return
        MythologicalRitualTrackerRepository.recordActiveAt(System.currentTimeMillis())
    }

    private fun handleVisibleMessage(message: ChatMessage) {
        if (!config.enabled || !message.isSystemLike || !DianaEventState.isOnHub()) return
        if (RareLootChatParser.parse(message.cleanText) != null) return
        val now = System.currentTimeMillis()
        MythologicalRitualTrackerRepository.update(now) { state ->
            MythologicalRitualMessageTracker.trackNonRareLoot(message.cleanText, state, lootShareWindow, now)
        }
    }

    private fun handlePartyMessage(message: ChatMessage) {
        if (!config.enabled) return
        SkysoftPartyShare.markPartyChatObserved()
        val state = MythologicalRitualTrackerRepository.displayStateOrNull() ?: return
        val response = MythologicalRitualPartyCommands.response(
            body = message.body,
            localPlayerName = DianaRareMobRuntime.localPlayerName(),
            state = state,
        ) ?: return
        if (!partyCommandCooldown.canRespond(message.sender?.name, System.currentTimeMillis())) return
        SkysoftPartyShare.sendParty(response, allowRecentPartyChatEvidence = true)
    }

    private fun isDianaActivityContext(): Boolean =
        DianaEventState.isOnHub() &&
            (DianaEventState.isMythologicalRitualActive() || DianaEventState.hasSpadeInHotbar())

    private fun clearSession() {
        lootShareWindow.clear()
        partyCommandCooldown.clear()
        ticks = 0
        MythologicalRitualTrackerRepository.saveNow()
    }

    private val rareLootContextContributor = object : RareLootContextContributor {
        override fun hasLootShareEvidence(now: Long): Boolean =
            DianaRareMobSharing.likelyRemoteRareLoot

        override fun recordDrop(drop: RareLootChatDrop, lootshare: Boolean, now: Long): RareLootDropCount? {
            if (!config.enabled || !DianaEventState.isOnHub()) return null
            val isLootShareDrop = lootshare || lootShareWindow.isActive(now)
            var dropCount: RareLootDropCount? = null
            MythologicalRitualTrackerRepository.update(now) { state ->
                dropCount = MythologicalRitualMessageTracker.trackRareLoot(drop, state, isLootShareDrop)
            }
            return dropCount
        }
    }

    private const val ACTIVE_TIME_TICK_INTERVAL = 20
}

internal class MythologicalPartyCommandCooldown(
    private val cooldownMillis: Long = PARTY_COMMAND_COOLDOWN_MILLIS,
) {
    private val lastResponseBySender = mutableMapOf<String, Long>()

    fun canRespond(senderName: String?, now: Long): Boolean {
        val sender = senderName?.lowercase() ?: return false
        val lastResponse = lastResponseBySender[sender]
        if (lastResponse != null && now - lastResponse < cooldownMillis) return false
        lastResponseBySender.entries.removeIf { (_, lastResponseAt) -> now - lastResponseAt >= cooldownMillis }
        lastResponseBySender[sender] = now
        return true
    }

    fun clear() {
        lastResponseBySender.clear()
    }
}

private const val PARTY_COMMAND_COOLDOWN_MILLIS = 1_000L
