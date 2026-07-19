package com.skysoft.features.chat

import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import com.mojang.blaze3d.platform.InputConstants
import com.mojang.blaze3d.platform.NativeImage
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.textures.FilterMode
import com.skysoft.SkysoftMod
import com.skysoft.config.SkysoftConfigGui
import com.skysoft.features.screenshot.ScreenshotUploadMetadataStore
import com.skysoft.features.screenshot.loadScaledScreenshotImage
import com.skysoft.gui.GuiOverlay
import com.skysoft.gui.GuiOverlayContextType
import com.skysoft.gui.GuiOverlayLayer
import com.skysoft.gui.GuiOverlayRegistry
import com.skysoft.utils.MinecraftClient
import com.skysoft.utils.gui.OverlayPanelStyle
import com.skysoft.utils.gui.Rect
import com.skysoft.utils.input.InputHandlingResult
import com.skysoft.utils.input.InputUtilities
import java.net.URI
import java.nio.file.Files
import java.util.LinkedHashMap
import java.util.Locale
import java.util.concurrent.CompletableFuture
import kotlin.math.min
import kotlin.math.roundToInt
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.ActiveTextCollector
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.ChatComponent
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.network.chat.ClickEvent
import net.minecraft.resources.Identifier
import org.lwjgl.glfw.GLFW

internal const val MAXIMUM_IMAGE_PREVIEW_TEXTURE_WIDTH = 1024
internal const val MAXIMUM_IMAGE_PREVIEW_TEXTURE_HEIGHT = 576

object ImageLinkPreview {
    private val textures = LinkedHashMap<String, RemoteImageTexture>(CACHE_SIZE, 0.75f, true)
    private val failures = mutableSetOf<String>()
    private var pendingUrl: String? = null
    private var pendingRequest: CompletableFuture<*>? = null
    private var isPendingRequestRemote = false
    private var candidate: ImageLinkCandidate? = null
    private var nextTextureId = 0

    fun register() {
        GuiOverlayRegistry.register(
            GuiOverlay(
                id = "Chat image link preview",
                layer = GuiOverlayLayer.ABOVE_SCREEN,
                contexts = setOf(GuiOverlayContextType.CHAT),
                visible = { candidate != null },
                render = { context, _ -> render(context) },
            ),
        )
    }

    fun updateHoveredLink(mouseX: Int, mouseY: Int, displayMode: ChatComponent.DisplayMode) {
        if (!SkysoftConfigGui.config().chat.previewImage.enabled) {
            if (candidate != null || textures.isNotEmpty() || pendingRequest != null) clear()
            return
        }
        val link = hoveredImageLink(mouseX, mouseY, displayMode)
        if (link == null) {
            candidate = null
            cancelPendingRequest()
            return
        }
        val current = candidate
        candidate = if (current?.url == link.url) {
            current.copy(mouseX = mouseX, mouseY = mouseY)
        } else {
            cancelPendingRequest()
            ImageLinkCandidate(link.url, link.requestUri, link.host, link.isTrusted, mouseX, mouseY)
        }
        if (isImageRevealRequested()) {
            requestCandidateImage()
        } else {
            cancelPendingRequest()
        }
    }

    fun endChatSession() {
        if (candidate != null || textures.isNotEmpty() || pendingRequest != null) clear()
    }

    fun processTrustClick(button: Int): InputHandlingResult {
        if (!SkysoftConfigGui.config().chat.previewImage.enabled || button != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return InputHandlingResult.IGNORED
        }
        val current = candidate ?: return InputHandlingResult.IGNORED
        if (current.isTrusted || !Minecraft.getInstance().hasShiftDown()) return InputHandlingResult.IGNORED
        TrustedImageHosts.trust(current.host)
        candidate = current.copy(isTrusted = true)
        return InputHandlingResult.CONSUMED
    }

    private fun hoveredImageLink(mouseX: Int, mouseY: Int, displayMode: ChatComponent.DisplayMode): ResolvedImageLink? {
        val minecraft = Minecraft.getInstance()
        val finder = ActiveTextCollector.ClickableStyleFinder(minecraft.font, mouseX, mouseY)
        MinecraftClient.chat(minecraft).captureClickableText(
            finder,
            minecraft.window.guiScaledHeight,
            MinecraftClient.guiTicks(minecraft),
            displayMode,
        )
        val event = finder.result()?.clickEvent as? ClickEvent.OpenUrl ?: return null
        return ImageUrlResolver.resolve(event.uri())
    }

