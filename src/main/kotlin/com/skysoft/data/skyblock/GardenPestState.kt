package com.skysoft.data.skyblock

import com.skysoft.data.SkyBlockIsland
import com.skysoft.data.hypixel.HypixelLocationState
import com.skysoft.utils.ActiveListenerRegistry
import com.skysoft.utils.ActiveStatePublisher
import com.skysoft.utils.SidebarScoreboardState
import com.skysoft.utils.chat.ChatEvents
import com.skysoft.utils.chat.ChatMessageVisibility

object GardenPestState {
    private val publisher = ActiveStatePublisher("Garden Pest State", GardenPestSnapshot())
    private val spawnListeners = ActiveListenerRegistry<(GardenPestSpawn) -> Unit>()
    private val killListeners = ActiveListenerRegistry<(String) -> Unit>()
    private var lastScoreboardPestCount: Int? = null
    private var unmatchedChatKills = 0
    private var unmatchedScoreboardKills = 0

    val current: GardenPestSnapshot
        get() = publisher.state

    fun register() {
        publisher.register()
        ChatEvents.onVisibleMessage(
            "Garden Pest State chat",
            isActive = { SkyBlockIsland.GARDEN.isInIsland() },
        ) { message ->
            if (message.isSystemLike) {
                parseSpawn(message.cleanText)?.let(::recordSpawn)
                parseGardenPestKill(message.cleanText)?.takeIf { it.countsAsKill }?.let { recordKill(it.pest) }
            }
            ChatMessageVisibility.SHOW
        }
        SidebarScoreboardState.onChange(
            "Garden Pest State scoreboard",
            isActive = { SkyBlockIsland.GARDEN.isInIsland() },
            listener = ::updateScoreboard,
        )
        HypixelLocationState.onChange(
            "Garden Pest State location",
            isActive = { SkyBlockIsland.GARDEN.isInIsland() || current != GardenPestSnapshot() },
        ) { location ->
            if (location.currentIsland != SkyBlockIsland.GARDEN) reset()
        }
    }

    fun onChange(boundary: String, isActive: () -> Boolean, listener: (GardenPestSnapshot) -> Unit) {
        publisher.onChange(boundary, isActive, listener)
    }

    fun onSpawn(boundary: String, isActive: () -> Boolean, listener: (GardenPestSpawn) -> Unit) {
        spawnListeners.register(boundary, isActive, listener)
    }

    fun onKill(boundary: String, isActive: () -> Boolean, listener: (String) -> Unit) {
        killListeners.register(boundary, isActive, listener)
    }

    private fun recordSpawn(spawn: GardenPestSpawn) {
        clearUnmatchedKills()
        val knownPests = current.knownPestsByPlot.toMutableMap()
        knownPests[spawn.plot] = maxOf(knownPests[spawn.plot] ?: 0, spawn.amount)
        publisher.update(
            current.copy(
                totalPests = maxOf(current.totalPests ?: 0, knownPests.values.sum()),
                pestsInCurrentPlot = if (current.currentPlot == spawn.plot) {
                    maxOf(current.pestsInCurrentPlot ?: 0, knownPests.getValue(spawn.plot))
                } else {
                    current.pestsInCurrentPlot
                },
                knownPestsByPlot = knownPests.toMap(),
                lastSpawn = spawn,
            ),
        )
        spawnListeners.forEachActive { listener -> listener(spawn) }
    }

    private fun recordKill(pest: String) {
        if (unmatchedScoreboardKills > 0) {
            unmatchedScoreboardKills--
            killListeners.forEachActive { listener -> listener(pest) }
            return
        }
        val totalPests = current.totalPests?.let { (it - 1).coerceAtLeast(0) }
        if (current.totalPests?.let { it > 0 } == true) unmatchedChatKills++
        val knownPests = current.knownPestsByPlot.toMutableMap()
        current.currentPlot?.let { plot ->
            knownPests[plot]?.let { pests ->
                if (pests <= 1) knownPests.remove(plot) else knownPests[plot] = pests - 1
            }
        }
        publisher.update(
            current.copy(
                totalPests = totalPests,
                pestsInCurrentPlot = current.pestsInCurrentPlot?.let { (it - 1).coerceAtLeast(0) },
                knownPestsByPlot = if (totalPests == 0) emptyMap() else knownPests.toMap(),
                lastSpawn = current.lastSpawn.takeUnless { totalPests == 0 },
            ),
        )
        killListeners.forEachActive { listener -> listener(pest) }
    }

