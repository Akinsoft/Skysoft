package com.skysoft.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.skysoft.integration.MixinFeatureAdapters;
import com.skysoft.utils.mixin.MixinErrorBoundary;
import java.util.Optional;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ItemStack.class)
public abstract class ItemStackTooltipMixin {
    @ModifyReturnValue(method = "getTooltipImage", at = @At("RETURN"))
    protected Optional<TooltipComponent> skysoftAddStoragePreview(Optional<TooltipComponent> original) {
        TooltipComponent preview = MixinErrorBoundary.value("Storage Preview tooltip", null,
            () -> MixinFeatureAdapters.storagePreviewTooltip((ItemStack) (Object) this));
        return preview != null ? Optional.of(preview) : original;
    }
}
