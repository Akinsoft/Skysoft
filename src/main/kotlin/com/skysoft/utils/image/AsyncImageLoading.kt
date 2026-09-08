package com.skysoft.utils.image

import com.mojang.blaze3d.platform.NativeImage
import java.util.concurrent.CompletableFuture
import net.minecraft.util.Util

internal fun <T> CompletableFuture<T>.mapNativeImageAsync(decode: (T) -> NativeImage): CompletableFuture<NativeImage> {
    val request = CompletableFuture<NativeImage>()
    thenAcceptAsync(
        { source ->
            if (!request.isDone) {
                val image = decode(source)
                if (!request.complete(image)) image.close()
            }
        },
        Util.ioPool(),
    ).whenComplete { _, failure ->
        if (failure != null) request.completeExceptionally(failure)
    }
    return request
}
