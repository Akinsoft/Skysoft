package com.skysoft.mixin;

import com.skysoft.utils.mixin.MixinErrorBoundary;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.skysoft.gui.scale.GuiScaleController;
import com.skysoft.gui.tooltip.AdjacentTooltipRenderer;
import com.skysoft.gui.tooltip.TooltipViewport;
import com.skysoft.utils.MinecraftClient;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
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
        AtomicBoolean called = new AtomicBoolean();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        MixinErrorBoundary.run("Tooltip GUI scale rendering", () -> skysoftRenderTooltipAtSeparateScale(graphics, x, y, point -> {
            called.set(true);
            try { original.call(graphics, font, tooltip, point[0], point[1], positioner, sprite); }
            catch (Throwable throwable) { failure.set(throwable); }
        }));
        if (!called.get()) original.call(graphics, font, tooltip, x, y, positioner, sprite);
        if (failure.get() != null) throwUnchecked(failure.get());
    }

    private void skysoftRenderTooltipAtSeparateScale(GuiGraphicsExtractor graphics, int x, int y, java.util.function.Consumer<int[]> render) {
        Minecraft minecraft = Minecraft.getInstance();
        Screen screen = MinecraftClient.INSTANCE.screen(minecraft);
        if (!GuiScaleController.usesSeparateTooltipScale(screen)) { render.accept(new int[] {x, y}); return; }
        var window = minecraft.getWindow();
        int tooltipScale = GuiScaleController.resolve(screen, window).tooltip();
        if (window.getGuiScale() == tooltipScale) { render.accept(new int[] {x, y}); return; }
        int activeScale = Math.max(1, window.getGuiScale());
        int tooltipX = GuiScaleController.convertCoordinate(x, activeScale, tooltipScale);
        int tooltipY = GuiScaleController.convertCoordinate(y, activeScale, tooltipScale);
        float poseScale = tooltipScale / (float) activeScale;
        graphics.pose().pushMatrix();
        try (var ignored = GuiScaleController.useTooltipScale(screen, window)) {
            graphics.pose().scale(poseScale, poseScale);
            render.accept(new int[] {tooltipX, tooltipY});
        } finally { graphics.pose().popMatrix(); }
    }

    @WrapOperation(method = "tooltip(Lnet/minecraft/client/gui/Font;Ljava/util/List;IILnet/minecraft/client/gui/screens/inventory/tooltip/ClientTooltipPositioner;Lnet/minecraft/resources/Identifier;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/inventory/tooltip/ClientTooltipPositioner;positionTooltip(IIIIII)Lorg/joml/Vector2ic;"))
    protected Vector2ic skysoftPositionScrollableTooltip(ClientTooltipPositioner positioner, int screenWidth, int screenHeight, int x, int y, int tooltipWidth, int tooltipHeight, Operation<Vector2ic> original, @Local(argsOnly = true) Font font, @Local(argsOnly = true) List<ClientTooltipComponent> tooltip) {
        ClientTooltipPositioner scrolling = MixinErrorBoundary.value("Scrollable tooltip positioning", positioner, () -> TooltipViewport.decorate(font, tooltip, x, y, positioner));
        Vector2ic result = original.call(scrolling, screenWidth, screenHeight, x, y, tooltipWidth, tooltipHeight);
        AdjacentTooltipRenderer.INSTANCE.captureMainFrame((GuiGraphicsExtractor) (Object) this, result, tooltipWidth);
        return result;
    }

    private static void throwUnchecked(Throwable throwable) { TooltipGuiScaleMixin.<RuntimeException>throwAny(throwable); }
    private static <T extends Throwable> void throwAny(Throwable throwable) throws T { throw (T) throwable; }
}
