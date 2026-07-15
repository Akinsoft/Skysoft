package com.skysoft.features.event.diana

import com.skysoft.config.DianaLobbyCompromisedAlert
import com.skysoft.config.MAX_LOBBY_COMPROMISED_STRANGER_LIMIT
import com.skysoft.config.MIN_LOBBY_COMPROMISED_STRANGER_LIMIT
import com.skysoft.config.SkysoftConfigGui
import com.skysoft.data.hypixel.HypixelPartyApi
import com.skysoft.data.hypixel.TabListApi
import com.skysoft.data.hypixel.TabListEntry
import com.skysoft.utils.TextUtilities.cleanSkyBlockText
import com.skysoft.utils.chat.ChatEvents
import com.skysoft.utils.chat.ChatMessage
import com.skysoft.utils.chat.ChatMessageVisibility
import com.skysoft.utils.chat.SkysoftPartyShare
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.minecraft.client.Minecraft
import java.util.UUID
import kotlin.time.Duration.Companion.seconds

internal object DianaLobbyCompromisedWatcher {
    const val MESSAGE = "Lobby compromised!"

    private val config get() = SkysoftConfigGui.config().events.diana
    private val settings get() = config.settings
    private val alertState = DianaLobbyCompromisedState(REQUIRED_COMPROMISED_STABLE_MILLIS)
    private val friendlyPresenceTracker = DianaLobbyFriendlyPresenceTracker()
    private var lastTabSessionId = Long.MIN_VALUE

    fun register() {
        ClientTickEvents.END_CLIENT_TICK.register { onTick() }
        ClientPlayConnectionEvents.DISCONNECT.register { _, _ -> clear() }
        ChatEvents.onPartyMessage { message -> handlePartyMessage(message) }
    }

    private fun onTick() {
        if (!isActive()) {
            clear()
            return
        }
        val now = System.currentTimeMillis()
        val population = currentPopulation(now) ?: run {
            alertState.reset()
            return
        }
        if (lastTabSessionId != TabListApi.sessionId) {
            alertState.reset()
            lastTabSessionId = TabListApi.sessionId
        }

        val threshold = settings.lobbyCompromisedStrangerLimit.coerceIn(
            MIN_LOBBY_COMPROMISED_STRANGER_LIMIT,
            MAX_LOBBY_COMPROMISED_STRANGER_LIMIT,
        )
        val result = alertState.update(population.strangerCount, threshold, now)
        if (result == DianaLobbyCompromisedUpdate.BECAME_COMPROMISED) {
            sendAlerts(settings.lobbyCompromisedAlerts.get())
        }
    }

    private fun isActive(): Boolean =
        config.enabled &&
            settings.lobbyCompromised &&
            DianaEventState.isOnHub() &&
            DianaEventState.isMythologicalRitualActive()

