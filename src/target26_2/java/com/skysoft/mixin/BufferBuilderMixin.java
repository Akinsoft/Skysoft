package com.skysoft.mixin;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import com.skysoft.utils.render.shader.VertexMemoryAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(BufferBuilder.class)
public abstract class BufferBuilderMixin implements VertexMemoryAccess {
    @Shadow
    private long vertexPointer;

    @Override
    public long skysoftAttributeAddress(VertexFormatElement element, int byteOffset) {
        return vertexPointer == -1L ? -1L : vertexPointer + byteOffset;
    }
}
