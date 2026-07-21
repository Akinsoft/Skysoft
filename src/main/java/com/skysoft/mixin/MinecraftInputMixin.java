// SPDX-License-Identifier: LGPL-2.1-only
// Adapted from SkyHanni; see credits.md for attribution and source details.

package com.skysoft.mixin;

import com.skysoft.features.pets.PetStorageService;
import com.skysoft.utils.input.InputEventInterceptor;
import com.skysoft.utils.input.InputHandlingResult;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.phys.HitResult;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public class MinecraftInputMixin {
    @Shadow
    public HitResult hitResult;

    @Shadow
    private int missTime;

    @Shadow
    @Nullable
    public MultiPlayerGameMode gameMode;

    @Inject(
        method = "startUseItem",
        at = @At("HEAD"),
        cancellable = true
    )
    private void skysoftHandleRightClickMouse(CallbackInfo ci) {
        if (this.gameMode != null && this.gameMode.isDestroying()) {
            return;
        }

        PetStorageService.onUseItem();
        if (InputEventInterceptor.processRightClick(this.hitResult) == InputHandlingResult.CONSUMED) {
            ci.cancel();
        }
    }

    @Inject(
        method = "startAttack",
        at = @At("HEAD"),
        cancellable = true
    )
    private void skysoftHandleLeftClickMouse(CallbackInfoReturnable<Boolean> cir) {
        if (this.missTime > 0) {
            return;
        }

        if (InputEventInterceptor.processLeftClick(this.hitResult) == InputHandlingResult.CONSUMED) {
            cir.setReturnValue(false);
        }
    }
}
