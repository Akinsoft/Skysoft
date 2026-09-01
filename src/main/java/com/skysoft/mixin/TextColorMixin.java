package com.skysoft.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.skysoft.utils.render.ChromaTextColor;
import com.skysoft.utils.render.ChromaTextRendering;
import io.github.notenoughupdates.moulconfig.ChromaColour;
import net.minecraft.network.chat.TextColor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(TextColor.class)
public abstract class TextColorMixin implements ChromaTextColor {
    @Unique private ChromaColour skysoftChromaColour;
    @Unique @Override public void skysoftUseChromaColour(ChromaColour colour) { skysoftChromaColour = colour; }
    @Unique @Override public ChromaColour skysoftChromaColour() { return skysoftChromaColour; }
    @ModifyReturnValue(method = "getValue", at = @At("RETURN"))
    protected int skysoftResolveChromaColour(int original) {
        return ChromaTextRendering.INSTANCE.resolve(original, skysoftChromaColour);
    }
}
