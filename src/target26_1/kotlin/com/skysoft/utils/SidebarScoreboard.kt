package com.skysoft.utils

import com.skysoft.mixin.ScoreboardHudAccessor
import com.skysoft.utils.TextUtilities.cleanSkyBlockText
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component
import net.minecraft.world.scores.DisplaySlot
import net.minecraft.world.scores.Objective
import net.minecraft.world.scores.PlayerScoreEntry
import net.minecraft.world.scores.PlayerTeam

object SidebarScoreboard {
    internal fun readCurrent(): SidebarScoreboardSnapshot {
        val objective = currentObjective() ?: return SidebarScoreboardSnapshot()
        val scoreboard = objective.scoreboard
        val entries = visibleEntries(objective)
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
        val teamSlot = scoreboard.getPlayersTeam(player.scoreboardName)
            ?.color
            ?.let(DisplaySlot::teamColorToSlot)
        return teamSlot?.let(scoreboard::getDisplayObjective)
            ?: scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR)
    }

    private fun visibleEntries(objective: Objective): List<PlayerScoreEntry> =
        objective.scoreboard.listPlayerScores(objective)
            .asSequence()
            .filterNot { it.isHidden }
            .sortedWith(SCORE_DISPLAY_ORDER)
            .take(MAX_ENTRIES)
            .toList()

    internal fun render(context: GuiGraphicsExtractor, objective: Objective) {
        (Minecraft.getInstance().gui as ScoreboardHudAccessor).skysoftDisplayScoreboardSidebar(context, objective)
    }
}

private val SCORE_DISPLAY_ORDER = compareByDescending<PlayerScoreEntry> { it.value() }
    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.owner() }
private const val MAX_ENTRIES = 15
