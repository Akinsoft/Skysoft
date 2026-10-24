package com.skysoft.mixin;

import com.skysoft.features.inventory.SelectedItemDropProtection;
import com.skysoft.utils.mixin.MixinErrorBoundary;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MultiPlayerGameMode.class)
public class LocalPlayerSlotLockMixin {
    @Inject(method = "dropItem", at = @At("HEAD"), cancellable = true)
    private void skysoftProtectLockedSelectedSlot(LocalPlayer player, boolean all, CallbackInfo ci) {
        boolean isBlocked = MixinErrorBoundary.value("Selected item drop protection", false, () -> SelectedItemDropProtection.shouldBlock(player));
        if (isBlocked) ci.cancel();
    }
}
