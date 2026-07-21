package com.skysoft.mixin;

import com.skysoft.utils.mixin.MixinErrorBoundary;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.skysoft.features.inventory.RarityHighlightRenderer;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Gui;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public abstract class HotbarRarityHighlightMixin {
    @Inject(method = "extractItemHotbar", at = @At("HEAD"))
    protected void skysoftBeginHotbarRarityHighlightFrame(GuiGraphicsExtractor context, DeltaTracker deltaTracker, CallbackInfo ci) { MixinErrorBoundary.run("Hotbar Rarity Highlight frame", RarityHighlightRenderer::beginFrame); }
    @Inject(method = "extractSlot", at = @At("HEAD"))
    protected void skysoftRenderHotbarRarityBackground(GuiGraphicsExtractor context, int x, int y, DeltaTracker deltaTracker, Player player, ItemStack stack, int seed, CallbackInfo ci) { MixinErrorBoundary.run("Hotbar Rarity Highlight background", () -> RarityHighlightRenderer.renderBackground(context, stack, x, y)); }
    @WrapOperation(method = "extractSlot", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;item(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;III)V"))
    protected void skysoftRenderHotbarRarityContour(GuiGraphicsExtractor context, LivingEntity owner, ItemStack stack, int x, int y, int seed, Operation<Void> original) {
        MixinErrorBoundary.aroundUnit("Hotbar Rarity Highlight item", () -> original.call(context, owner, stack, x, y, seed), renderItem -> RarityHighlightRenderer.renderItem(stack, () -> { renderItem.run(); return kotlin.Unit.INSTANCE; }));
    }
}
