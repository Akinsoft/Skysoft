package com.skysoft.mixin;

import com.skysoft.gui.tooltip.TooltipScrollExcludedScreen;
import net.minecraft.client.gui.screens.packs.PackSelectionScreen;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(PackSelectionScreen.class)
public abstract class PackSelectionScreenMixin implements TooltipScrollExcludedScreen {
}
