package com.skysoft.mixin;

import com.skysoft.utils.mixin.MixinFeatureAdapters;
import java.util.function.Consumer;
import net.minecraft.client.Screenshot;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(Screenshot.class)
public abstract class ScreenshotCaptureMixin {
    @ModifyArg(method = "grab(Lnet/minecraft/client/Minecraft;Z)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Screenshot;grab(Ljava/io/File;Lcom/mojang/blaze3d/pipeline/RenderTarget;Ljava/util/function/Consumer;)V"), index = 2)
    private static Consumer<Component> skysoftCustomizeScreenshotCapture(Consumer<Component> callback) { return MixinFeatureAdapters.decorateScreenshotCallback(callback); }
}
