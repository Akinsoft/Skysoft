package com.skysoft.mixin;

import com.skysoft.features.inventory.ExperimentationTableHelper;
import com.skysoft.utils.mixin.MixinErrorBoundary;
import java.util.List;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractContainerMenu.class)
public abstract class AbstractContainerMenuMixin {
    @Inject(method = "setItem", at = @At("TAIL"))
    private void skysoftTrackExperimentationSlotUpdate(int slotId, int stateId, ItemStack stack, CallbackInfo ci) {
        AbstractContainerMenu menu = (AbstractContainerMenu) (Object) this;
        MixinErrorBoundary.run(
            "Experimentation Table slot update",
            () -> ExperimentationTableHelper.INSTANCE.onMenuSlotChanged(menu, slotId, stack)
        );
    }

    @Inject(method = "initializeContents", at = @At("TAIL"))
    private void skysoftTrackExperimentationContentsUpdate(int stateId, List<ItemStack> items, ItemStack carried, CallbackInfo ci) {
        AbstractContainerMenu menu = (AbstractContainerMenu) (Object) this;
        MixinErrorBoundary.run(
            "Experimentation Table contents update",
            () -> ExperimentationTableHelper.INSTANCE.onMenuContentsChanged(menu)
        );
    }
}
