package com.skysoft.features.spotify

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.skysoft.SkysoftMod
import com.skysoft.utils.net.SkysoftHttp
import java.net.URI
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.CompletableFuture
import kotlin.math.abs

internal object SpotifyWebApi {
    fun currentPlayback(): CompletableFuture<SpotifyPlayback?> = SpotifyAuthentication.accessToken().thenCompose { token ->
        if (token == null) return@thenCompose CompletableFuture.completedFuture(null)
        val request = request(PLAYER_ENDPOINT, token).GET().build()
        SkysoftHttp.sendString(request).thenApply { response ->
            when (response.statusCode()) {
                HTTP_OK -> parsePlayback(response.body())
                HTTP_NO_CONTENT -> null
                HTTP_UNAUTHORIZED -> {
                    SpotifyAuthentication.invalidateAccessToken()
                    throw SpotifyApiException(response.statusCode())
                }
                else -> throw SpotifyApiException(response.statusCode(), retryAfterMillis(response))
            }
        }
    }

    fun control(action: SpotifyPlaybackAction): CompletableFuture<Unit> = SpotifyAuthentication.accessToken().thenCompose { token ->
        if (token == null) return@thenCompose CompletableFuture.failedFuture(SpotifyApiException(HTTP_UNAUTHORIZED))
        val request = request("$PLAYER_ENDPOINT/${action.path}", token)
            .method(action.method, HttpRequest.BodyPublishers.noBody())
            .build()
        SkysoftHttp.sendString(request).thenApply { response ->
            if (response.statusCode() !in HTTP_SUCCESS) {
                if (response.statusCode() == HTTP_UNAUTHORIZED) SpotifyAuthentication.invalidateAccessToken()
                throw SpotifyApiException(response.statusCode(), retryAfterMillis(response))
            }
        }
    }

    private fun request(url: String, token: String): HttpRequest.Builder = HttpRequest.newBuilder(URI.create(url))
        .timeout(Duration.ofSeconds(REQUEST_TIMEOUT_SECONDS))
        .header("Authorization", "Bearer $token")
        .header("User-Agent", "Skysoft/${SkysoftMod.VERSION}")

    private fun parsePlayback(body: String): SpotifyPlayback? {
        val json = JsonParser.parseString(body).asJsonObject
        val item = json.objectValue("item") ?: return null
        val type = item.stringValue("type") ?: return null
        val title = item.stringValue("name") ?: return null
        val durationMillis = item.longValue("duration_ms")?.takeIf { it > 0 } ?: return null
        val trackDetails = if (type == TRACK_TYPE) parseTrackDetails(item) else parseEpisodeDetails(item)
        val identity = item.stringValue("id") ?: item.stringValue("uri")
            ?: "$type:$title:${trackDetails.subtitle}:$durationMillis"
        return SpotifyPlayback(
            identity = identity,
            title = title,
            subtitle = trackDetails.subtitle,
            collection = trackDetails.collection,
            artworkUrl = trackDetails.artworkUrl,
            durationMillis = durationMillis,
            progressMillis = json.longValue("progress_ms")?.coerceIn(0, durationMillis) ?: 0,
            playing = json.booleanValue("is_playing") == true,
            receivedAtMillis = System.currentTimeMillis(),
            supportsLyrics = type == TRACK_TYPE,
        )
    }

    private fun parseTrackDetails(item: JsonObject): PlaybackDetails {
        val album = item.objectValue("album")
        return PlaybackDetails(
            subtitle = item.arrayValue("artists").names().joinToString(", "),
            collection = album?.stringValue("name").orEmpty(),
            artworkUrl = album?.arrayValue("images").artworkUrl(),
        )
    }

    private fun parseEpisodeDetails(item: JsonObject): PlaybackDetails {
        val show = item.objectValue("show")
        return PlaybackDetails(
            subtitle = show?.stringValue("name").orEmpty(),
            collection = "Podcast",
            artworkUrl = item.arrayValue("images").artworkUrl(),
        )
    }

    private fun JsonArray?.names(): List<String> = this?.mapNotNull { element ->
        element.takeIf { it.isJsonObject }?.asJsonObject?.stringValue("name")
    }.orEmpty()

    private fun JsonArray?.artworkUrl(): String? = this?.mapNotNull { element ->
        val image = element.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
        val url = image.stringValue("url") ?: return@mapNotNull null
        val width = image.longValue("width")?.toInt() ?: TARGET_ARTWORK_SIZE
        ArtworkCandidate(url, abs(width - TARGET_ARTWORK_SIZE))
    }?.minByOrNull(ArtworkCandidate::distance)?.url

    private fun JsonObject.stringValue(name: String): String? = get(name)?.takeUnless { it.isJsonNull }?.asString
    private fun JsonObject.longValue(name: String): Long? = get(name)?.takeUnless { it.isJsonNull }?.asLong
    private fun JsonObject.booleanValue(name: String): Boolean? = get(name)?.takeUnless { it.isJsonNull }?.asBoolean
    private fun JsonObject.objectValue(name: String): JsonObject? = get(name)?.takeIf { it.isJsonObject }?.asJsonObject
    private fun JsonObject.arrayValue(name: String): JsonArray? = get(name)?.takeIf { it.isJsonArray }?.asJsonArray

    private fun retryAfterMillis(response: HttpResponse<*>): Long? = response.headers()
        .firstValue("Retry-After")
        .orElse(null)
        ?.toLongOrNull()
        ?.times(MILLIS_PER_SECOND)

    private const val PLAYER_ENDPOINT = "https://api.spotify.com/v1/me/player"
    private const val TRACK_TYPE = "track"
    private const val REQUEST_TIMEOUT_SECONDS = 15L
    private const val TARGET_ARTWORK_SIZE = 300
    private const val MILLIS_PER_SECOND = 1_000L
    private const val HTTP_OK = 200
    private const val HTTP_NO_CONTENT = 204
    private const val HTTP_UNAUTHORIZED = 401
    private val HTTP_SUCCESS = 200..299
}

internal data class SpotifyPlayback(
    val identity: String,
    val title: String,
    val subtitle: String,
    val collection: String,
    val artworkUrl: String?,
    val durationMillis: Long,
    val progressMillis: Long,
    val playing: Boolean,
    val receivedAtMillis: Long,
    val supportsLyrics: Boolean,
) {
    fun positionAt(nowMillis: Long): Long =
        (progressMillis + if (playing) (nowMillis - receivedAtMillis).coerceAtLeast(0) else 0).coerceIn(0, durationMillis)
}

internal enum class SpotifyPlaybackAction(val path: String, val method: String) {
    PLAY("play", "PUT"),
    PAUSE("pause", "PUT"),
    PREVIOUS("previous", "POST"),
    NEXT("next", "POST"),
}

internal class SpotifyApiException(
    val statusCode: Int,
    val retryAfterMillis: Long? = null,
) : IllegalStateException("Spotify API returned HTTP $statusCode")

private data class PlaybackDetails(val subtitle: String, val collection: String, val artworkUrl: String?)
private data class ArtworkCandidate(val url: String, val distance: Int)
