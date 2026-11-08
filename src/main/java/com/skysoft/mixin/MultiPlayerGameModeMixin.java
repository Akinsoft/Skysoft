package com.skysoft.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.skysoft.features.helditem.HeldItemUpdateFix;
import com.skysoft.integration.ContainerTransferHooks;
import com.skysoft.integration.MixinFeatureAdapters;
import com.skysoft.utils.MinecraftClient;
import com.skysoft.utils.input.InputEventInterceptor;
import com.skysoft.utils.input.InputHandlingResult;
import com.skysoft.utils.mixin.MixinErrorBoundary;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MultiPlayerGameMode.class)
public abstract class MultiPlayerGameModeMixin {
    @Inject(method = "handleContainerInput", at = @At("HEAD"))
    private void skysoftTrackContainerTransfers(int containerId, int slotNum, int buttonNum, ContainerInput input, Player player, CallbackInfo ci) {
        ContainerTransferHooks.trackContainerInput(containerId, slotNum, buttonNum, input, player);
    }

    @WrapOperation(method = "sameDestroyTarget", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;isSameItemSameComponents(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;)Z"))
    private boolean isSkysoftSameDestroyTargetAfterItemUpdate(ItemStack current, ItemStack previous, Operation<Boolean> original) {
        if (original.call(current, previous)) return true;
        return MixinErrorBoundary.value("Held Item destroy target update", false, () -> HeldItemUpdateFix.INSTANCE.shouldPreserveUpdate(previous, current));
    }

    @WrapOperation(method = "continueDestroyBlock", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;startDestroyBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;)Z"))
    private boolean skysoftHandleContinuedBlockClick(MultiPlayerGameMode gameMode, BlockPos position, Direction direction, Operation<Boolean> original) {
        if (InputEventInterceptor.processContinuedLeftBlockClick(position) == InputHandlingResult.CONSUMED) return false;
        return original.call(gameMode, position, direction);
    }

    @WrapOperation(method = "handleContainerInput", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/inventory/AbstractContainerMenu;clicked(IILnet/minecraft/world/inventory/ContainerInput;Lnet/minecraft/world/entity/player/Player;)V"))
    private void skysoftAnimateLocalContainerMutation(AbstractContainerMenu menu, int slotNum, int buttonNum, ContainerInput input, Player player, Operation<Void> original) {
        Screen current = MinecraftClient.INSTANCE.screen();
        if (!(current instanceof AbstractContainerScreen<?> screen) || screen.getMenu() != menu) {
            original.call(menu, slotNum, buttonNum, input, player);
            return;
        }
        MixinErrorBoundary.aroundUnit("Smooth Swapping local mutation",
            () -> original.call(menu, slotNum, buttonNum, input, player),
            mutate -> MixinFeatureAdapters.animateLocalContainerMutation(screen, mutate));
    }
}
