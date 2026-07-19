package com.skysoft.mixin

import com.skysoft.features.inventory.StoragePreviews
import com.skysoft.utils.SkysoftErrorBoundary
import java.util.Optional
import net.minecraft.world.inventory.tooltip.TooltipComponent
import net.minecraft.world.item.ItemStack
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable

@Mixin(ItemStack::class)
abstract class ItemStackTooltipMixin {
    @Inject(method = ["getTooltipImage"], at = [At("HEAD")], cancellable = true)
    protected fun skysoftAddStoragePreview(cir: CallbackInfoReturnable<Optional<TooltipComponent>>) {
        val stack = (this as Any) as ItemStack
        val preview = SkysoftErrorBoundary.value<TooltipComponent?>("Storage Preview tooltip", null) {
            StoragePreviews.tooltipFor(stack)
        } ?: return
        cir.returnValue = Optional.of(preview)
    }
}
