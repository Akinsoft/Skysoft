package com.skysoft.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.skysoft.gui.scale.TooltipScaleRenderer;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(GuiGraphicsExtractor.class)
public class TooltipGuiScaleMixin {
    @WrapOperation(method = "lambda$setTooltipForNextFrameInternal$0", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;tooltip(Lnet/minecraft/client/gui/Font;Ljava/util/List;IILnet/minecraft/client/gui/screens/inventory/tooltip/ClientTooltipPositioner;Lnet/minecraft/resources/Identifier;)V"))
    protected void skysoftRenderTooltipAtSeparateScale(GuiGraphicsExtractor graphics, Font font, List<ClientTooltipComponent> tooltip, int x, int y, ClientTooltipPositioner positioner, Identifier sprite, Operation<Void> original) {
        TooltipScaleRenderer.render(graphics, x, y,
            (tooltipX, tooltipY) -> original.call(graphics, font, tooltip, tooltipX, tooltipY, positioner, sprite));
    }
}
