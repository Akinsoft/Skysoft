package com.skysoft.utils

import com.skysoft.mixin.ScoreboardHudAccessor
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.world.scores.DisplaySlot
import net.minecraft.world.scores.Objective
import net.minecraft.world.scores.PlayerTeam

object SidebarScoreboard {
    internal fun teamSlot(team: PlayerTeam): DisplaySlot? = team.color.orElse(null)?.displaySlot()

    internal fun render(context: GuiGraphicsExtractor, objective: Objective) {
        (Minecraft.getInstance().gui.hud as ScoreboardHudAccessor)
            .skysoftDisplayScoreboardSidebar(context, objective)
    }
}