    private fun updateScoreboard(lines: List<String>) {
        val garden = lines.firstNotNullOfOrNull { line -> GARDEN_PATTERN.matchEntire(line.trim()) } ?: return
        val scoreboardPestCount = garden.groups["count"]?.value?.toInt() ?: 0
        val totalPests = reconcileScoreboardPestCount(scoreboardPestCount)
        val plotWithPests = lines.firstNotNullOfOrNull { line -> PLOT_PESTS_PATTERN.matchEntire(line.trim()) }
        val plotWithoutPests = if (plotWithPests == null) {
            lines.firstNotNullOfOrNull { line -> PLOT_PATTERN.matchEntire(line.trim()) }
        } else {
            null
        }
        val plot = (plotWithPests ?: plotWithoutPests)?.groups?.get("plot")?.value?.trim()
        val scoreboardPestsInPlot = when {
            plotWithPests != null -> plotWithPests.groups["count"]?.value?.toInt()
            plotWithoutPests != null -> 0
            else -> null
        }
        val pestsInPlot = scoreboardPestsInPlot?.let { (it - unmatchedChatKills).coerceAtLeast(0) }
        val knownPests = current.knownPestsByPlot.toMutableMap()
        if (plot != null && pestsInPlot != null) {
            if (pestsInPlot == 0) knownPests.remove(plot) else knownPests[plot] = pestsInPlot
        }
        publisher.update(
            GardenPestSnapshot(
                totalPests = totalPests,
                currentPlot = plot,
                pestsInCurrentPlot = pestsInPlot,
                knownPestsByPlot = if (totalPests == 0) emptyMap() else knownPests.toMap(),
                lastSpawn = current.lastSpawn.takeIf { totalPests > 0 },
            ),
        )
    }

    private fun reconcileScoreboardPestCount(scoreboardPestCount: Int): Int {
        val previousPestCount = lastScoreboardPestCount
        lastScoreboardPestCount = scoreboardPestCount
        if (previousPestCount == null || scoreboardPestCount > previousPestCount) {
            clearUnmatchedKills()
            return scoreboardPestCount
        }
        val scoreboardKills = previousPestCount - scoreboardPestCount
        val matchedKills = minOf(scoreboardKills, unmatchedChatKills)
        unmatchedChatKills -= matchedKills
        unmatchedScoreboardKills += scoreboardKills - matchedKills
        return (scoreboardPestCount - unmatchedChatKills).coerceAtLeast(0)
    }

    private fun clearUnmatchedKills() {
        unmatchedChatKills = 0
        unmatchedScoreboardKills = 0
    }

    private fun reset() {
        lastScoreboardPestCount = null
        clearUnmatchedKills()
        publisher.update(GardenPestSnapshot())
    }

    private fun parseSpawn(message: String): GardenPestSpawn? {
        ONE_PEST_PATTERN.matchEntire(message)?.let { match ->
            return GardenPestSpawn(1, match.groups["location"]!!.value.toPlotName())
        }
        val match = MULTIPLE_PESTS_PATTERN.matchEntire(message) ?: return null
        return GardenPestSpawn(
            amount = match.groups["amount"]!!.value.toInt(),
            plot = match.groups["location"]!!.value.toPlotName(),
        )
    }

    private fun String.toPlotName(): String = removePrefix("Plot - ").trim()

    private val GARDEN_PATTERN = Regex("[⏣\\uE067] The Garden(?: [\\uE07F\\uE018] x(?<count>\\d+))?")
    private val PLOT_PESTS_PATTERN =
        Regex("(?:[⏣\\uE067] )?Plot - (?<plot>.+) [\\uE07F\\uE018] x(?<count>\\d+)")
    private val PLOT_PATTERN = Regex("(?:[⏣\\uE067] )?Plot - (?<plot>.+)")
    private val ONE_PEST_PATTERN =
        Regex("\\w+! A [\\uE07F\\uE018] Pest has appeared in (?<location>(?:Plot - )?.+)!")
    private val MULTIPLE_PESTS_PATTERN =
        Regex("\\w+! (?<amount>\\d+) [\\uE07F\\uE018] Pests? have spawned in (?<location>(?:Plot - )?.+)!")
}

data class GardenPestSnapshot(
    val totalPests: Int? = null,
    val currentPlot: String? = null,
    val pestsInCurrentPlot: Int? = null,
    val knownPestsByPlot: Map<String, Int> = emptyMap(),
    val lastSpawn: GardenPestSpawn? = null,
)

data class GardenPestSpawn(
    val amount: Int,
    val plot: String,
)
