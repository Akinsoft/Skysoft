package com.skysoft.features.misc

import com.skysoft.mixin.ScoreboardHudAccessor
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.world.scores.DisplaySlot
import net.minecraft.world.scores.Objective

object VanillaScoreboardHud {
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

    fun render(context: GuiGraphicsExtractor, objective: Objective) {
        (Minecraft.getInstance().gui.hud as ScoreboardHudAccessor)
            .skysoftDisplayScoreboardSidebar(context, objective)
    }
}
