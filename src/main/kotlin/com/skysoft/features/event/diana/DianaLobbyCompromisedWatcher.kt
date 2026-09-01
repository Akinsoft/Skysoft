package com.skysoft.features.event.diana

import com.skysoft.config.DianaLobbyCompromisedAlert
import com.skysoft.config.MAX_LOBBY_COMPROMISED_STRANGER_LIMIT
import com.skysoft.config.MIN_LOBBY_COMPROMISED_STRANGER_LIMIT
import com.skysoft.config.SkysoftConfigGui
import com.skysoft.data.hypixel.HypixelPartyApi
import com.skysoft.data.hypixel.TabListApi
import com.skysoft.utils.chat.ChatEvents
import com.skysoft.utils.chat.ChatMessage
import com.skysoft.utils.chat.ChatMessageVisibility
import com.skysoft.utils.chat.SkysoftPartyShare
import com.skysoft.utils.SkysoftClientEvents
import net.minecraft.client.Minecraft
import java.util.UUID
import kotlin.time.Duration.Companion.seconds

internal object DianaLobbyCompromisedWatcher {
    const val MESSAGE = "Lobby compromised!"

    private val config get() = SkysoftConfigGui.config().events.diana
    private val feature get() = config.lobbyCompromised
    private val settings get() = feature.settings
    private val alertState = DianaLobbyCompromisedState(REQUIRED_COMPROMISED_STABLE_MILLIS)
    private val friendlyPresenceTracker = DianaLobbyFriendlyPresenceTracker()
    private var tabState = DianaLobbyTabState.EMPTY
    private var lastTabSessionId = Long.MIN_VALUE

    fun register() {
        TabListApi.onChange(
            "Diana Lobby Compromised",
            isActive = ::isConfigured,
            listener = ::updateTabState,
        )
        SkysoftClientEvents.onEndTick(
            "Diana Lobby Compromised tick",
            isActive = { isConfigured() || lastTabSessionId != Long.MIN_VALUE },
        ) { onTick() }
        SkysoftClientEvents.onDisconnect("Diana Lobby Compromised disconnect reset", ::clear)
        ChatEvents.onPartyMessage("Diana compromised-lobby chat", ::isConfigured) { message -> handlePartyMessage(message) }
    }

    private fun onTick() {
        if (!isActive()) {
            clear()
            return
        }
        val now = System.currentTimeMillis()
        val strangerCount = currentStrangerCount(now) ?: run {
            alertState.reset()
            return
        }
        if (lastTabSessionId != TabListApi.sessionId) {
            alertState.reset()
            lastTabSessionId = TabListApi.sessionId
        }

        val threshold = settings.strangerLimit.coerceIn(
            MIN_LOBBY_COMPROMISED_STRANGER_LIMIT,
            MAX_LOBBY_COMPROMISED_STRANGER_LIMIT,
        )
        val result = alertState.update(strangerCount, threshold, now)
        if (result == DianaLobbyCompromisedUpdate.BECAME_COMPROMISED) {
            sendAlerts(settings.alerts.get())
        }
    }

    private fun isActive(): Boolean =
        feature.enabled &&
            DianaEventState.isOnHub() &&
            DianaEventState.isMythologicalRitualActive()

    private fun isConfigured(): Boolean = feature.enabled

    private fun currentStrangerCount(now: Long): Int? {
        if (
            !HypixelPartyApi.isLoaded ||
            !TabListApi.isSkyBlockDataLoaded ||
            !TabListApi.hasWaitedForSkyBlockData(TAB_BASELINE_DELAY)
        ) return null
        val localPlayerUuid = Minecraft.getInstance().player?.uuid
        val reportedPlayerCount = tabState.reportedPlayerCount ?: return null
        val friendlyPlayerCount = friendlyPresenceTracker.friendlyUuids(
            visibleUuids = tabState.visibleUuids,
            partyMemberUuids = HypixelPartyApi.memberUuids,
            localPlayerUuid = localPlayerUuid,
            reportedPlayerCount = reportedPlayerCount,
            sessionId = TabListApi.sessionId,
            now = now,
        ).size
        return (reportedPlayerCount - friendlyPlayerCount).coerceAtLeast(0)
    }

    private fun updateTabState() {
        val entries = TabListApi.entries
        tabState = DianaLobbyTabState(
            reportedPlayerCount = entries.firstNotNullOfOrNull { entry ->
                entry.cleanDisplayName.playerCountFromTabDisplay()
            },
            visibleUuids = entries.map { entry -> entry.uuid },
        )
    }

    private fun sendAlerts(alerts: Collection<DianaLobbyCompromisedAlert>) {
        if (DianaLobbyCompromisedAlert.TITLE_ALERT in alerts) {
            DianaLobbyCompromisedTitleRenderer.show()
        }
        if (DianaLobbyCompromisedAlert.CHAT_ALERT in alerts) {
            SkysoftPartyShare.sendParty(MESSAGE)
        }
    }