    private fun currentPopulation(now: Long): DianaLobbyPopulation? {
        if (
            !HypixelPartyApi.isLoaded ||
            !TabListApi.isSkyBlockDataLoaded ||
            !TabListApi.hasWaitedForSkyBlockData(TAB_BASELINE_DELAY)
        ) return null
        val localProfile = Minecraft.getInstance().player?.gameProfile
        val tabEntries = TabListApi.entries
        val tabParse = tabEntries.parseDianaLobbyTab()
        val reportedPlayerCount = tabParse.reportedPlayerCount ?: return null
        val tabEntryUuids = tabEntries.map { entry -> entry.uuid }
        val friendlyPlayerUuids = friendlyPresenceTracker.friendlyUuids(
            visibleUuids = tabEntryUuids,
            partyMemberUuids = HypixelPartyApi.memberUuids,
            localPlayerUuid = localProfile?.id,
            reportedPlayerCount = reportedPlayerCount,
            sessionId = TabListApi.sessionId,
            now = now,
        )
        return dianaLobbyPopulation(
            players = tabParse.players,
            partyMemberUuids = HypixelPartyApi.memberUuids,
            localPlayerUuid = localProfile?.id,
            localPlayerName = localProfile?.name,
            reportedPlayerCount = reportedPlayerCount,
            tabEntryUuids = tabEntryUuids,
            friendlyPlayerUuids = friendlyPlayerUuids,
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
        return if (DianaRareMobPartyEcho.shouldHideRecentlySent(
                message,
                sender,
                DianaRareMobRuntime.localPlayerName(),
                System.currentTimeMillis(),
            )
        ) {
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

internal data class DianaLobbyPopulation(
    val strangerCount: Int,
    val playerCount: Int,
    val friendlyPlayerCount: Int,
)

internal data class DianaLobbyPlayer(
    val uuid: UUID,
    val name: String,
)

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

internal fun dianaLobbyPopulation(
    players: Collection<DianaLobbyPlayer>,
    partyMemberUuids: Set<UUID>,
    localPlayerUuid: UUID?,
    localPlayerName: String?,
    reportedPlayerCount: Int? = null,
    tabEntryUuids: Collection<UUID> = players.map { player -> player.uuid },
    friendlyPlayerUuids: Collection<UUID>? = null,
): DianaLobbyPopulation {
    val distinctPlayers = players.distinctBy { player -> player.uuid }
    val parsedStrangerCount = distinctPlayers
        .asSequence()
        .filterNot { player -> player.isLocalPlayer(localPlayerUuid, localPlayerName) }
        .filterNot { player -> player.uuid in partyMemberUuids }
        .count()
    val friendlyPlayerCount = if (reportedPlayerCount != null) {
        val friendlyUuids = friendlyPlayerUuids?.toSet()
            ?: knownFriendlyUuids(tabEntryUuids, partyMemberUuids, localPlayerUuid, reportedPlayerCount)
        friendlyUuids.take(reportedPlayerCount).size
    } else {
        distinctPlayers.size - parsedStrangerCount
    }
    val strangerCount = if (reportedPlayerCount != null) {
        (reportedPlayerCount - friendlyPlayerCount).coerceAtLeast(0)
    } else {
        parsedStrangerCount
    }
    return DianaLobbyPopulation(
        strangerCount = strangerCount,
        playerCount = reportedPlayerCount ?: distinctPlayers.size,
        friendlyPlayerCount = friendlyPlayerCount,
    )
}

internal fun Collection<TabListEntry>.parseDianaLobbyTab(): DianaLobbyTabParse {
    val players = mutableListOf<DianaLobbyPlayer>()
    var reportedPlayerCount: Int? = null
    for (entry in this) {
        val cleanDisplayName = entry.displayName.cleanSkyBlockText()
        reportedPlayerCount = reportedPlayerCount ?: cleanDisplayName.playerCountFromTabDisplay()
        entry.parseDianaLobbyTabRow(cleanDisplayName)?.let(players::add)
    }
    return DianaLobbyTabParse(
        players = players.distinctBy { player -> player.uuid },
        reportedPlayerCount = reportedPlayerCount,
    )
}

private fun TabListEntry.parseDianaLobbyTabRow(cleanDisplayName: String): DianaLobbyPlayer? {
    val profilePlayerName = profileName.takeIf { it.isMinecraftPlayerName() }
        ?: return null
    val displayedName = cleanDisplayName.playerNameFromTabDisplay()
        ?: return null
    if (!displayedName.equals(profilePlayerName, ignoreCase = true)) {
        return null
    }
    return DianaLobbyPlayer(uuid, profilePlayerName)
}

private fun DianaLobbyPlayer.isLocalPlayer(localPlayerUuid: UUID?, localPlayerName: String?): Boolean =
    uuid == localPlayerUuid || name.equals(localPlayerName, ignoreCase = true)

private fun String.playerNameFromTabDisplay(): String? {
    val match = playerRowPattern.matchEntire(this) ?: return null
    return match.groups["name"]?.value
}

private fun String.playerCountFromTabDisplay(): Int? {
    val match = playerCountPattern.matchEntire(this) ?: return null
    return match.groups["count"]?.value?.toIntOrNull()
}

private fun String.isMinecraftPlayerName(): Boolean =
    minecraftPlayerNamePattern.matchEntire(this) != null

private fun knownFriendlyUuids(
    tabEntryUuids: Collection<UUID>,
    partyMemberUuids: Set<UUID>,
    localPlayerUuid: UUID?,
    reportedPlayerCount: Int,
): Set<UUID> {
    val friendlyUuids = tabEntryUuids
        .asSequence()
        .filter { uuid -> uuid in partyMemberUuids || uuid == localPlayerUuid }
        .toMutableSet()
    if (localPlayerUuid != null && reportedPlayerCount > 0) {
        friendlyUuids += localPlayerUuid
    }
    return friendlyUuids.take(reportedPlayerCount).toSet()
}

internal data class DianaLobbyTabParse(
    val players: List<DianaLobbyPlayer>,
    val reportedPlayerCount: Int?,
)

private val minecraftPlayerNamePattern = Regex("""[A-Za-z0-9_]{1,16}""")
private val playerRowPattern = Regex("""\[\d+]\s+(?:\[[^]]+]\s+)*(?<name>[A-Za-z0-9_]{1,16})(?:\s+\[[^]]+])?""")
private val playerCountPattern = Regex("""Players \((?<count>\d+)\)""")
private const val FRIENDLY_PRESENCE_GRACE_MILLIS = 15_000L
