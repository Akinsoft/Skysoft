package com.skysoft.features.screenshot

import com.mojang.blaze3d.platform.NativeImage
import com.skysoft.SkysoftMod
import com.skysoft.utils.image.AsyncImageTextureCache
import com.skysoft.utils.image.RegisteredImageTexture
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import kotlin.math.min
import kotlin.math.roundToInt
import net.minecraft.client.Minecraft
import net.minecraft.util.Util

internal class ScreenshotTextureStore(private val minecraft: Minecraft) : AutoCloseable {
    private var nextTextureId = 0
    private val thumbnails = AsyncImageTextureCache<Path>(
        minecraft,
        THUMBNAIL_CACHE_SIZE,
        MAX_PENDING_THUMBNAILS,
    ) { _, image -> registerTexture(image, "thumbnail") }
    private val previews = AsyncImageTextureCache<Path>(
        minecraft,
        maximumSize = 1,
        maximumPending = 1,
    ) { _, image -> registerTexture(image, "preview") }
    private val discardedPaths = mutableSetOf<Path>()
    private var previewPath: Path? = null
    private var isClosed = false

    fun thumbnail(path: Path): RegisteredImageTexture? {
        val texture = thumbnails.texture(path)
        if (texture == null) requestThumbnail(path)
        return texture
    }

    fun isThumbnailFailed(path: Path): Boolean = thumbnails.isFailed(path)

    fun preview(path: Path): RegisteredImageTexture? {
        if (previewPath != path) selectPreview(path)
        val texture = previews.texture(path)
        if (texture == null) requestPreview(path)
        return texture
    }

    fun isSelectedPreviewFailed(path: Path): Boolean = previewPath == path && previews.isFailed(path)

    fun clearSelectedPreview() {
        clearPreview()
    }

    fun discard(path: Path) {
        discardedPaths.add(path)
        thumbnails.invalidate(path)
        previews.invalidate(path)
        if (previewPath == path) previewPath = null
    }

    fun refresh(path: Path) {
        discardedPaths.remove(path)
        thumbnails.invalidate(path)
        previews.invalidate(path)
        if (previewPath == path) previewPath = null
    }

    override fun close() {
        if (isClosed) return
        isClosed = true
        thumbnails.close()
        previews.close()
        previewPath = null
    }

    private fun requestThumbnail(path: Path) {
        if (isClosed || path in discardedPaths) return
        thumbnails.request(path) {
            loadScaledScreenshotImage(path, THUMBNAIL_MAX_WIDTH, THUMBNAIL_MAX_HEIGHT)
        }
    }

    private fun selectPreview(path: Path) {
        clearPreview()
        previewPath = path
    }

    private fun requestPreview(path: Path) {
        if (isClosed || path in discardedPaths || previewPath != path) return
        previews.request(path) {
            loadScaledScreenshotImage(path, PREVIEW_MAX_WIDTH, PREVIEW_MAX_HEIGHT)
        }
    }

    private fun clearPreview() {
        previewPath?.let(previews::invalidate)
        previewPath = null
    }

    private fun registerTexture(image: NativeImage, kind: String): RegisteredImageTexture {
        val id = SkysoftMod.id("screenshot_manager/${kind}_${nextTextureId++}")
        return RegisteredImageTexture.register(id, "Skysoft Screenshot Manager $kind", image)
    }

    private companion object {
        const val THUMBNAIL_CACHE_SIZE = 30
        const val MAX_PENDING_THUMBNAILS = 12
        const val THUMBNAIL_MAX_WIDTH = 640
        const val THUMBNAIL_MAX_HEIGHT = 360
        const val PREVIEW_MAX_WIDTH = 1600
        const val PREVIEW_MAX_HEIGHT = 900
    }
}

internal fun loadScaledScreenshotImage(path: Path, maximumWidth: Int, maximumHeight: Int): CompletableFuture<NativeImage> =
    CompletableFuture.supplyAsync(
        {
            val source = NativeImage.read(Files.newInputStream(path))
            val scale = min(
                min(maximumWidth.toDouble() / source.width, maximumHeight.toDouble() / source.height),
                1.0,
            )
            val targetWidth = (source.width * scale).roundToInt().coerceAtLeast(1)
            val targetHeight = (source.height * scale).roundToInt().coerceAtLeast(1)
            if (targetWidth == source.width && targetHeight == source.height) return@supplyAsync source
            val target = NativeImage(targetWidth, targetHeight, false)
            try {
                source.resizeSubRectTo(0, 0, source.width, source.height, target)
                target
            } catch (failure: Throwable) {
                target.close()
                throw failure
            } finally {
                source.close()
            }
        },
        Util.ioPool(),
    )
