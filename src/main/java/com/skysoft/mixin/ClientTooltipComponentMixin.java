package com.skysoft.mixin;

import com.skysoft.features.inventory.ClientStoragePreviewTooltip;
import com.skysoft.features.inventory.StoragePreviewTooltip;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientTooltipComponent.class)
public interface ClientTooltipComponentMixin {
    @Inject(
        method = "create(Lnet/minecraft/world/inventory/tooltip/TooltipComponent;)" +
            "Lnet/minecraft/client/gui/screens/inventory/tooltip/ClientTooltipComponent;",
        at = @At("HEAD"),
        cancellable = true
    )
    private static void skysoftCreateStoragePreviewTooltip(
        TooltipComponent component,
        CallbackInfoReturnable<ClientTooltipComponent> cir
    ) {
        if (component instanceof StoragePreviewTooltip preview) {
            cir.setReturnValue(new ClientStoragePreviewTooltip(preview));
        }
    }
}