    private fun requestCandidateImage() {
        val current = candidate ?: return
        if (
            !current.isTrusted ||
            !isImageRevealRequested() ||
            current.url in textures ||
            current.url in failures ||
            pendingUrl == current.url
        ) {
            return
        }
        pendingUrl = current.url
        val localScreenshot = ScreenshotUploadMetadataStore.screenshotForUrl(current.url)
        val request = if (localScreenshot != null) {
            loadScaledScreenshotImage(
                localScreenshot,
                MAXIMUM_IMAGE_PREVIEW_TEXTURE_WIDTH,
                MAXIMUM_IMAGE_PREVIEW_TEXTURE_HEIGHT,
            )
        } else {
            RemoteImageLoader.load(current.requestUri)
        }
        pendingRequest = request
        isPendingRequestRemote = localScreenshot == null
        request.whenComplete { image, failure ->
            Minecraft.getInstance().execute {
                val isCurrentRequest = pendingRequest === request
                if (isCurrentRequest) {
                    pendingUrl = null
                    pendingRequest = null
                    isPendingRequestRemote = false
                }
                when {
                    image != null && isCurrentRequest && SkysoftConfigGui.config().chat.previewImage.enabled -> {
                        installTexture(current.url, image)
                    }
                    image != null -> image.close()
                    failure != null && isCurrentRequest &&
                        SkysoftConfigGui.config().chat.previewImage.enabled -> failures += current.url
                }
            }
        }
    }

    private fun installTexture(url: String, image: NativeImage) {
        textures.remove(url)?.let(::release)
        val id = SkysoftMod.id("image_preview/remote_${nextTextureId++}")
        val texture = try {
            DynamicTexture({ "Skysoft Image Preview" }, image)
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
        textures[url] = RemoteImageTexture(id, texture, image.width, image.height)
        while (textures.size > CACHE_SIZE) {
            val eldest = textures.entries.iterator().next()
            textures.remove(eldest.key)
            release(eldest.value)
        }
    }

    private fun render(context: GuiGraphicsExtractor) {
        requestCandidateImage()
        val current = candidate ?: return
        val isImageRevealRequested = isImageRevealRequested()
        val texture = textures[current.url]?.takeIf { isImageRevealRequested }
        val lines = wrappedLines(current.url, context.guiWidth() - URL_HORIZONTAL_INSET)
        val imageBounds = texture?.let { previewImageBounds(it) }
        val instruction = when {
            !current.isTrusted -> "Shift-click the link to trust ${current.host}"
            !isImageRevealRequested -> "Hold ${previewKeyName()} to show the image"
            current.url in failures -> "Image preview unavailable"
            texture == null -> "Loading image..."
            else -> "Click to open"
        }
        val contentWidth = maxOf(
            imageBounds?.width ?: 0,
            lines.maxOfOrNull { Minecraft.getInstance().font.width(it) } ?: 0,
            Minecraft.getInstance().font.width(instruction),
        )
        val imageHeight = imageBounds?.height ?: 0
        val panelWidth = contentWidth + PANEL_PADDING * 2
        val panelHeight = imageHeight + (if (imageHeight > 0) CONTENT_GAP else 0) +
            lines.size * TEXT_LINE_HEIGHT + CONTENT_GAP + TEXT_LINE_HEIGHT + PANEL_PADDING * 2
        val panel = positionedPanel(current, panelWidth, panelHeight, context.guiWidth(), context.guiHeight())
        OverlayPanelStyle.draw(context, panel.x, panel.y, panel.width, panel.height)
        var contentY = panel.y + PANEL_PADDING
        if (texture != null && imageBounds != null) {
            val x = panel.x + (panel.width - imageBounds.width) / 2
            context.blit(
                texture.texture.textureView,
                RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR),
                x,
                contentY,
                x + imageBounds.width,
                contentY + imageBounds.height,
                0f,
                1f,
                0f,
                1f,
            )
            contentY += imageBounds.height + CONTENT_GAP
        }
        val font = Minecraft.getInstance().font
        lines.forEach { line ->
            context.text(font, line, panel.x + PANEL_PADDING, contentY, URL_COLOR, false)
            contentY += TEXT_LINE_HEIGHT
        }
        context.text(font, instruction, panel.x + PANEL_PADDING, contentY + CONTENT_GAP, INSTRUCTION_COLOR, false)
    }

