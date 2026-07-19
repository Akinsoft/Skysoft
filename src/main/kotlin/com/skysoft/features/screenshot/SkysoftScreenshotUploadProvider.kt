package com.skysoft.features.screenshot

import com.google.gson.JsonParser
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CompletableFuture

internal interface ScreenshotUploadProvider {
    fun upload(path: Path): CompletableFuture<ScreenshotUpload>
}

internal object SkysoftScreenshotUploadProvider : ScreenshotUploadProvider {
    private const val EXPIRATION_SECONDS = 30L * 24L * 60L * 60L
    private val client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(CONNECT_TIMEOUT_SECONDS))
        .build()

    override fun upload(path: Path): CompletableFuture<ScreenshotUpload> {
        val boundary = "Skysoft-${UUID.randomUUID()}"
        val body = HttpRequest.BodyPublishers.concat(
            HttpRequest.BodyPublishers.ofByteArray(uploadHeader(boundary, path)),
            HttpRequest.BodyPublishers.ofFile(path),
            HttpRequest.BodyPublishers.ofByteArray("\r\n--$boundary--\r\n".toByteArray(StandardCharsets.UTF_8)),
        )
        val request = HttpRequest.newBuilder(URI.create(UPLOAD_URL))
            .timeout(Duration.ofSeconds(UPLOAD_TIMEOUT_SECONDS))
            .header("User-Agent", "Skysoft")
            .header("Content-Type", "multipart/form-data; boundary=$boundary")
            .POST(body)
            .build()
        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
            .thenApply { response -> readUpload(response.statusCode(), response.body()) }
    }

    private fun uploadHeader(boundary: String, path: Path): ByteArray {
        val fileName = path.fileName.toString().replace('"', '_')
        return (
            "--$boundary\r\n" +
                "Content-Disposition: form-data; name=\"image\"; filename=\"$fileName\"\r\n" +
                "Content-Type: image/png\r\n\r\n"
            ).toByteArray(StandardCharsets.UTF_8)
    }

    private fun readUpload(statusCode: Int, responseBody: String): ScreenshotUpload {
        val data = responseData(statusCode, responseBody)
        val imageUrl = data.requiredHttpsUrl("url", "i.ibb.co")
        val pageUrl = data.requiredHttpsUrl("url_viewer", "ibb.co")
        val deleteUrl = data.requiredHttpsUrl("delete_url", "ibb.co")
        val width = data.get("width")?.asString?.toIntOrNull() ?: 0
        val height = data.get("height")?.asString?.toIntOrNull() ?: 0
        return ScreenshotUpload(
            imageUrl = imageUrl,
            pageUrl = pageUrl,
            deleteUrl = deleteUrl,
            width = width,
            height = height,
            expiresAtEpochSecond = Instant.now().epochSecond + EXPIRATION_SECONDS,
        )
    }

    private fun responseData(statusCode: Int, responseBody: String): com.google.gson.JsonObject {
        require(statusCode in 200..299) { "ImgBB returned HTTP $statusCode" }
        val response = JsonParser.parseString(responseBody).asJsonObject
        require(response.get("success").asBoolean) { "ImgBB rejected the upload" }
        return requireNotNull(response.getAsJsonObject("data")) { "ImgBB omitted upload data" }
    }

    private fun com.google.gson.JsonObject.requiredHttpsUrl(field: String, expectedHost: String): String {
        val value = get(field)?.asString ?: throw IllegalStateException("ImgBB omitted $field")
        val uri = URI.create(value)
        require(uri.scheme == "https" && uri.host.equals(expectedHost, ignoreCase = true)) {
            "ImgBB returned an invalid $field"
        }
        return value
    }

    private const val UPLOAD_URL = "https://api.findthesoft.com/screenshots"
    private const val CONNECT_TIMEOUT_SECONDS = 10L
    private const val UPLOAD_TIMEOUT_SECONDS = 70L
}

internal data class ScreenshotUpload(
    val imageUrl: String,
    val pageUrl: String,
    val deleteUrl: String,
    val width: Int,
    val height: Int,
    val expiresAtEpochSecond: Long,
)
