package com.skysoft.mixin

import com.skysoft.utils.render.ChromaTextColor
import com.skysoft.utils.render.ChromaTextRendering
import io.github.notenoughupdates.moulconfig.ChromaColour
import net.minecraft.network.chat.TextColor
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.Unique
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable

@Mixin(TextColor::class)
abstract class TextColorMixin : ChromaTextColor {
    @field:Unique
    private var skysoftChromaColour: ChromaColour? = null

    @Unique
    override fun skysoftUseChromaColour(colour: ChromaColour) {
        skysoftChromaColour = colour
    }

    @Unique
    override fun skysoftChromaColour(): ChromaColour? = skysoftChromaColour

    @Inject(method = ["getValue"], at = [At("HEAD")], cancellable = true)
    protected fun skysoftResolveChromaColour(cir: CallbackInfoReturnable<Int>) {
        val chromaColour = skysoftChromaColour ?: return
        cir.returnValue = ChromaTextRendering.resolve(0, chromaColour)
    }
}
