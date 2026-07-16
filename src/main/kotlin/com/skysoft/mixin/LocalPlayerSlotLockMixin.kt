package com.skysoft.mixin

import com.skysoft.features.inventory.ItemProtectionManager
import com.skysoft.features.inventory.SlotLockManager
import com.skysoft.utils.input.InputHandlingResult
import net.minecraft.client.player.LocalPlayer
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable

@Mixin(LocalPlayer::class)
open class LocalPlayerSlotLockMixin {
    @Inject(method = ["drop"], at = [At("HEAD")], cancellable = true)
    protected fun skysoftProtectLockedSelectedSlot(all: Boolean, cir: CallbackInfoReturnable<Boolean>) {
        val player = this as LocalPlayer
        if (ItemProtectionManager.shouldAllowDungeonUltimate(player)) return
        val slotLockResult = SlotLockManager.handleSelectedItemDrop(player)
        val itemProtectionResult = ItemProtectionManager.handleWorldDrop(player)
        if (slotLockResult == InputHandlingResult.CONSUMED || itemProtectionResult == InputHandlingResult.CONSUMED) {
            cir.setReturnValue(false)
        }
    }
}
