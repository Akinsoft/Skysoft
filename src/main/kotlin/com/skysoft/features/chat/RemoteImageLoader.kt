package com.skysoft.features.chat

import com.mojang.blaze3d.platform.NativeImage
import com.skysoft.utils.image.ScaledImageDecoder
import java.io.InputStream
import java.net.Inet6Address
import java.net.InetAddress
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.Locale
import java.util.concurrent.CompletableFuture
import net.minecraft.util.Util

internal interface RemoteImageRequest {
    val future: CompletableFuture<NativeImage>
    fun cancel()
}

internal object RemoteImageLoader {
    private val client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(CONNECT_TIMEOUT_SECONDS))
        .build()
    private val imageMetaPattern = Regex(
        """<meta\b[^>]*(?:property|name)\s*=\s*["'](?:og:image(?::url)?|twitter:image)["'][^>]*""" +
            """content\s*=\s*["'](?<url>[^"']+)["'][^>]*>""",
        RegexOption.IGNORE_CASE,
    )
    private val reversedImageMetaPattern = Regex(
        """<meta\b[^>]*content\s*=\s*["'](?<url>[^"']+)["'][^>]*""" +
            """(?:property|name)\s*=\s*["'](?:og:image(?::url)?|twitter:image)["'][^>]*>""",
        RegexOption.IGNORE_CASE,
    )
    private val imgBbViewerImagePattern = Regex(
        """<img\b[^>]*src\s*=\s*["'](?<url>https://i\.ibb\.co/[^"']+)["'][^>]*""" +
            """data-load\s*=\s*["']full["'][^>]*>""",
        RegexOption.IGNORE_CASE,
    )
    private val redirectStatusCodes = setOf(301, 302, 303, 307, 308)
    private val supportedContentTypes = setOf(
        "image/png",
        "image/jpeg",
        "image/gif",
        "image/bmp",
        "image/x-ms-bmp",
        "image/vnd.wap.wbmp",
    )

    fun load(uri: URI): RemoteImageRequest = ActiveRemoteImageRequest(uri)

    private fun createRequest(uri: URI): HttpRequest {
        require(ImageUrlResolver.isTrusted(uri)) { "Image request changed to an untrusted host" }
        requirePublicAddress(requireNotNull(uri.host))
        return HttpRequest.newBuilder(uri)
            .timeout(Duration.ofSeconds(REQUEST_TIMEOUT_SECONDS))
            .header("User-Agent", "Skysoft")
            .GET()
            .build()
    }

    private fun readResponse(
        uri: URI,
        redirectCount: Int,
        htmlDepth: Int,
        response: HttpResponse<InputStream>,
        body: InputStream,
    ): ImageResponse {
        if (response.statusCode() in redirectStatusCodes) {
            require(redirectCount < MAXIMUM_REDIRECTS) { "Image request redirected too many times" }
            val location = response.headers().firstValue("Location").orElseThrow {
                IllegalStateException("Image redirect omitted its destination")
            }
            return ImageResponse.Follow(uri.resolve(location), redirectCount + 1, htmlDepth)
        }
        require(response.statusCode() in 200..299) { "Image request returned HTTP ${response.statusCode()}" }
        val contentType = response.headers().firstValue("Content-Type").orElse("")
            .substringBefore(';')
            .lowercase(Locale.ROOT)
        return when {
            contentType in supportedContentTypes -> ImageResponse.Decoded(
                decode(readBody(response, body, MAXIMUM_DOWNLOAD_BYTES, "Image download is too large")),
            )
            contentType == HTML_CONTENT_TYPE && htmlDepth < MAXIMUM_HTML_DEPTH -> {
                val html = readBody(response, body, MAXIMUM_HTML_BYTES, "Image page is too large")
                    .toString(Charsets.UTF_8)
                ImageResponse.Follow(imageUriFromHtml(uri, html), redirectCount, htmlDepth + 1)
            }
            else -> throw IllegalStateException("Unsupported image content type")
        }
    }

    private fun readBody(
        response: HttpResponse<InputStream>,
        body: InputStream,
        maximumBytes: Int,
        failureMessage: String,
    ): ByteArray {
        val contentLength = response.headers().firstValueAsLong("Content-Length").orElse(-1L)
        require(contentLength <= maximumBytes || contentLength < 0L) { failureMessage }
        val bytes = body.readNBytes(maximumBytes + 1)
        require(bytes.size <= maximumBytes) { failureMessage }
        return bytes
    }

    private fun imageUriFromHtml(pageUri: URI, html: String): URI {
        val host = pageUri.host.lowercase(Locale.ROOT)
        val match = if (host == "ibb.co" || host == "www.ibb.co") {
            imgBbViewerImagePattern.find(html)
        } else {
            imageMetaPattern.find(html) ?: reversedImageMetaPattern.find(html)
        }
        val value = match?.groups?.get("url")?.value ?: throw IllegalStateException("Image page omitted its preview")
        return pageUri.resolve(value.replace("&amp;", "&"))
    }

    private fun decode(bytes: ByteArray): NativeImage =
        ScaledImageDecoder.decode(bytes, MAXIMUM_IMAGE_PREVIEW_TEXTURE_WIDTH, MAXIMUM_IMAGE_PREVIEW_TEXTURE_HEIGHT)

    private fun requirePublicAddress(host: String) {
        val addresses = InetAddress.getAllByName(host)
        require(addresses.isNotEmpty() && addresses.all(::isPublicAddress)) { "Image host does not resolve publicly" }
    }

    private fun isPublicAddress(address: InetAddress): Boolean {
        if (
            address.isAnyLocalAddress ||
            address.isLoopbackAddress ||
            address.isLinkLocalAddress ||
            address.isSiteLocalAddress ||
            address.isMulticastAddress
        ) {
            return false
        }
        val bytes = address.address
        return address !is Inet6Address ||
            (bytes[0].toInt() and IPV6_UNIQUE_LOCAL_MASK) != IPV6_UNIQUE_LOCAL_PREFIX
    }

    private class ActiveRemoteImageRequest(initialUri: URI) : RemoteImageRequest {
        private val lock = Any()
        private val activeTasks = mutableSetOf<CompletableFuture<*>>()
        private val activeBodies = mutableSetOf<InputStream>()
        private var isCancelled = false

        override val future = CompletableFuture<NativeImage>()

        init {
            future.whenComplete { _, _ -> if (future.isCancelled) cancel() }
            download(initialUri, 0, 0)
        }

        override fun cancel() {
            val active = synchronized(lock) {
                if (isCancelled) return
                isCancelled = true
                ActiveResources(activeTasks.toList(), activeBodies.toList()).also {
                    activeTasks.clear()
                    activeBodies.clear()
                }
            }
            future.cancel(true)
            active.bodies.forEach { runCatching(it::close) }
            active.tasks.forEach { it.cancel(true) }
        }

        private fun download(uri: URI, redirectCount: Int, htmlDepth: Int) {
            if (cancelled()) return
            val preparation = CompletableFuture.supplyAsync({ createRequest(uri) }, Util.ioPool())
            track(preparation)
            preparation.whenComplete { request, failure ->
                when {
                    cancelled() -> Unit
                    failure != null -> fail(failure)
                    request != null -> send(uri, redirectCount, htmlDepth, request)
                }
            }
        }

        private fun send(uri: URI, redirectCount: Int, htmlDepth: Int, request: HttpRequest) {
            val responseFuture = synchronized(lock) {
                if (isCancelled) return
                client.sendAsync(request, HttpResponse.BodyHandlers.ofInputStream()).also { activeTasks += it }
            }
            responseFuture.whenComplete { response, failure ->
                removeTask(responseFuture)
                when {
                    cancelled() -> response?.body()?.let { runCatching(it::close) }
                    failure != null -> fail(failure)
                    response != null -> process(uri, redirectCount, htmlDepth, response)
                }
            }
        }

        private fun process(
            uri: URI,
            redirectCount: Int,
            htmlDepth: Int,
            response: HttpResponse<InputStream>,
        ) {
            val body = response.body()
            val accepted = synchronized(lock) {
                if (isCancelled) false else activeBodies.add(body)
            }
            if (!accepted) {
                runCatching(body::close)
                return
            }
            CompletableFuture.supplyAsync(
                { body.use { readResponse(uri, redirectCount, htmlDepth, response, it) } },
                Util.ioPool(),
            ).whenComplete { result, failure ->
                synchronized(lock) { activeBodies -= body }
                when {
                    failure != null -> fail(failure)
                    result is ImageResponse.Decoded && cancelled() -> result.image.close()
                    result is ImageResponse.Decoded -> if (!future.complete(result.image)) result.image.close()
                    result is ImageResponse.Follow && !cancelled() ->
                        download(result.uri, result.redirectCount, result.htmlDepth)
                }
            }
        }

        private fun track(task: CompletableFuture<*>) {
            val cancelImmediately = synchronized(lock) {
                if (isCancelled) true else {
                    activeTasks += task
                    false
                }
            }
            if (cancelImmediately) task.cancel(true)
            task.whenComplete { _, _ -> removeTask(task) }
        }

        private fun removeTask(task: CompletableFuture<*>) {
            synchronized(lock) { activeTasks -= task }
        }

        private fun cancelled(): Boolean = synchronized(lock) { isCancelled }

        private fun fail(failure: Throwable) {
            if (!cancelled()) future.completeExceptionally(failure)
        }
    }

    private sealed interface ImageResponse {
        data class Decoded(val image: NativeImage) : ImageResponse
        data class Follow(val uri: URI, val redirectCount: Int, val htmlDepth: Int) : ImageResponse
    }

    private data class ActiveResources(
        val tasks: List<CompletableFuture<*>>,
        val bodies: List<InputStream>,
    )

    private const val CONNECT_TIMEOUT_SECONDS = 8L
    private const val REQUEST_TIMEOUT_SECONDS = 15L
    private const val MAXIMUM_REDIRECTS = 3
    private const val MAXIMUM_HTML_DEPTH = 1
    private const val MAXIMUM_HTML_BYTES = 1024 * 1024
    private const val HTML_CONTENT_TYPE = "text/html"
    private const val MAXIMUM_DOWNLOAD_BYTES = 8 * 1024 * 1024
    private const val IPV6_UNIQUE_LOCAL_MASK = 0xFE
    private const val IPV6_UNIQUE_LOCAL_PREFIX = 0xFC
}
