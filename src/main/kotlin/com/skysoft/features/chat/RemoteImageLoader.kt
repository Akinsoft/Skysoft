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

    @Volatile
    private var activeHttpRequest: CompletableFuture<*>? = null

    @Volatile
    private var activeBody: InputStream? = null

    fun load(uri: URI): CompletableFuture<NativeImage> = download(uri, 0, 0)

    fun cancel() {
        activeBody?.let { runCatching(it::close) }
        activeBody = null
        activeHttpRequest?.cancel(true)
        activeHttpRequest = null
    }

    private fun download(
        uri: URI,
        redirectCount: Int,
        htmlDepth: Int,
    ): CompletableFuture<NativeImage> = CompletableFuture.supplyAsync(
        {
            require(ImageUrlResolver.isTrusted(uri)) { "Image request changed to an untrusted host" }
            requirePublicAddress(requireNotNull(uri.host))
            HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(REQUEST_TIMEOUT_SECONDS))
                .header("User-Agent", "Skysoft")
                .GET()
                .build()
        },
        Util.ioPool(),
    ).thenCompose { request ->
        client.sendAsync(request, HttpResponse.BodyHandlers.ofInputStream()).also { activeHttpRequest = it }
    }.thenComposeAsync({ response ->
        readResponse(uri, redirectCount, htmlDepth, response)
    }, Util.ioPool())

    private fun readResponse(
        uri: URI,
        redirectCount: Int,
        htmlDepth: Int,
        response: HttpResponse<InputStream>,
    ): CompletableFuture<NativeImage> {
        val body = response.body()
        activeBody = body
        try {
            if (response.statusCode() in redirectStatusCodes) {
                require(redirectCount < MAXIMUM_REDIRECTS) { "Image request redirected too many times" }
                val location = response.headers().firstValue("Location").orElseThrow {
                    IllegalStateException("Image redirect omitted its destination")
                }
                return download(uri.resolve(location), redirectCount + 1, htmlDepth)
            }
            require(response.statusCode() in 200..299) { "Image request returned HTTP ${response.statusCode()}" }
            val contentType = response.headers().firstValue("Content-Type").orElse("")
                .substringBefore(';')
                .lowercase(Locale.ROOT)
            return when {
                contentType in supportedContentTypes -> CompletableFuture.completedFuture(
                    decode(readBody(response, body, MAXIMUM_DOWNLOAD_BYTES, "Image download is too large")),
                )
                contentType == HTML_CONTENT_TYPE && htmlDepth < MAXIMUM_HTML_DEPTH -> {
                    val html = readBody(response, body, MAXIMUM_HTML_BYTES, "Image page is too large")
                        .toString(Charsets.UTF_8)
                    download(imageUriFromHtml(uri, html), redirectCount, htmlDepth + 1)
                }
                else -> CompletableFuture.failedFuture(IllegalStateException("Unsupported image content type"))
            }
        } catch (failure: Exception) {
            return CompletableFuture.failedFuture(failure)
        } finally {
            if (activeBody === body) activeBody = null
            body.close()
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
