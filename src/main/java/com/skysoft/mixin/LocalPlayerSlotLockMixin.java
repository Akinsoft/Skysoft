package com.skysoft.mixin;

import com.skysoft.utils.mixin.MixinErrorBoundary;
import com.skysoft.features.inventory.ItemProtectionManager;
import com.skysoft.features.inventory.SlotLockManager;
import com.skysoft.utils.input.InputHandlingResult;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LocalPlayer.class)
public class LocalPlayerSlotLockMixin {
    @Inject(method = "drop", at = @At("HEAD"), cancellable = true)
    private void skysoftProtectLockedSelectedSlot(boolean all, CallbackInfoReturnable<Boolean> cir) {
        boolean isBlocked = MixinErrorBoundary.value("Selected item drop protection", false, () -> {
            LocalPlayer player = (LocalPlayer) (Object) this;
            if (ItemProtectionManager.shouldAllowDungeonUltimate(player)) return false;
            InputHandlingResult slotLockResult = SlotLockManager.handleSelectedItemDrop(player);
            InputHandlingResult itemProtectionResult = ItemProtectionManager.handleWorldDrop(player);
            return slotLockResult == InputHandlingResult.CONSUMED || itemProtectionResult == InputHandlingResult.CONSUMED;
        });
        if (isBlocked) cir.setReturnValue(false);
    }
}
