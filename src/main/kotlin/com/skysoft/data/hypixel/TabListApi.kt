package com.skysoft.data.hypixel

import com.mojang.authlib.GameProfile
import com.skysoft.utils.ActiveConsumerRegistry
import com.skysoft.utils.ActiveStatePublisher
import com.skysoft.utils.ConsumerActivity
import com.skysoft.utils.ElapsedTimeMark
import com.skysoft.utils.SkysoftClientEvents
import com.skysoft.utils.TabListOverlay
import com.skysoft.utils.TextUtilities.cleanSkyBlockText
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.PlayerInfo
import net.minecraft.world.entity.player.PlayerSkin
import net.minecraft.network.chat.Component
import net.minecraft.world.level.GameType
import net.minecraft.world.scores.PlayerTeam
import java.util.Collections
import java.util.UUID
import kotlin.time.Duration

object TabListApi {
    private const val SKYBLOCK_DATA_READY_READS = 2
    private val skyBlockAreaPattern = Regex("""Area: .+""")

    private var skyBlockDataReads = 0
    private var dirty = false
    private var stabilityTicks = 0
    private var skyBlockDataLoadStartedAt = ElapsedTimeMark.farPast()
    private val consumers = ActiveConsumerRegistry()
    private val publisher = ActiveStatePublisher("Tab List API", TabListSnapshot())

    val sessionId: Long
        get() = publisher.state.sessionId

    val contentVersion: Long
        get() = publisher.version

    val isLoaded: Boolean
        get() = HypixelLocationState.inSkyBlock && publisher.state.loaded

    val lines: List<Component>
        get() = if (isLoaded) publisher.state.lines else emptyList()

    val entries: List<TabListEntry>
        get() = if (isLoaded) publisher.state.entries else emptyList()

    val playerProfiles: List<TabListPlayerProfile>
        get() = if (isLoaded) publisher.state.playerProfiles else emptyList()

    fun playerProfile(uuid: UUID): TabListPlayerProfile? = playerProfiles.firstOrNull { it.uuid == uuid }

    fun playerProfile(profileName: String): TabListPlayerProfile? =
        playerProfiles.firstOrNull { it.profileName.equals(profileName, ignoreCase = true) }

    val header: Component?
        get() = if (isLoaded) publisher.state.header else null

    val footer: Component?
        get() = if (isLoaded) publisher.state.footer else null

    val isSkyBlockDataLoaded: Boolean
        get() = isLoaded && publisher.state.skyBlockDataLoaded

    val skyBlockLines: List<Component>
        get() = if (isSkyBlockDataLoaded) publisher.state.lines else emptyList()

    val skyBlockFooter: Component?
        get() = if (isSkyBlockDataLoaded) publisher.state.footer else null

    val skyBlockAreaName: String?
        get() = parseSkyBlockTabArea(skyBlockLines.map { it.cleanSkyBlockText() })

    fun register() {
        publisher.register()
        HypixelLocationState.onChange(
            "Tab List location",
            isActive = { consumers.hasActiveConsumers },
        ) {
            resetSession()
            dirty = true
        }
        SkysoftClientEvents.onEndTick(
            "Tab List update",
            isActive = { consumers.isActiveOrDeactivating },
        ) {
            onClientTick()
        }
        SkysoftClientEvents.onDisconnect("Tab List reset") {
            resetSession()
            consumers.resetActivity()
        }
    }

    fun registerConsumer(id: String, isActive: () -> Boolean) {
        consumers.register(id, isActive)
    }

    fun onChange(boundary: String, isActive: () -> Boolean, listener: () -> Unit) {
        registerConsumer(boundary, isActive)
        publisher.onChange(boundary, isActive) { listener() }
    }

    internal fun markDirty() {
        dirty = true
    }

    fun hasWaitedForSkyBlockData(duration: Duration): Boolean =
        HypixelLocationState.inSkyBlock && skyBlockDataLoadStartedAt.passedSince() >= duration

    private fun onClientTick() {
        when (consumers.activity()) {
            ConsumerActivity.INACTIVE -> return
            ConsumerActivity.DEACTIVATED -> {
                resetSession()
                return
            }
            ConsumerActivity.ACTIVATED -> dirty = true
            ConsumerActivity.ACTIVE -> Unit
        }

        if (!HypixelLocationState.inSkyBlock) return

        val shouldRefresh = when {
            dirty -> {
                dirty = false
                stabilityTicks = 0
                true
            }
            publisher.state.skyBlockDataLoaded -> false
            ++stabilityTicks >= STABILITY_READ_INTERVAL_TICKS -> {
                stabilityTicks = 0
                true
            }
            else -> false
        }
        if (shouldRefresh) refresh()
    }

    private fun refresh() {
        val next = readTabList()
        if (next.entries.isEmpty()) {
            clearLoadedLines()
            return
        }

        val nextEntries = next.entries
        val nextLines = nextEntries.map { it.displayName }
        val minecraft = Minecraft.getInstance()
        val nextHeader = TabListOverlay.readHeader(minecraft)
        val nextFooter = TabListOverlay.readFooter(minecraft)
        updateSkyBlockDataLoadState(nextEntries)
        publisher.update(
            TabListSnapshot(
                sessionId = sessionId,
                loaded = true,
                skyBlockDataLoaded = skyBlockDataReads >= SKYBLOCK_DATA_READY_READS,
                lines = Collections.unmodifiableList(nextLines),
                entries = Collections.unmodifiableList(nextEntries),
                playerProfiles = Collections.unmodifiableList(next.playerProfiles),
                header = nextHeader,
                footer = nextFooter,
            ),
        )
    }

