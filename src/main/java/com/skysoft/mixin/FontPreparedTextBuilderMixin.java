package com.skysoft.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.skysoft.utils.render.ChromaTextColor;
import com.skysoft.utils.render.ChromaTextRendering;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.TextColor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets = "net.minecraft.client.gui.Font$PreparedTextBuilder")
public abstract class FontPreparedTextBuilderMixin {
    @Shadow private float x;
    @Shadow private float y;
    @WrapOperation(method = "getTextColor", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/chat/TextColor;getValue()I"))
    protected int skysoftResolveSpatialChroma(TextColor textColor, Operation<Integer> original) {
        ChromaTextColor marked = (Object) textColor instanceof ChromaTextColor color ? color : null;
        return ChromaTextRendering.INSTANCE.resolveGlyph(original.call(textColor), marked == null ? null : marked.skysoftChromaColour(), x, y, Minecraft.getInstance().getWindow().getGuiScaledWidth());
    }
}
