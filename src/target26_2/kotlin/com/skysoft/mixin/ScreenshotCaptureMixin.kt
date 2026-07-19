package com.skysoft.mixin

import com.skysoft.features.screenshot.ScreenshotManager
import java.util.function.Consumer
import net.minecraft.client.Screenshot
import net.minecraft.network.chat.Component
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.ModifyArg

@Mixin(Screenshot::class)
abstract class ScreenshotCaptureMixin private constructor() {
    private companion object {
        @JvmStatic
        @ModifyArg(
            method = ["grab(Lnet/minecraft/client/Minecraft;Z)V"],
            at = At(
                value = "INVOKE",
                target = "Lnet/minecraft/client/Screenshot;grab(" +
                    "Ljava/io/File;Lcom/mojang/blaze3d/pipeline/RenderTarget;Ljava/util/function/Consumer;)V",
            ),
            index = 2,
        )
        private fun skysoftCustomizeScreenshotCapture(callback: Consumer<Component>): Consumer<Component> =
            ScreenshotManager.decorateCaptureCallback(callback)
    }
}
