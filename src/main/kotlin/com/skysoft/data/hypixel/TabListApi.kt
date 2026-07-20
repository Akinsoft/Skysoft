package com.skysoft.data.hypixel

import com.skysoft.utils.ActiveConsumerRegistry
import com.skysoft.utils.ElapsedTimeMark
import com.skysoft.utils.SkysoftClientEvents
import com.skysoft.utils.TabListOverlay
import com.skysoft.utils.TextUtilities.cleanSkyBlockText
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.world.level.GameType
import net.minecraft.world.scores.PlayerTeam
import java.util.Collections
import java.util.UUID
import kotlin.time.Duration

object TabListApi {
    private const val SKYBLOCK_DATA_READY_READS = 2
    private val skyBlockAreaPattern = Regex("""Area: .+""")

    private var cachedLines: List<Component> = emptyList()
    private var cachedEntries: List<TabListEntry> = emptyList()
    private var cachedHeader: Component? = null
    private var cachedFooter: Component? = null
    private var loaded = false
    private var skyBlockDataLoaded = false
    private var skyBlockDataReads = 0
    private var lastLocationVersion = Long.MIN_VALUE
    private var ticks = 0
    private var skyBlockDataLoadStartedAt = ElapsedTimeMark.farPast()
    private val consumers = ActiveConsumerRegistry()
    private var wasActive = false

    var sessionId: Long = 0
        private set

    var contentVersion: Long = 0
        private set

    val isLoaded: Boolean
        get() = HypixelLocationState.inSkyBlock && loaded

    val lines: List<Component>
        get() = if (isLoaded) cachedLines else emptyList()

    val entries: List<TabListEntry>
        get() = if (isLoaded) cachedEntries else emptyList()

    val header: Component?
        get() = if (isLoaded) cachedHeader else null

    val footer: Component?
        get() = if (isLoaded) cachedFooter else null

    val isSkyBlockDataLoaded: Boolean
        get() = isLoaded && skyBlockDataLoaded

    val skyBlockLines: List<Component>
        get() = if (isSkyBlockDataLoaded) cachedLines else emptyList()

    val skyBlockHeader: Component?
        get() = if (isSkyBlockDataLoaded) cachedHeader else null

    val skyBlockFooter: Component?
        get() = if (isSkyBlockDataLoaded) cachedFooter else null

    val skyBlockAreaName: String?
        get() = parseSkyBlockTabArea(skyBlockLines.map { it.cleanSkyBlockText() })

    fun register() {
        SkysoftClientEvents.onEndTick(
            "Tab List update",
            isActive = { consumers.hasActiveConsumers || wasActive },
        ) {
            onClientTick()
        }
        SkysoftClientEvents.onDisconnect("Tab List reset") {
            resetSession()
            wasActive = false
        }
    }

    fun registerConsumer(id: String, isActive: () -> Boolean) {
        consumers.register(id, isActive)
    }

    internal val hasActiveConsumers: Boolean
        get() = consumers.hasActiveConsumers

    fun hasWaitedForSkyBlockData(duration: Duration): Boolean =
        HypixelLocationState.inSkyBlock && skyBlockDataLoadStartedAt.passedSince() >= duration

    private fun onClientTick() {
        val isActive = consumers.hasActiveConsumers
        if (!isActive) {
            if (wasActive) resetSession()
            wasActive = false
            return
        }
        if (!wasActive) {
            resetSession()
            wasActive = true
        }
        if (!HypixelLocationState.inSkyBlock) {
            ticks = 0
            return
        }

        if (lastLocationVersion != HypixelLocationState.locationVersion) {
            resetSession()
            lastLocationVersion = HypixelLocationState.locationVersion
        }

        if (++ticks % READ_INTERVAL_TICKS != 0) return
        val nextEntries = readTabList()
        if (nextEntries.isEmpty()) {
            clearLoadedLines()
            return
        }

        val nextLines = nextEntries.map { it.displayName }
        val minecraft = Minecraft.getInstance()
        val nextHeader = TabListOverlay.readHeader(minecraft)
        val nextFooter = TabListOverlay.readFooter(minecraft)
        if (nextLines != cachedLines || nextHeader != cachedHeader || nextFooter != cachedFooter) contentVersion++
        cachedEntries = Collections.unmodifiableList(nextEntries)
        cachedLines = Collections.unmodifiableList(nextLines)
        cachedHeader = nextHeader
        cachedFooter = nextFooter
        loaded = true
        updateSkyBlockDataLoadState(nextLines)
    }

