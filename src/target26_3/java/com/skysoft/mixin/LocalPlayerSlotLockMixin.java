package com.skysoft.mixin;

import com.skysoft.utils.mixin.MixinErrorBoundary;
import com.skysoft.data.skyblock.SkyBlockDroppedItems;
import com.skysoft.features.inventory.ItemProtectionManager;
import com.skysoft.features.inventory.SlotLockManager;
import com.skysoft.utils.input.InputHandlingResult;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MultiPlayerGameMode.class)
public class LocalPlayerSlotLockMixin {
    @Inject(method = "dropItem", at = @At("HEAD"), cancellable = true)
    private void skysoftProtectLockedSelectedSlot(LocalPlayer player, boolean all, CallbackInfo ci) {
        boolean isBlocked = MixinErrorBoundary.value("Selected item drop protection", false, () -> {
            if (ItemProtectionManager.shouldAllowDungeonUltimate(player)) return false;
            InputHandlingResult slotLockResult = SlotLockManager.handleSelectedItemDrop(player);
            InputHandlingResult itemProtectionResult = ItemProtectionManager.handleWorldDrop(player);
            return slotLockResult == InputHandlingResult.CONSUMED || itemProtectionResult == InputHandlingResult.CONSUMED;
        });
        if (isBlocked) {
            ci.cancel();
            return;
        }
        // Native 26.3 now sends the drop packet here, after removing the selected stack.
        // Record the accepted intent before that mutation, as the previous Minecraft hook did.
        MixinErrorBoundary.run("Dropped item tracking", () ->
            SkyBlockDroppedItems.INSTANCE.recordIntent(player.getMainHandItem(), all ? player.getMainHandItem().getCount() : 1));
    }
}