    private fun previewImageBounds(texture: RemoteImageTexture): Rect {
        val scale = min(
            min(MAXIMUM_RENDER_WIDTH.toDouble() / texture.width, MAXIMUM_RENDER_HEIGHT.toDouble() / texture.height),
            1.0,
        )
        return Rect(
            0,
            0,
            (texture.width * scale).roundToInt().coerceAtLeast(1),
            (texture.height * scale).roundToInt().coerceAtLeast(1),
        )
    }

    private fun positionedPanel(
        current: ImageLinkCandidate,
        width: Int,
        height: Int,
        screenWidth: Int,
        screenHeight: Int,
    ): Rect {
        val preferredX = current.mouseX + CURSOR_OFFSET
        val x = if (preferredX + width <= screenWidth - SCREEN_INSET) preferredX else current.mouseX - width - CURSOR_OFFSET
        val preferredY = current.mouseY - height / 2
        return Rect(
            x.coerceIn(SCREEN_INSET, (screenWidth - width - SCREEN_INSET).coerceAtLeast(SCREEN_INSET)),
            preferredY.coerceIn(SCREEN_INSET, (screenHeight - height - SCREEN_INSET).coerceAtLeast(SCREEN_INSET)),
            width,
            height,
        )
    }

    private fun wrappedLines(text: String, maximumWidth: Int): List<String> {
        val font = Minecraft.getInstance().font
        if (font.width(text) <= maximumWidth) return listOf(text)
        val lines = mutableListOf<String>()
        var remaining = text
        while (remaining.isNotEmpty()) {
            val line = font.plainSubstrByWidth(remaining, maximumWidth).ifEmpty { remaining.take(1) }
            lines += line
            remaining = remaining.removePrefix(line)
        }
        return lines
    }

    private fun isImageRevealRequested(): Boolean {
        val key = SkysoftConfigGui.config().chat.previewImage.settings.previewKey
        return key == GLFW.GLFW_KEY_UNKNOWN || InputUtilities.isKeyDown(key)
    }

    private fun previewKeyName(): String {
        val key = SkysoftConfigGui.config().chat.previewImage.settings.previewKey
        return InputConstants.Type.KEYSYM.getOrCreate(key).displayName.string
    }

    private fun cancelPendingRequest() {
        if (isPendingRequestRemote) pendingRequest?.cancel(true)
        RemoteImageLoader.cancel()
        pendingRequest = null
        pendingUrl = null
        isPendingRequestRemote = false
    }

    private fun clear() {
        candidate = null
        cancelPendingRequest()
        textures.values.forEach(::release)
        textures.clear()
        failures.clear()
    }

    private fun release(texture: RemoteImageTexture) {
        Minecraft.getInstance().textureManager.release(texture.id)
    }

    private const val CACHE_SIZE = 8
    private const val MAXIMUM_RENDER_WIDTH = 420
    private const val MAXIMUM_RENDER_HEIGHT = 236
    private const val PANEL_PADDING = 5
    private const val CONTENT_GAP = 4
    private const val TEXT_LINE_HEIGHT = 10
    private const val CURSOR_OFFSET = 12
    private const val SCREEN_INSET = 8
    private const val URL_HORIZONTAL_INSET = 32
    private const val URL_COLOR = 0xFFFFFFFF.toInt()
    private const val INSTRUCTION_COLOR = 0xFFAAAAAA.toInt()
}

internal object ImageUrlResolver {
    private val commonHosts = setOf(
        "i.ibb.co",
        "ibb.co",
        "www.ibb.co",
        "i.imgur.com",
        "imgur.com",
        "www.imgur.com",
        "cdn.discordapp.com",
        "media.discordapp.net",
        "images-ext-1.discordapp.net",
        "images-ext-2.discordapp.net",
        "cdn.modrinth.com",
        "raw.githubusercontent.com",
        "user-images.githubusercontent.com",
        "media.githubusercontent.com",
        "media.tenor.com",
        "c.tenor.com",
        "media.giphy.com",
        "i.giphy.com",
        "pbs.twimg.com",
        "gyazo.com",
        "i.gyazo.com",
    )
    private val pageHosts = setOf(
        "ibb.co",
        "www.ibb.co",
        "imgur.com",
        "www.imgur.com",
        "gyazo.com",
    )
    private val imageExtensions = setOf("png", "jpg", "jpeg", "gif", "bmp", "wbmp")
    private val imgurId = Regex("[A-Za-z0-9]+")