    private fun resetSession() {
        sessionId++
        if (cachedLines.isNotEmpty() || cachedHeader != null || cachedFooter != null) contentVersion++
        cachedLines = emptyList()
        cachedEntries = emptyList()
        cachedHeader = null
        cachedFooter = null
        loaded = false
        skyBlockDataLoaded = false
        skyBlockDataReads = 0
        ticks = 0
        skyBlockDataLoadStartedAt = ElapsedTimeMark.now()
    }

    private fun clearLoadedLines() {
        if (!loaded) return
        contentVersion++
        cachedLines = emptyList()
        cachedEntries = emptyList()
        cachedHeader = null
        cachedFooter = null
        loaded = false
        resetSkyBlockDataLoad()
    }

    private fun updateSkyBlockDataLoadState(lines: List<Component>) {
        if (!hasSkyBlockData(lines)) {
            resetSkyBlockDataLoad()
            return
        }
        skyBlockDataReads = (skyBlockDataReads + 1).coerceAtMost(SKYBLOCK_DATA_READY_READS)
        skyBlockDataLoaded = skyBlockDataReads >= SKYBLOCK_DATA_READY_READS
    }

    private fun resetSkyBlockDataLoad() {
        if (skyBlockDataLoaded || skyBlockDataReads > 0) {
            skyBlockDataLoadStartedAt = ElapsedTimeMark.now()
        }
        skyBlockDataLoaded = false
        skyBlockDataReads = 0
    }

    private fun hasSkyBlockData(lines: List<Component>): Boolean {
        val cleanLines = lines.map { it.cleanSkyBlockText() }
        return cleanLines.any { it == "Info" } && cleanLines.any { skyBlockAreaPattern.matches(it) }
    }

    private fun readTabList(): List<TabListEntry> {
        val connection = Minecraft.getInstance().connection ?: return emptyList()
        return connection.listedOnlinePlayers.map { playerInfo ->
            val isSpectator = playerInfo.gameMode == GameType.SPECTATOR
            val displayName = playerInfo.tabListDisplayName?.copy()
                ?: PlayerTeam.formatNameForTeam(playerInfo.team, Component.literal(playerInfo.profile.name))
            TabListEntry(
                uuid = playerInfo.profile.id,
                profileName = playerInfo.profile.name,
                displayName = if (isSpectator) displayName.copy().withStyle(ChatFormatting.ITALIC) else displayName,
                tabListOrder = playerInfo.tabListOrder,
                isSpectator = isSpectator,
                teamName = playerInfo.team?.name.orEmpty(),
            )
        }.sortedForDisplay()
    }

    private const val READ_INTERVAL_TICKS = 5
}

internal fun parseSkyBlockTabArea(lines: Iterable<String>): String? = lines.firstNotNullOfOrNull { line ->
    line.trim().takeIf { it.startsWith(SKYBLOCK_AREA_PREFIX) }
        ?.removePrefix(SKYBLOCK_AREA_PREFIX)
        ?.trim()
        ?.takeIf(String::isNotEmpty)
}

data class TabListEntry(
    val uuid: UUID,
    val profileName: String,
    val displayName: Component,
    val tabListOrder: Int = 0,
    val isSpectator: Boolean = false,
    val teamName: String = "",
)

internal fun Collection<TabListEntry>.sortedForDisplay(): List<TabListEntry> =
    sortedWith(
        compareByDescending<TabListEntry> { it.tabListOrder }
            .thenBy { it.isSpectator }
            .thenBy { it.teamName }
            .thenBy(String.CASE_INSENSITIVE_ORDER) { it.profileName },
    ).take(MAX_RENDERED_TAB_ENTRIES)

private const val SKYBLOCK_AREA_PREFIX = "Area:"
private const val MAX_RENDERED_TAB_ENTRIES = 80
