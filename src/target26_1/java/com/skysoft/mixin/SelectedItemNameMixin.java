package com.skysoft.mixin;

import com.skysoft.features.misc.selecteditem.SelectedItemName;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public class SelectedItemNameMixin {
    @Shadow
    private int toolHighlightTimer;

    @Shadow
    private ItemStack lastToolHighlight;

    @Inject(method = "extractSelectedItemName", at = @At("HEAD"), cancellable = true)
    private void skysoftRenderSelectedItemName(GuiGraphicsExtractor context, CallbackInfo ci) {
        if (!SelectedItemName.INSTANCE.isEnabled()) {
            return;
        }
        SelectedItemName.INSTANCE.render(context, lastToolHighlight, toolHighlightTimer);
        ci.cancel();
    }
}
