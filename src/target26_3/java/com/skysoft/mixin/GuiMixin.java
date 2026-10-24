package com.skysoft.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.platform.Window;
import com.skysoft.gui.scale.GuiScaleController;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Gui.class)
public class GuiMixin {
    @Shadow
    @Final
    private Minecraft minecraft;

    @WrapOperation(
        method = "extractRenderState",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/screens/Screen;extractRenderStateWithTooltipAndSubtitles(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V"
        )
    )
    private void skysoftExtractInventoryAtSeparateScale(
        Screen screen,
        GuiGraphicsExtractor graphics,
        int mouseX,
        int mouseY,
        float delta,
        Operation<Void> original
    ) {
        Window window = minecraft.getWindow();
        if (!GuiScaleController.usesSeparateInventoryScale(screen)) {
            GuiScaleController.restoreScreenDimensions(screen, window);
            original.call(screen, graphics, mouseX, mouseY, delta);
            return;
        }

        GuiRenderState screenRenderState = new GuiRenderState();
        GuiRenderState aboveScreenRenderState = new GuiRenderState();
        try (GuiScaleController.WindowScaleOverride ignored = GuiScaleController.useInventoryScale(screen, window)) {
            skysoftSyncWindowScale(window);
            GuiScaleController.updateScreenDimensions(screen, window);
            int scaledMouseX = (int) minecraft.mouseHandler.getScaledXPos(window);
            int scaledMouseY = (int) minecraft.mouseHandler.getScaledYPos(window);
            GuiGraphicsExtractor scaledGraphics = new GuiGraphicsExtractor(
                minecraft,
                screenRenderState,
                scaledMouseX,
                scaledMouseY
            );
            original.call(screen, scaledGraphics, scaledMouseX, scaledMouseY, delta);
            ((GuiGraphicsExtractorAccessor) graphics).skysoftSetGuiRenderState(aboveScreenRenderState);
            GuiScaleController.submitRenderBatch(screenRenderState, aboveScreenRenderState);
        } finally {
            skysoftSyncWindowScale(window);
        }
    }

    @Unique
    private void skysoftSyncWindowScale(Window window) {
        minecraft.gameRenderer.gameRenderState().windowRenderState.guiScale = window.getGuiScale();
    }
}
