package com.skysoft.mixin;

import com.skysoft.utils.mixin.MixinErrorBoundary;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.skysoft.gui.scale.TooltipScaleRenderer;
import com.skysoft.gui.tooltip.AdjacentTooltipRenderer;
import com.skysoft.gui.tooltip.TooltipViewport;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import net.minecraft.resources.Identifier;
import org.joml.Vector2ic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(GuiGraphicsExtractor.class)
public class TooltipGuiScaleMixin {
    @WrapOperation(method = "lambda$setTooltipForNextFrameInternal$0", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;tooltip(Lnet/minecraft/client/gui/Font;Ljava/util/List;IILnet/minecraft/client/gui/screens/inventory/tooltip/ClientTooltipPositioner;Lnet/minecraft/resources/Identifier;)V"))
    protected void skysoftRenderTooltipAtSeparateScale(GuiGraphicsExtractor graphics, Font font, List<ClientTooltipComponent> tooltip, int x, int y, ClientTooltipPositioner positioner, Identifier sprite, Operation<Void> original) {
        TooltipScaleRenderer.render(graphics, x, y,
            (tooltipX, tooltipY) -> original.call(graphics, font, tooltip, tooltipX, tooltipY, positioner, sprite));
    }

    @WrapOperation(method = "tooltip(Lnet/minecraft/client/gui/Font;Ljava/util/List;IILnet/minecraft/client/gui/screens/inventory/tooltip/ClientTooltipPositioner;Lnet/minecraft/resources/Identifier;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/inventory/tooltip/ClientTooltipPositioner;positionTooltip(IIIIII)Lorg/joml/Vector2ic;"))
    protected Vector2ic skysoftPositionScrollableTooltip(ClientTooltipPositioner positioner, int screenWidth, int screenHeight, int x, int y, int tooltipWidth, int tooltipHeight, Operation<Vector2ic> original, @Local(argsOnly = true) Font font, @Local(argsOnly = true) List<ClientTooltipComponent> tooltip) {
        ClientTooltipPositioner scrolling = MixinErrorBoundary.value("Scrollable tooltip positioning", positioner, () -> TooltipViewport.decorate(font, tooltip, x, y, positioner));
        Vector2ic result = original.call(scrolling, screenWidth, screenHeight, x, y, tooltipWidth, tooltipHeight);
        AdjacentTooltipRenderer.INSTANCE.captureMainFrame((GuiGraphicsExtractor) (Object) this, result, tooltipWidth);
        return result;
    }
}
