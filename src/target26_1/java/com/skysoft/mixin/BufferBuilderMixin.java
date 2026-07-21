package com.skysoft.mixin;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import com.skysoft.utils.render.shader.VertexMemoryAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(BufferBuilder.class)
public abstract class BufferBuilderMixin implements VertexMemoryAccess {
    @Shadow
    protected abstract long beginElement(VertexFormatElement element);

    @Override
    public long skysoftAttributeAddress(VertexFormatElement element, int byteOffset) {
        return beginElement(element);
    }
}
