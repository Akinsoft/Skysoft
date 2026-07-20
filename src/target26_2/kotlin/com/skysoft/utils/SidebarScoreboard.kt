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
    fun currentObjective(): Objective? {
        val minecraft = Minecraft.getInstance()
        val level = minecraft.level ?: return null
        val player = minecraft.player ?: return null
        val scoreboard = level.scoreboard
        val teamSlot = scoreboard.getPlayersTeam(player.scoreboardName)
            ?.color
            ?.orElse(null)
            ?.displaySlot()
        return teamSlot?.let(scoreboard::getDisplayObjective)
            ?: scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR)
    }

    fun currentLines(): List<String> {
        val objective = currentObjective() ?: return emptyList()
        val scoreboard = objective.scoreboard
        return visibleEntries(objective).map { entry ->
            PlayerTeam.formatNameForTeam(scoreboard.getPlayersTeam(entry.owner()), Component.empty())
                .cleanSkyBlockText()
        }
    }

    fun visibleEntries(objective: Objective): List<PlayerScoreEntry> =
        objective.scoreboard.listPlayerScores(objective)
            .asSequence()
            .filterNot { it.isHidden }
            .sortedWith(SCORE_DISPLAY_ORDER)
            .take(MAX_ENTRIES)
            .toList()

    fun render(context: GuiGraphicsExtractor, objective: Objective) {
        (Minecraft.getInstance().gui.hud as ScoreboardHudAccessor)
            .skysoftDisplayScoreboardSidebar(context, objective)
    }
}

private val SCORE_DISPLAY_ORDER = compareByDescending<PlayerScoreEntry> { it.value() }
    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.owner() }
private const val MAX_ENTRIES = 15
