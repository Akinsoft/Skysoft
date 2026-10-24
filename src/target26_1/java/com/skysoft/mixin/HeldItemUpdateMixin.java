package com.skysoft.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.skysoft.features.helditem.HeldItemUpdateFix;
import com.skysoft.utils.mixin.MixinErrorBoundary;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ItemInHandRenderer.class)
public class HeldItemUpdateMixin {
    @ModifyReturnValue(method = "shouldInstantlyReplaceVisibleItem", at = @At("RETURN"))
    protected boolean skysoftKeepSameUpdatedItemVisible(boolean original, ItemStack currentlyVisibleItem, ItemStack expectedItem) {
        return MixinErrorBoundary.value("Held Item visible item update", original, () -> HeldItemUpdateFix.shouldKeepVisible(original, currentlyVisibleItem, expectedItem));
    }
}
