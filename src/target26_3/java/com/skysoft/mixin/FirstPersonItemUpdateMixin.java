package com.skysoft.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.skysoft.features.helditem.HeldItemUpdateFix;
import com.skysoft.utils.mixin.MixinErrorBoundary;
import net.minecraft.client.player.FirstPersonHandsAndItems;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(FirstPersonHandsAndItems.class)
public class FirstPersonItemUpdateMixin {
    @ModifyReturnValue(method = "shouldInstantlyReplaceVisibleItem", at = @At("RETURN"))
    private boolean skysoftKeepSameUpdatedItemVisible(boolean original, ItemStack visible, ItemStack expected, LocalPlayer player) {
        boolean preserve = MixinErrorBoundary.value("Held Item visible item update", false,
            () -> HeldItemUpdateFix.INSTANCE.shouldPreserveUpdate(visible, expected));
        return original || preserve;
    }
}
