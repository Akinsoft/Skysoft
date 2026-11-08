package com.skysoft.features.spotify

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.skysoft.utils.MILLIS_PER_SECOND
import com.skysoft.utils.net.CancellableRequestGroup
import com.skysoft.utils.net.SkysoftHttp
import java.net.URI
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.CompletableFuture
import kotlin.math.abs

internal object SpotifyWebApi {
    fun currentPlayback(): CompletableFuture<SpotifyPlayback?> = sendAuthorized(
        { CompletableFuture.completedFuture(null) },
        { request(PLAYER_ENDPOINT, it).GET() },
    ) { response ->
        when (response.statusCode()) {
            HTTP_OK -> parsePlayback(response.body())
            HTTP_NO_CONTENT -> null
            HTTP_UNAUTHORIZED -> throw SpotifyApiException(response.statusCode())
            else -> throw spotifyApiException(response)
        }
    }

    fun control(action: SpotifyPlaybackAction): CompletableFuture<Unit> = sendAuthorized(
        { CompletableFuture.failedFuture(SpotifyApiException(HTTP_UNAUTHORIZED)) },
        { request("$PLAYER_ENDPOINT/${action.path}", it).method(action.method, HttpRequest.BodyPublishers.noBody()) },
    ) { response ->
        if (response.statusCode() !in HTTP_SUCCESS) throw spotifyApiException(response)
    }

    private fun <T> sendAuthorized(
        withoutToken: () -> CompletableFuture<T>,
        buildRequest: (String) -> HttpRequest.Builder,
        handleResponse: (HttpResponse<String>) -> T,
    ): CompletableFuture<T> {
        val requests = CancellableRequestGroup()
        val operation = SpotifyAuthentication.accessToken().thenCompose { token ->
            if (token == null) return@thenCompose withoutToken()
            val request = buildRequest(token).build()
            requests.track(SkysoftHttp.sendString(request, MAXIMUM_API_RESPONSE_BYTES)).thenApply { response ->
                if (response.statusCode() == HTTP_UNAUTHORIZED) SpotifyAuthentication.invalidateAccessToken(token)
                handleResponse(response)
            }
        }
        return requests.result(operation)
    }

    private fun request(url: String, token: String): HttpRequest.Builder = HttpRequest.newBuilder(URI.create(url))
        .timeout(Duration.ofSeconds(REQUEST_TIMEOUT_SECONDS))
        .header("Authorization", "Bearer $token")
        .header("User-Agent", SkysoftHttp.versionedUserAgent)

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

    private fun spotifyApiException(response: HttpResponse<String>): SpotifyApiException = SpotifyApiException(
        response.statusCode(),
        retryAfterMillis(response),
        spotifyApiFailureReason(response.body()),
    )

    private fun retryAfterMillis(response: HttpResponse<*>): Long? {
        val seconds = response.headers()
            .firstValue("Retry-After")
            .orElse(null)
            ?.toLongOrNull()
            ?.takeIf { it >= 0L }
            ?: return null
        return runCatching { Math.multiplyExact(seconds, MILLIS_PER_SECOND) }.getOrNull()
    }

    private const val PLAYER_ENDPOINT = "https://api.spotify.com/v1/me/player"
    private const val TRACK_TYPE = "track"
    private const val REQUEST_TIMEOUT_SECONDS = 15L
    private const val MAXIMUM_API_RESPONSE_BYTES = 1024L * 1024L
    private const val TARGET_ARTWORK_SIZE = 300
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

internal const val SPOTIFY_QUOTA_EXCEEDED_REASON = "QUOTA_EXCEEDED"
internal const val HTTP_OK = 200
internal const val HTTP_NO_CONTENT = 204
internal const val HTTP_BAD_REQUEST = 400
internal const val HTTP_UNAUTHORIZED = 401
internal const val HTTP_FORBIDDEN = 403
internal const val HTTP_NOT_FOUND = 404
internal const val HTTP_TOO_MANY_REQUESTS = 429
internal val HTTP_SUCCESS = 200..299

private fun spotifyApiFailureReason(responseBody: String): String? = runCatching {
    val response = JsonParser.parseString(responseBody).asJsonObject
    response.get("reason")?.takeUnless { it.isJsonNull }?.asString
        ?: response.getAsJsonObject("error")?.get("reason")?.takeUnless { it.isJsonNull }?.asString
}.getOrNull()

internal class SpotifyApiException(
    val statusCode: Int,
    val retryAfterMillis: Long? = null,
    val reason: String? = null,
) : IllegalStateException("Spotify API returned HTTP $statusCode")

private data class PlaybackDetails(val subtitle: String, val collection: String, val artworkUrl: String?)
private data class ArtworkCandidate(val url: String, val distance: Int)