    private fun resetSession() {
        val nextSessionId = sessionId + 1
        skyBlockDataReads = 0
        dirty = false
        stabilityTicks = 0
        skyBlockDataLoadStartedAt = ElapsedTimeMark.now()
        publisher.update(TabListSnapshot(sessionId = nextSessionId))
    }

    private fun clearLoadedLines() {
        if (!publisher.state.loaded) return
        resetSkyBlockDataLoad()
        publisher.update(TabListSnapshot(sessionId = sessionId))
    }

    private fun updateSkyBlockDataLoadState(entries: List<TabListEntry>) {
        if (!hasSkyBlockData(entries)) {
            resetSkyBlockDataLoad()
            return
        }
        skyBlockDataReads = (skyBlockDataReads + 1).coerceAtMost(SKYBLOCK_DATA_READY_READS)
    }

    private fun resetSkyBlockDataLoad() {
        if (publisher.state.skyBlockDataLoaded || skyBlockDataReads > 0) {
            skyBlockDataLoadStartedAt = ElapsedTimeMark.now()
        }
        skyBlockDataReads = 0
    }

    private fun hasSkyBlockData(entries: List<TabListEntry>): Boolean =
        entries.any { it.cleanDisplayName == "Info" } &&
            entries.any { skyBlockAreaPattern.matches(it.cleanDisplayName) }

    private fun readTabList(): TabListReadResult {
        val connection = Minecraft.getInstance().connection ?: return TabListReadResult()
        val players = connection.listedOnlinePlayers
        val playerProfiles = players.map { player ->
            val entry = player.toTabListEntry()
            TabListPlayerProfile(
                uuid = player.profile.id,
                profileName = player.profile.name,
                profile = player.profile,
                entry = entry,
                playerInfo = player,
            )
        }
        return TabListReadResult(
            entries = playerProfiles.map(TabListPlayerProfile::entry).sortedForDisplay(),
            playerProfiles = playerProfiles,
        )
    }

    private const val STABILITY_READ_INTERVAL_TICKS = 5
}

private data class TabListSnapshot(
    val sessionId: Long = 0L,
    val loaded: Boolean = false,
    val skyBlockDataLoaded: Boolean = false,
    val lines: List<Component> = emptyList(),
    val entries: List<TabListEntry> = emptyList(),
    val playerProfiles: List<TabListPlayerProfile> = emptyList(),
    val header: Component? = null,
    val footer: Component? = null,
)

data class TabListPlayerProfile(
    val uuid: UUID,
    val profileName: String,
    val profile: GameProfile,
    internal val entry: TabListEntry,
    private val playerInfo: PlayerInfo,
) {
    val displayName: Component
        get() = entry.displayName

    val showHat: Boolean
        get() = playerInfo.showHat()

    fun skin(): PlayerSkin = playerInfo.skin
}

private data class TabListReadResult(
    val entries: List<TabListEntry> = emptyList(),
    val playerProfiles: List<TabListPlayerProfile> = emptyList(),
)

internal fun parseSkyBlockTabArea(lines: Iterable<String>): String? = lines.firstNotNullOfOrNull { line ->
    line.trim().takeIf { it.startsWith(SKYBLOCK_AREA_PREFIX) }
        ?.removePrefix(SKYBLOCK_AREA_PREFIX)
        ?.trim()
        ?.takeIf(String::isNotEmpty)
}

internal fun PlayerInfo.toTabListEntry(): TabListEntry {
    val isSpectator = gameMode == GameType.SPECTATOR
    val resolvedName = tabListDisplayName?.copy()
        ?: PlayerTeam.formatNameForTeam(team, Component.literal(profile.name))
    return TabListEntry(
        uuid = profile.id,
        profileName = profile.name,
        displayName = if (isSpectator) resolvedName.copy().withStyle(ChatFormatting.ITALIC) else resolvedName,
        tabListOrder = tabListOrder,
        isSpectator = isSpectator,
        teamName = team?.name.orEmpty(),
    )
}

data class TabListEntry(
    val uuid: UUID,
    val profileName: String,
    val displayName: Component,
    val tabListOrder: Int = 0,
    val isSpectator: Boolean = false,
    val teamName: String = "",
) {
    internal val cleanDisplayName: String = displayName.cleanSkyBlockText()
    val skyBlockPlayerName: String?
        get() = skyBlockPlayerPattern.find(cleanDisplayName)?.groups?.get("name")?.value
}

internal fun Collection<TabListEntry>.sortedForDisplay(): List<TabListEntry> =
    sortedWith(
        compareByDescending<TabListEntry> { it.tabListOrder }
            .thenBy { it.isSpectator }
            .thenBy { it.teamName }
            .thenBy(String.CASE_INSENSITIVE_ORDER) { it.profileName },
    ).take(MAX_RENDERED_TAB_ENTRIES)

private val skyBlockPlayerPattern = Regex(
    """^\[\d+] (?:\[[^]]+] )?(?<name>[A-Za-z0-9_]{1,16})(?:\s|$)""",
)
private const val SKYBLOCK_AREA_PREFIX = "Area:"
private const val MAX_RENDERED_TAB_ENTRIES = 80
