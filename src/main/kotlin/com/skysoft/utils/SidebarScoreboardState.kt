package com.skysoft.utils

import com.skysoft.utils.TextUtilities.cleanSkyBlockText
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.world.scores.DisplaySlot
import net.minecraft.world.scores.Objective
import net.minecraft.world.scores.PlayerScoreEntry
import net.minecraft.world.scores.PlayerTeam

object SidebarScoreboardState {
    private val publisher = ActiveStatePublisher("Sidebar Scoreboard State", SidebarScoreboardSnapshot())
    private var dirty = true

    internal val current: SidebarScoreboardSnapshot
        get() = publisher.state

    val title: String
        get() = publisher.state.title

    fun register() {
        publisher.register()
        SkysoftClientEvents.onEndTick(
            "Sidebar scoreboard state",
            isActive = { dirty },
        ) { refresh() }
        SkysoftClientEvents.onJoin("Sidebar scoreboard state join", ::markDirty)
        SkysoftClientEvents.onDisconnect("Sidebar scoreboard state reset", ::reset)
    }

    fun onChange(boundary: String, isActive: () -> Boolean, listener: (List<String>) -> Unit) {
        publisher.onChange(boundary, isActive) { snapshot -> listener(snapshot.lines) }
    }

    internal fun markDirty() {
        dirty = true
    }

    private fun refresh() {
        dirty = false
        publisher.update(readCurrent())
    }

    private fun readCurrent(): SidebarScoreboardSnapshot {
        val objective = currentObjective() ?: return SidebarScoreboardSnapshot()
        val scoreboard = objective.scoreboard
        val entries = scoreboard.listPlayerScores(objective)
            .asSequence()
            .filterNot { it.isHidden }
            .sortedWith(SCORE_DISPLAY_ORDER)
            .take(MAX_ENTRIES)
            .toList()
        return SidebarScoreboardSnapshot(
            objective = objective,
            entries = entries,
            lines = entries.map { entry ->
                PlayerTeam.formatNameForTeam(scoreboard.getPlayersTeam(entry.owner()), Component.empty())
                    .cleanSkyBlockText()
            },
        )
    }

    private fun currentObjective(): Objective? {
        val minecraft = Minecraft.getInstance()
        val level = minecraft.level ?: return null
        val player = minecraft.player ?: return null
        val scoreboard = level.scoreboard
        val teamSlot = scoreboard.getPlayersTeam(player.scoreboardName)?.let(SidebarScoreboard::teamSlot)
        return teamSlot?.let(scoreboard::getDisplayObjective)
            ?: scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR)
    }

    private fun reset() {
        dirty = false
        publisher.update(SidebarScoreboardSnapshot())
    }
}

private val SCORE_DISPLAY_ORDER = compareByDescending<PlayerScoreEntry> { it.value() }
    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.owner() }
private const val MAX_ENTRIES = 15

internal data class SidebarScoreboardSnapshot(
    val objective: Objective? = null,
    val title: String = objective?.displayName?.cleanSkyBlockText().orEmpty(),
    val entries: List<PlayerScoreEntry> = emptyList(),
    val lines: List<String> = emptyList(),
)
