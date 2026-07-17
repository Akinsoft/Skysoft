// SPDX-License-Identifier: LGPL-2.1-only
// Adapted from SkyHanni; see credits.md for attribution and source details.

package com.skysoft.mixin

import com.skysoft.features.pets.PetStorageService
import com.skysoft.utils.SkysoftErrorBoundary
import com.skysoft.utils.input.InputEventInterceptor
import com.skysoft.utils.input.InputHandlingResult
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.MultiPlayerGameMode
import net.minecraft.world.phys.HitResult
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.Shadow
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable

@Mixin(Minecraft::class)
open class MinecraftInputMixin {
    @field:Shadow
    @JvmField
    var hitResult: HitResult? = null

    @field:Shadow
    private var missTime = 0

    @field:Shadow
    @JvmField
    var gameMode: MultiPlayerGameMode? = null

    @Inject(method = ["startUseItem"], at = [At("HEAD")], cancellable = true)
    protected fun skysoftHandleRightClickMouse(ci: CallbackInfo) {
        if (gameMode?.isDestroying == true) return

        SkysoftErrorBoundary.run("Pet Storage item use", PetStorageService::onUseItem)
        val result = SkysoftErrorBoundary.value(
            "Right click input",
            InputHandlingResult.IGNORED,
        ) { InputEventInterceptor.processRightClick(hitResult) }
        if (result == InputHandlingResult.CONSUMED) {
            ci.cancel()
        }
    }

    @Inject(method = ["startAttack"], at = [At("HEAD")], cancellable = true)
    protected fun skysoftHandleLeftClickMouse(cir: CallbackInfoReturnable<Boolean>) {
        if (missTime > 0) return

        val result = SkysoftErrorBoundary.value(
            "Left click input",
            InputHandlingResult.IGNORED,
        ) { InputEventInterceptor.processLeftClick(hitResult) }
        if (result == InputHandlingResult.CONSUMED) {
            cir.setReturnValue(false)
        }
    }
}
