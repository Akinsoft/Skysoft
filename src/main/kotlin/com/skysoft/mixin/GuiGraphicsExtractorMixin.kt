package com.skysoft.mixin

import com.llamalad7.mixinextras.injector.wrapoperation.Operation
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation
import com.skysoft.features.inventory.ClientStoragePreviewTooltip
import com.skysoft.features.inventory.RarityHighlightRenderer
import com.skysoft.features.inventory.SlotBindingManager
import com.skysoft.features.inventory.StoragePreviewTooltip
import com.skysoft.utils.SkysoftErrorBoundary
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent
import net.minecraft.client.renderer.state.gui.GuiItemRenderState
import net.minecraft.client.renderer.state.gui.GuiRenderState
import net.minecraft.world.inventory.tooltip.TooltipComponent
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

@Mixin(GuiGraphicsExtractor::class)
abstract class GuiGraphicsExtractorMixin {
    @WrapOperation(
        method = [
            "item(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/level/Level;" +
                "Lnet/minecraft/world/item/ItemStack;III)V",
        ],
        at = [
            At(
                value = "INVOKE",
                target = "Lnet/minecraft/client/renderer/state/gui/GuiRenderState;" +
                    "addItem(Lnet/minecraft/client/renderer/state/gui/GuiItemRenderState;)V",
            ),
        ],
    )
    protected fun skysoftAttachRarityContour(
        renderState: GuiRenderState,
        itemState: GuiItemRenderState,
        original: Operation<Void>,
    ) {
        SkysoftErrorBoundary.run("Rarity Highlight contour attachment") {
            RarityHighlightRenderer.attachContour(itemState)
        }
        original.call(renderState, itemState)
    }

    @Inject(method = ["extractDeferredElements"], at = [At("HEAD")])
    protected fun skysoftQueueSlotBindingTooltipBeforeDeferredTooltips(
        mouseX: Int,
        mouseY: Int,
        delta: Float,
        ci: CallbackInfo,
    ) {
        SkysoftErrorBoundary.run("Slot Binding tooltip rendering") {
            SlotBindingManager.renderTopLayer((this as Any) as GuiGraphicsExtractor)
        }
    }

    private companion object {
        @JvmStatic
        @WrapOperation(
            method = [
                "lambda\$setTooltipForNextFrame\$0",
                "lambda\$setTooltipForNextFrame\$1",
            ],
            at = [
                At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/screens/inventory/tooltip/ClientTooltipComponent;" +
                        "create(Lnet/minecraft/world/inventory/tooltip/TooltipComponent;)" +
                        "Lnet/minecraft/client/gui/screens/inventory/tooltip/ClientTooltipComponent;",
                ),
            ],
        )
        private fun skysoftCreateStoragePreviewTooltip(
            component: TooltipComponent,
            original: Operation<ClientTooltipComponent>,
        ): ClientTooltipComponent = if (component is StoragePreviewTooltip) {
            ClientStoragePreviewTooltip(component)
        } else {
            original.call(component)
        }
    }
}
