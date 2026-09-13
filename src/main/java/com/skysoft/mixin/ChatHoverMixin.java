package com.skysoft.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.skysoft.gui.GuiOverlayRegistry;
import java.util.function.Consumer;
import net.minecraft.client.gui.ActiveTextCollector;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.chat.GuiMessageTag;
import net.minecraft.network.chat.Style;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.client.gui.components.ChatComponent$DrawingFocusedGraphicsAccess")
public abstract class ChatHoverMixin {
    @WrapOperation(method = "<init>", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;textRenderer(Lnet/minecraft/client/gui/GuiGraphicsExtractor$HoveredTextEffects;Ljava/util/function/Consumer;)Lnet/minecraft/client/gui/ActiveTextCollector;"))
    protected ActiveTextCollector skysoftBlockChatTextHover(GuiGraphicsExtractor graphics, GuiGraphicsExtractor.HoveredTextEffects effects, Consumer<Style> hoveredStyle, Operation<ActiveTextCollector> original) {
        boolean blocked = GuiOverlayRegistry.isPointerCovered();
        return original.call(graphics, blocked ? GuiGraphicsExtractor.HoveredTextEffects.NONE : effects, blocked ? null : hoveredStyle);
    }

    @Inject(method = "showTooltip", at = @At("HEAD"), cancellable = true)
    protected void skysoftBlockChatTagHover(GuiMessageTag tag, CallbackInfo ci) {
        if (GuiOverlayRegistry.isPointerCovered()) ci.cancel();
    }
}
