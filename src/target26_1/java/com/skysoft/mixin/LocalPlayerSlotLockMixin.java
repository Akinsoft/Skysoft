package com.skysoft.mixin;

import com.skysoft.features.inventory.SelectedItemDropProtection;
import com.skysoft.utils.mixin.MixinErrorBoundary;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LocalPlayer.class)
public class LocalPlayerSlotLockMixin {
    @Inject(method = "drop", at = @At("HEAD"), cancellable = true)
    private void skysoftProtectLockedSelectedSlot(boolean all, CallbackInfoReturnable<Boolean> cir) {
        LocalPlayer player = (LocalPlayer) (Object) this;
        boolean isBlocked = MixinErrorBoundary.value("Selected item drop protection", false, () -> SelectedItemDropProtection.shouldBlock(player));
        if (isBlocked) cir.setReturnValue(false);
    }
}
