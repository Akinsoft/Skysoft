package com.skysoft.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.skysoft.features.misc.TabListPositionEditor;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Scoreboard;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(PlayerTabOverlay.class)
public abstract class PlayerTabOverlayMixin {
    @WrapMethod(method = "extractRenderState")
    private void skysoftPositionTabList(GuiGraphicsExtractor context, int screenWidth, Scoreboard scoreboard, Objective objective, Operation<Void> original) {
        TabListPositionEditor.INSTANCE.render(context, () -> { original.call(context, screenWidth, scoreboard, objective); return kotlin.Unit.INSTANCE; });
    }
}
