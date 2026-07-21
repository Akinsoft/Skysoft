package com.skysoft.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.skysoft.features.misc.ScoreboardPositionEditor;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Gui;
import net.minecraft.world.scores.Objective;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(Gui.class)
public abstract class ScoreboardHudMixin {
    @WrapMethod(method = "displayScoreboardSidebar")
    private void skysoftPositionScoreboard(GuiGraphicsExtractor context, Objective objective, Operation<Void> original) {
        ScoreboardPositionEditor.INSTANCE.render(context, objective, () -> { original.call(context, objective); return kotlin.Unit.INSTANCE; });
    }
}
