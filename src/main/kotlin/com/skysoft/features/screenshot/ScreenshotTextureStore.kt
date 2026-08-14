package com.skysoft.features.screenshot

import com.mojang.blaze3d.platform.NativeImage
import com.skysoft.SkysoftMod
import com.skysoft.utils.image.RegisteredImageTexture
import java.nio.file.Files
import java.nio.file.Path
import java.util.LinkedHashMap
import java.util.concurrent.CompletableFuture
import kotlin.math.min
import kotlin.math.roundToInt
import net.minecraft.client.Minecraft
import net.minecraft.util.Util

internal class ScreenshotTextureStore(private val minecraft: Minecraft) : AutoCloseable {
    private val thumbnails = LinkedHashMap<Path, RegisteredImageTexture>(THUMBNAIL_CACHE_SIZE, 0.75f, true)
    private val pendingThumbnails = mutableSetOf<Path>()
    private val failedThumbnails = mutableSetOf<Path>()
    private val discardedPaths = mutableSetOf<Path>()
    private val pathRevisions = mutableMapOf<Path, Int>()
    private var previewPath: Path? = null
    private var previewTexture: RegisteredImageTexture? = null
    private var pendingPreviewPath: Path? = null
    private var isPreviewFailed = false
    private var nextTextureId = 0
    private var isClosed = false

    fun thumbnail(path: Path): RegisteredImageTexture? {
        val texture = thumbnails[path]
        if (texture == null) requestThumbnail(path)
        return texture
    }

    fun isThumbnailFailed(path: Path): Boolean = path in failedThumbnails

    fun preview(path: Path): RegisteredImageTexture? {
        if (previewPath != path) selectPreview(path)
        if (previewTexture == null && pendingPreviewPath == null && !isPreviewFailed) requestPreview(path)
        return previewTexture
    }

    fun isSelectedPreviewFailed(path: Path): Boolean = previewPath == path && isPreviewFailed

    fun clearSelectedPreview() {
        clearPreview()
    }

    fun discard(path: Path) {
        advanceRevision(path)
        discardedPaths.add(path)
        failedThumbnails.remove(path)
        pendingThumbnails.remove(path)
        thumbnails.remove(path)?.let(::release)
        if (previewPath == path) clearPreview()
    }

    fun refresh(path: Path) {
        advanceRevision(path)
        discardedPaths.remove(path)
        failedThumbnails.remove(path)
        pendingThumbnails.remove(path)
        thumbnails.remove(path)?.let(::release)
        if (previewPath == path) clearPreview()
    }

    override fun close() {
        if (isClosed) return
        isClosed = true
        thumbnails.values.forEach(::release)
        thumbnails.clear()
        clearPreview()
    }

    private fun requestThumbnail(path: Path) {
        if (
            isClosed ||
            path in pendingThumbnails ||
            path in failedThumbnails ||
            path in discardedPaths ||
            pendingThumbnails.size >= MAX_PENDING_THUMBNAILS
        ) {
            return
        }
        pendingThumbnails.add(path)
        val requestedRevision = revision(path)
        loadScaledScreenshotImage(path, THUMBNAIL_MAX_WIDTH, THUMBNAIL_MAX_HEIGHT).whenComplete { image, failure ->
            minecraft.execute {
                pendingThumbnails.remove(path)
                when {
                    image != null && !isClosed && path !in discardedPaths && revision(path) == requestedRevision ->
                        installThumbnail(path, image)
                    image != null -> image.close()
                    failure != null && !isClosed && path !in discardedPaths && revision(path) == requestedRevision ->
                        failedThumbnails.add(path)
                }
            }
        }
    }

    private fun installThumbnail(path: Path, image: NativeImage) {
        thumbnails[path] = registerTexture(image, "thumbnail")
        while (thumbnails.size > THUMBNAIL_CACHE_SIZE) {
            val eldest = thumbnails.entries.iterator().next()
            thumbnails.remove(eldest.key)
            release(eldest.value)
        }
    }

    private fun selectPreview(path: Path) {
        clearPreview()
        previewPath = path
    }

    private fun requestPreview(path: Path) {
        pendingPreviewPath = path
        val requestedRevision = revision(path)
        loadScaledScreenshotImage(path, PREVIEW_MAX_WIDTH, PREVIEW_MAX_HEIGHT).whenComplete { image, failure ->
            minecraft.execute {
                if (pendingPreviewPath == path) pendingPreviewPath = null
                when {
                    image != null && !isClosed && previewPath == path && revision(path) == requestedRevision -> {
                        previewTexture = registerTexture(image, "preview")
                    }
                    image != null -> image.close()
                    failure != null && !isClosed && previewPath == path && revision(path) == requestedRevision ->
                        isPreviewFailed = true
                }
            }
        }
    }

    private fun clearPreview() {
        previewTexture?.let(::release)
        previewTexture = null
        previewPath = null
        pendingPreviewPath = null
        isPreviewFailed = false
    }

    private fun registerTexture(image: NativeImage, kind: String): RegisteredImageTexture {
        val id = SkysoftMod.id("screenshot_manager/${kind}_${nextTextureId++}")
        return RegisteredImageTexture.register(id, "Skysoft Screenshot Manager $kind", image)
    }

    private fun release(texture: RegisteredImageTexture) = texture.release()

    private fun revision(path: Path): Int = pathRevisions[path] ?: 0

    private fun advanceRevision(path: Path) {
        pathRevisions[path] = revision(path) + 1
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
