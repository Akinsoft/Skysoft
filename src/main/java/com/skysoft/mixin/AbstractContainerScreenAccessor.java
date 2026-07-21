package com.skysoft.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(AbstractContainerScreen.class)
public interface AbstractContainerScreenAccessor {
    @Accessor("leftPos")
    int skysoftGetLeftPos();

    @Accessor("topPos")
    int skysoftGetTopPos();

    @Accessor("topPos")
    void skysoftSetTopPos(int topPos);

    @Accessor("imageWidth")
    int skysoftGetImageWidth();

    @Accessor("imageHeight")
    int skysoftGetImageHeight();

    @Mutable
    @Accessor("imageHeight")
    void skysoftSetImageHeight(int imageHeight);

    @Accessor("inventoryLabelY")
    void skysoftSetInventoryLabelY(int inventoryLabelY);

    @Accessor("skipNextRelease")
    void skysoftSetSkipNextRelease(boolean skipNextRelease);

    @Accessor("hoveredSlot")
    Slot skysoftGetHoveredSlot();

    @Invoker("extractSlot")
    void skysoftExtractSlot(GuiGraphicsExtractor context, Slot slot, int mouseX, int mouseY);

    @Invoker("slotClicked")
    void skysoftSlotClicked(Slot slot, int slotId, int button, ContainerInput action);
}
