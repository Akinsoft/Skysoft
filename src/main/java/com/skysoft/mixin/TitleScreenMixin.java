package com.skysoft.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.realmsclient.gui.screens.RealmsNotificationsScreen;
import com.skysoft.features.misc.HideSillyButtons;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(TitleScreen.class)
public class TitleScreenMixin {
    @WrapOperation(method = "extractRenderState", at = @At(value = "INVOKE", target = "Lcom/mojang/realmsclient/gui/screens/RealmsNotificationsScreen;extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V"))
    private void skysoftPositionRealmsNotifications(RealmsNotificationsScreen notifications, GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta, Operation<Void> original) {
        AbstractWidget button = HideSillyButtons.realmsButton((TitleScreen) (Object) this);
        if (button == null || notifications.height < 0) {
            original.call(notifications, graphics, mouseX, mouseY, delta);
            return;
        }
        int offsetX = button.getX() + button.getWidth() - (notifications.width / 2 + 100);
        int offsetY = button.getY() - (notifications.height / 4 + 96);
        graphics.pose().pushMatrix();
        try {
            graphics.pose().translate(offsetX, offsetY);
            original.call(notifications, graphics, mouseX - offsetX, mouseY - offsetY, delta);
        } finally {
            graphics.pose().popMatrix();
        }
    }

    @Inject(method = "realmsNotificationsEnabled", at = @At("HEAD"), cancellable = true)
    private void skysoftHideRealmsNotifications(CallbackInfoReturnable<Boolean> cir) {
        if (HideSillyButtons.shouldHideRealmsNotifications()) {
            cir.setReturnValue(false);
        }
    }
}
