package com.skysoft.mixin

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod
import com.llamalad7.mixinextras.injector.wrapoperation.Operation
import com.skysoft.features.misc.ScoreboardPositionEditor
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.Hud
import net.minecraft.world.scores.Objective
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.gen.Invoker

@Mixin(Hud::class)
interface ScoreboardHudAccessor {
    @Invoker("displayScoreboardSidebar")
    fun skysoftDisplayScoreboardSidebar(context: GuiGraphicsExtractor, objective: Objective)
}

@Mixin(Hud::class)
abstract class ScoreboardHudMixin {
    @WrapMethod(method = ["displayScoreboardSidebar"])
    private fun skysoftPositionScoreboard(
        context: GuiGraphicsExtractor,
        objective: Objective,
        original: Operation<Void>,
    ) {
        ScoreboardPositionEditor.render(context, objective) {
            original.call(context, objective)
        }
    }
}
