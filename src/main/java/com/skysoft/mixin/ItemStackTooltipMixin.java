package com.skysoft.mixin;

import com.skysoft.utils.mixin.MixinFeatureAdapters;
import com.skysoft.utils.mixin.MixinErrorBoundary;
import java.util.Optional;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemStack.class)
public abstract class ItemStackTooltipMixin {
    @Inject(method = "getTooltipImage", at = @At("HEAD"), cancellable = true)
    protected void skysoftAddStoragePreview(CallbackInfoReturnable<Optional<TooltipComponent>> cir) {
        TooltipComponent preview = MixinErrorBoundary.value("Storage Preview tooltip", null,
            () -> MixinFeatureAdapters.storagePreviewTooltip((ItemStack) (Object) this));
        if (preview != null) cir.setReturnValue(Optional.of(preview));
    }
}
