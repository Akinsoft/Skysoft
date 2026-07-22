package com.skysoft.utils.image

import com.mojang.blaze3d.platform.NativeImage
import net.minecraft.client.Minecraft
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
