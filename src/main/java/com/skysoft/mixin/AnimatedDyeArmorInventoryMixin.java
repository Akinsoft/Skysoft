package com.skysoft.mixin;

import com.skysoft.features.inventory.AnimatedDyeArmorCache;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Inventory.class)
public abstract class AnimatedDyeArmorInventoryMixin {
    @Inject(method = "setItem", at = @At("TAIL"))
    private void skysoftRememberArmor(int slot, ItemStack stack, CallbackInfo ci) {
        AnimatedDyeArmorCache.observe((Inventory) (Object) this, slot, stack);
    }
}
