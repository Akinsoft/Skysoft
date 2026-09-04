package com.skysoft.mixin;

import com.skysoft.integration.ContainerInputHooks;
import com.skysoft.utils.MinecraftClient;
import com.skysoft.utils.input.InputUtilities;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class)
public class KeyboardHandlerMixin {
    @Inject(method = "keyPress", at = @At("HEAD"))
    protected void skysoftRecordKeyPressScreen(long window, int action, KeyEvent event, CallbackInfo ci) {
        InputUtilities.recordBindingInput(window, event.key(), action);
    }

    @Inject(method = "charTyped", at = @At("HEAD"), cancellable = true)
    protected void skysoftTypeContainerOverlay(long window, CharacterEvent event, CallbackInfo ci) {
        Screen current = MinecraftClient.INSTANCE.screen();
        if (current instanceof AbstractContainerScreen<?> screen && ContainerInputHooks.didConsumeCharacterInput(screen, event)) {
            ci.cancel();
        }
    }
}
