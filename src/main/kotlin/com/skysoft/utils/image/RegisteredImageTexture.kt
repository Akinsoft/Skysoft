package com.skysoft.utils.image

import com.mojang.blaze3d.platform.NativeImage
import com.mojang.blaze3d.systems.RenderSystem
import com.skysoft.utils.gui.Rect
import com.skysoft.utils.render.gpu.FilterMode
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.resources.Identifier

internal class RegisteredImageTexture private constructor(
    val id: Identifier,
    val texture: DynamicTexture,
    val width: Int,
    val height: Int,
) {
    fun release() {
        Minecraft.getInstance().textureManager.release(id)
    }

    companion object {
        fun register(id: Identifier, description: String, image: NativeImage): RegisteredImageTexture {
            val texture = try {
                DynamicTexture({ description }, image)
            } catch (failure: Throwable) {
                image.close()
                throw failure
            }
            try {
                Minecraft.getInstance().textureManager.register(id, texture)
            } catch (failure: Throwable) {
                texture.close()
                throw failure
            }
            return RegisteredImageTexture(id, texture, image.width, image.height)
        }
    }
}

internal fun GuiGraphicsExtractor.drawLinearTexture(
    texture: DynamicTexture,
    bounds: Rect,
    u0: Float = 0f,
    u1: Float = 1f,
    v0: Float = 0f,
    v1: Float = 1f,
) {
    blit(
        texture.textureView,
        RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR),
        bounds.x,
        bounds.y,
        bounds.x + bounds.width,
        bounds.y + bounds.height,
        u0,
        u1,
        v0,
        v1,
    )
}
