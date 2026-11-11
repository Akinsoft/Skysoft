package com.skysoft.mixin;

import com.skysoft.features.misc.InventoryCraftingHider;
import com.skysoft.utils.mixin.MixinErrorBoundary;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InventoryScreen.class)
public abstract class InventoryScreenMixin {
    @Inject(method = "extractBackground", at = @At("TAIL"))
    private void skysoftCoverInventoryCrafting(
        GuiGraphicsExtractor context,
        int mouseX,
        int mouseY,
        float delta,
        CallbackInfo ci
    ) {
        MixinErrorBoundary.run(
            "Inventory crafting cover",
            () -> InventoryCraftingHider.cover((InventoryScreen) (Object) this, context)
        );
    }
}
