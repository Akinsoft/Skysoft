package com.skysoft.mixin;

import com.skysoft.features.misc.InputMath;
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractSignEditScreen.class)
public abstract class AbstractSignEditScreenMixin {
    @Shadow @Final private String[] messages;
    @Shadow public abstract void onClose();

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void skysoftSubmitInputMath(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
        if (!event.isConfirmation() || !skysoftCompileInputMath()) return;
        onClose();
        cir.setReturnValue(true);
    }

    @Inject(method = "onDone", at = @At("HEAD"))
    private void skysoftCompileInputMathBeforeSubmit(CallbackInfo ci) {
        skysoftCompileInputMath();
    }

    @Unique
    private boolean skysoftCompileInputMath() {
        String result = InputMath.compile(messages);
        if (result == null) return false;
        messages[0] = result;
        return true;
    }
}
