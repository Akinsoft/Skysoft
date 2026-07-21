package com.skysoft.mixin;

import com.skysoft.utils.render.ChromaTextColor;
import com.skysoft.utils.render.ChromaTextRendering;
import io.github.notenoughupdates.moulconfig.ChromaColour;
import net.minecraft.network.chat.TextColor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(TextColor.class)
public abstract class TextColorMixin implements ChromaTextColor {
    @Unique private ChromaColour skysoftChromaColour;
    @Unique @Override public void skysoftUseChromaColour(ChromaColour colour) { skysoftChromaColour = colour; }
    @Unique @Override public ChromaColour skysoftChromaColour() { return skysoftChromaColour; }
    @Inject(method = "getValue", at = @At("HEAD"), cancellable = true)
    protected void skysoftResolveChromaColour(CallbackInfoReturnable<Integer> cir) {
        if (skysoftChromaColour != null) cir.setReturnValue(ChromaTextRendering.INSTANCE.resolve(0, skysoftChromaColour));
    }
}
