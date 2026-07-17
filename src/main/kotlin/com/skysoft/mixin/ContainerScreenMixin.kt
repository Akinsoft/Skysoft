package com.skysoft.mixin

import com.skysoft.features.bazaar.BazaarTracker
import com.skysoft.features.inventory.StorageOverlayController
import com.skysoft.utils.SkysoftErrorBoundary
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.inventory.ContainerScreen
import org.spongepowered.asm.mixin.Final
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.Mutable
import org.spongepowered.asm.mixin.Shadow
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

@Mixin(ContainerScreen::class)
abstract class ContainerScreenMixin {
    @field:Mutable
    @field:Shadow
    @field:Final
    private var containerRows = 0

    @Inject(method = ["extractBackground"], at = [At("HEAD")], cancellable = true)
    protected fun skysoftSuppressStorageOverlayBackground(
        context: GuiGraphicsExtractor,
        mouseX: Int,
        mouseY: Int,
        delta: Float,
        ci: CallbackInfo,
    ) {
        val screen = this as ContainerScreen
        containerRows = SkysoftErrorBoundary.value("Bazaar Tracker order menu layout", containerRows) {
            BazaarTracker.layoutOrderMenu(screen)
        }
        val shouldSuppress = SkysoftErrorBoundary.value("Storage Overlay background", false) {
            StorageOverlayController.isActive(screen) && !StorageOverlayController.shouldDimBackground()
        }
        if (shouldSuppress) {
            ci.cancel()
        }
    }

    @Inject(
        method = ["extractBackground"],
        at = [
            At(
                value = "INVOKE",
                target = "Lnet/minecraft/client/gui/screens/inventory/AbstractContainerScreen;" +
                    "extractBackground(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V",
                shift = At.Shift.AFTER,
            ),
        ],
        cancellable = true,
    )
    protected fun skysoftSuppressStorageOverlayContainerBackground(
        context: GuiGraphicsExtractor,
        mouseX: Int,
        mouseY: Int,
        delta: Float,
        ci: CallbackInfo,
    ) {
        val shouldSuppress = SkysoftErrorBoundary.value("Storage Overlay container background", false) {
            StorageOverlayController.isActive(this as ContainerScreen) &&
                StorageOverlayController.shouldDimBackground()
        }
        if (shouldSuppress) {
            ci.cancel()
        }
    }
}