    private fun handlePartyMessage(message: ChatMessage): ChatMessageVisibility {
        if (!message.body.equals(MESSAGE, ignoreCase = true)) return ChatMessageVisibility.SHOW
        val sender = DianaRareMobRuntime.senderFor(message, MESSAGE) ?: return ChatMessageVisibility.SHOW
        val isOwnEcho = DianaRareMobPartyEcho.shouldHideRecentlySent(
            message,
            sender,
            DianaRareMobRuntime.localPlayerName(),
            System.currentTimeMillis(),
        )
        return if (isOwnEcho && !config.showPartyMessages) {
            ChatMessageVisibility.HIDE
        } else {
            ChatMessageVisibility.SHOW
        }
    }

    private fun clear() {
        alertState.reset()
        friendlyPresenceTracker.reset()
        lastTabSessionId = Long.MIN_VALUE
        DianaLobbyCompromisedTitleRenderer.clear()
    }

    private val TAB_BASELINE_DELAY = 2.seconds
    private const val REQUIRED_COMPROMISED_STABLE_MILLIS = 2_000L
}

internal class DianaLobbyCompromisedState(
    private val requiredStableMillis: Long = 0L,
) {
    var hasBaseline = false
        private set
    private var wasCompromised = false
    private var lastThreshold: Int? = null
    private var acknowledgedStrangerCount = 0
    private var compromisedCandidateSinceMillis: Long? = null

    fun update(
        strangerCount: Int,
        threshold: Int,
        now: Long = System.currentTimeMillis(),
    ): DianaLobbyCompromisedUpdate {
        val isCompromised = strangerCount >= threshold
        if (!hasBaseline || lastThreshold != threshold) {
            hasBaseline = true
            wasCompromised = isCompromised
            lastThreshold = threshold
            acknowledgedStrangerCount = strangerCount
            compromisedCandidateSinceMillis = null
            return DianaLobbyCompromisedUpdate.NO_ALERT
        }

        if (!isCompromised) {
            wasCompromised = false
            acknowledgedStrangerCount = strangerCount
            compromisedCandidateSinceMillis = null
            return DianaLobbyCompromisedUpdate.NO_ALERT
        }
        if (wasCompromised && strangerCount <= acknowledgedStrangerCount) {
            acknowledgedStrangerCount = strangerCount
            compromisedCandidateSinceMillis = null
            return DianaLobbyCompromisedUpdate.NO_ALERT
        }

        val compromisedSince = compromisedCandidateSinceMillis ?: now.also {
            compromisedCandidateSinceMillis = it
        }
        if (now - compromisedSince < requiredStableMillis) return DianaLobbyCompromisedUpdate.NO_ALERT

        wasCompromised = true
        acknowledgedStrangerCount = strangerCount
        compromisedCandidateSinceMillis = null
        return DianaLobbyCompromisedUpdate.BECAME_COMPROMISED
    }

    fun reset() {
        hasBaseline = false
        wasCompromised = false
        lastThreshold = null
        acknowledgedStrangerCount = 0
        compromisedCandidateSinceMillis = null
    }
}

internal enum class DianaLobbyCompromisedUpdate {
    NO_ALERT,
    BECAME_COMPROMISED,
}

internal class DianaLobbyFriendlyPresenceTracker(
    private val graceMillis: Long = FRIENDLY_PRESENCE_GRACE_MILLIS,
) {
    private val lastSeenFriendlyUuids = mutableMapOf<UUID, Long>()
    private var tabSessionId = Long.MIN_VALUE

    fun friendlyUuids(
        visibleUuids: Collection<UUID>,
        partyMemberUuids: Set<UUID>,
        localPlayerUuid: UUID?,
        reportedPlayerCount: Int,
        sessionId: Long,
        now: Long,
    ): Set<UUID> {
        if (tabSessionId != sessionId) {
            reset(sessionId)
        }

        val eligibleFriendlyUuids = partyMemberUuids.toMutableSet()
        localPlayerUuid?.let { uuid -> eligibleFriendlyUuids += uuid }
        lastSeenFriendlyUuids.keys.retainAll(eligibleFriendlyUuids)

        visibleUuids
            .asSequence()
            .filter { uuid -> uuid in eligibleFriendlyUuids }
            .forEach { uuid -> lastSeenFriendlyUuids[uuid] = now }
        if (localPlayerUuid != null && reportedPlayerCount > 0) {
            lastSeenFriendlyUuids[localPlayerUuid] = now
        }

        return lastSeenFriendlyUuids
            .asSequence()
            .filter { (_, lastSeenAt) -> now - lastSeenAt <= graceMillis }
            .map { (uuid, _) -> uuid }
            .take(reportedPlayerCount)
            .toSet()
    }

    fun reset(nextTabSessionId: Long = Long.MIN_VALUE) {
        tabSessionId = nextTabSessionId
        lastSeenFriendlyUuids.clear()
    }
}

private fun String.playerCountFromTabDisplay(): Int? {
    val match = playerCountPattern.matchEntire(this) ?: return null
    return match.groups["count"]?.value?.toIntOrNull()
}

private data class DianaLobbyTabState(
    val reportedPlayerCount: Int?,
    val visibleUuids: List<UUID>,
) {
    companion object {
        val EMPTY = DianaLobbyTabState(
            reportedPlayerCount = null,
            visibleUuids = emptyList(),
        )
    }
}

private val playerCountPattern = Regex("""Players \((?<count>\d+)\)""")
private const val FRIENDLY_PRESENCE_GRACE_MILLIS = 15_000L