    fun resolve(uri: URI): ResolvedImageLink? {
        if (!hasSupportedOrigin(uri)) return null
        val host = uri.host?.lowercase(Locale.ROOT) ?: return null
        if (host == "localhost" || host.endsWith(".local")) return null
        val requestUri = when (host) {
            "imgur.com", "www.imgur.com" -> resolveImgur(uri) ?: return null
            "media.discordapp.net", "pbs.twimg.com" -> requestPngWhenWebP(uri)
            else -> uri
        }
        val format = requestUri.query
            ?.split('&')
            ?.firstNotNullOfOrNull { parameter ->
                parameter.substringAfter("format=", "").substringBefore('&').takeIf(String::isNotEmpty)
            }
            ?.lowercase(Locale.ROOT)
            ?: requestUri.path.substringAfterLast('.', "").lowercase(Locale.ROOT)
        if (format !in imageExtensions && host !in pageHosts) return null
        val url = uri.toASCIIString()
        if (url.length > MAXIMUM_URL_LENGTH) return null
        return ResolvedImageLink(
            url = url,
            requestUri = requestUri,
            host = host,
            isTrusted = host in commonHosts || TrustedImageHosts.isTrusted(host),
        )
    }

    fun isTrusted(uri: URI): Boolean {
        if (!hasSupportedOrigin(uri)) return false
        val host = uri.host?.lowercase(Locale.ROOT) ?: return false
        return host in commonHosts || TrustedImageHosts.isTrusted(host)
    }

    private fun hasSupportedOrigin(uri: URI): Boolean =
        uri.scheme?.lowercase(Locale.ROOT) == "https" &&
            uri.userInfo == null &&
            uri.port in listOf(DEFAULT_PORT, HTTPS_PORT)

    private fun resolveImgur(uri: URI): URI? {
        val id = uri.path.trim('/').takeIf { it.matches(imgurId) } ?: return null
        return URI.create("https://i.imgur.com/$id.png")
    }

    private fun requestPngWhenWebP(uri: URI): URI {
        val query = uri.rawQuery ?: return uri
        if (!query.split('&').any { it.equals("format=webp", ignoreCase = true) }) return uri
        val pngQuery = query.split('&').joinToString("&") { parameter ->
            if (parameter.equals("format=webp", ignoreCase = true)) "format=png" else parameter
        }
        return URI(uri.scheme, uri.userInfo, uri.host, uri.port, uri.path, pngQuery, null)
    }

    private const val DEFAULT_PORT = -1
    private const val HTTPS_PORT = 443
    private const val MAXIMUM_URL_LENGTH = 2048
}

private object TrustedImageHosts {
    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val hostListType = object : TypeToken<List<String>>() {}.type
    private val path = FabricLoader.getInstance().configDir.resolve("skysoft").resolve("trusted-image-hosts.json")
    private var hosts: MutableSet<String>? = null

    @Synchronized
    fun isTrusted(host: String): Boolean = host in hosts()

    @Synchronized
    fun trust(host: String) {
        if (!hosts().add(host)) return
        Files.createDirectories(path.parent)
        Files.newBufferedWriter(path).use { writer -> gson.toJson(hosts().sorted(), hostListType, writer) }
    }

    private fun hosts(): MutableSet<String> {
        hosts?.let { return it }
        val loaded = if (Files.isRegularFile(path)) {
            Files.newBufferedReader(path).use { reader -> gson.fromJson<List<String>>(reader, hostListType).orEmpty() }
        } else {
            emptyList()
        }
        return loaded.mapTo(linkedSetOf()) { it.lowercase(Locale.ROOT) }.also { hosts = it }
    }
}

internal data class ResolvedImageLink(
    val url: String,
    val requestUri: URI,
    val host: String,
    val isTrusted: Boolean,
)

private data class ImageLinkCandidate(
    val url: String,
    val requestUri: URI,
    val host: String,
    val isTrusted: Boolean,
    val mouseX: Int,
    val mouseY: Int,
)

private data class RemoteImageTexture(
    val id: Identifier,
    val texture: DynamicTexture,
    val width: Int,
    val height: Int,
)
