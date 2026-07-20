package com.skysoft.mixin

import com.skysoft.utils.render.ChromaTextColor
import com.skysoft.utils.render.ChromaTextRendering
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.TextColor
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.Shadow
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Redirect

@Mixin(targets = ["net.minecraft.client.gui.Font\$PreparedTextBuilder"])
abstract class FontPreparedTextBuilderMixin {
    @Shadow
    private var x = 0f

    @Shadow
    private var y = 0f

    @Redirect(
        method = ["getTextColor"],
        at = At(
            value = "INVOKE",
            target = "Lnet/minecraft/network/chat/TextColor;getValue()I",
        ),
    )
    protected fun skysoftResolveSpatialChroma(textColor: TextColor): Int {
        val markedColor: Any = textColor
        return ChromaTextRendering.resolveGlyph(
            textColor.value,
            (markedColor as? ChromaTextColor)?.skysoftChromaColour(),
            x,
            y,
            Minecraft.getInstance().window.guiScaledWidth,
        )
    }
}
