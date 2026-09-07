package com.skysoft.utils.image

import com.mojang.blaze3d.platform.NativeImage
import com.skysoft.utils.boundedAccessOrderMap
import com.skysoft.utils.net.isCancellationFailure
import java.util.concurrent.CompletableFuture
import net.minecraft.client.Minecraft
import net.minecraft.resources.Identifier

internal class AsyncImageTextureCache<K>(
    private val minecraft: Minecraft,
    maximumSize: Int,
    private val maximumPending: Int,
    private val textureDescription: String,
    private val textureId: (K) -> Identifier,
) : AutoCloseable {
    private val textures = boundedAccessOrderMap<K, RegisteredImageTexture>(maximumSize) { _, texture ->
        texture.release()
    }
    private val failures = boundedAccessOrderMap<K, Unit>(maximumSize)
    private val pending = mutableMapOf<K, CompletableFuture<NativeImage>>()
    private var closed = false

    val isEmpty: Boolean
        get() = textures.isEmpty()

    val hasPending: Boolean
        get() = pending.isNotEmpty()

    fun texture(key: K): RegisteredImageTexture? = textures[key]

    fun isFailed(key: K): Boolean = key in failures

    fun request(key: K, requestFactory: () -> CompletableFuture<NativeImage>) {
        if (closed || key in textures || key in failures || key in pending || pending.size >= maximumPending) return
        val request = requestFactory()
        pending[key] = request
        request.whenComplete { image, failure ->
            minecraft.execute {
                val accepted = !closed && pending[key] === request
                if (pending[key] === request) pending.remove(key)
                when {
                    image != null && accepted -> install(key, image)
                    image != null -> image.close()
                    failure != null && accepted && !failure.isCancellationFailure() -> failures[key] = Unit
                }
            }
        }
    }

    fun cancelPending(key: K) {
        pending.remove(key)?.cancel(true)
    }

    fun invalidate(key: K) {
        cancelPending(key)
        failures.remove(key)
        textures.remove(key)?.release()
    }

    fun clear() {
        val requestsToCancel = pending.values.toList()
        pending.clear()
        requestsToCancel.forEach { it.cancel(true) }
        failures.clear()
        val texturesToRelease = textures.values.toList()
        textures.clear()
        texturesToRelease.forEach(RegisteredImageTexture::release)
    }

    override fun close() {
        if (closed) return
        closed = true
        clear()
    }

    private fun install(key: K, image: NativeImage) {
        val id = try {
            textureId(key)
        } catch (failure: Throwable) {
            image.close()
            failures[key] = Unit
            return
        }
        val texture = try {
            RegisteredImageTexture.register(id, textureDescription, image)
        } catch (failure: Throwable) {
            failures[key] = Unit
            return
        }
        textures.put(key, texture)?.release()
    }

}
