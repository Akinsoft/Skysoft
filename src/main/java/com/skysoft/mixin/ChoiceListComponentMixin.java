package com.skysoft.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import io.github.notenoughupdates.moulconfig.gui.GuiImmediateContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(targets = "io.github.notenoughupdates.moulconfig.gui.editors.ChoiceListComponent", remap = false)
public abstract class ChoiceListComponentMixin {
    @Unique
    private static final int SKYSOFT_ROW_HEIGHT = 12;

    @Shadow
    private int scrollOffset;

    @Shadow
    protected abstract int listViewportHeight(int height);

    @ModifyVariable(method = "render", at = @At(value = "STORE", ordinal = 0), ordinal = 0)
    private int skysoftFirstVisibleRow(int original) {
        return scrollOffset / SKYSOFT_ROW_HEIGHT;
    }

    @ModifyExpressionValue(
        method = "render",
        at = @At(value = "INVOKE", target = "Ljava/util/List;size()I")
    )
    private int skysoftVisibleRowEnd(int totalRows, GuiImmediateContext context) {
        int visibleBottom = scrollOffset + listViewportHeight(context.getHeight());
        int endRow = (visibleBottom + SKYSOFT_ROW_HEIGHT - 1) / SKYSOFT_ROW_HEIGHT;
        return Math.min(totalRows, endRow);
    }
}
