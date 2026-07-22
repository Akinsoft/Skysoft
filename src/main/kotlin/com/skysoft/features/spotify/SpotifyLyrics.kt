package com.skysoft.features.spotify

import com.google.gson.JsonParser
import com.skysoft.SkysoftMod
import com.skysoft.utils.net.SkysoftHttp
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpRequest
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.concurrent.CompletableFuture
import kotlin.math.roundToLong

internal object SpotifyLyrics {
    private val timestampPattern = Regex("""^\[(\d{1,3}):(\d{2}(?:\.\d{1,3})?)]\s*(.*)$""")

    fun fetch(playback: SpotifyPlayback): CompletableFuture<List<SyncedLyricLine>> {
        val query = parameters(
            "track_name" to playback.title,
            "artist_name" to playback.subtitle,
            "album_name" to playback.collection,
            "duration" to (playback.durationMillis / MILLIS_PER_SECOND).toString(),
        )
        val request = HttpRequest.newBuilder(URI.create("$LYRICS_ENDPOINT?$query"))
            .timeout(Duration.ofSeconds(REQUEST_TIMEOUT_SECONDS))
            .header("User-Agent", "Skysoft/${SkysoftMod.VERSION} (https://modrinth.com/mod/skysoft)")
            .GET()
            .build()
        return SkysoftHttp.sendString(request).thenApply { response ->
            when (response.statusCode()) {
                HTTP_OK -> {
                    val json = JsonParser.parseString(response.body()).asJsonObject
                    val lyrics = json.get("syncedLyrics")?.takeUnless { it.isJsonNull }?.asString
                    parseSyncedLyrics(lyrics.orEmpty())
                }
                HTTP_NOT_FOUND -> emptyList()
                HTTP_TOO_MANY_REQUESTS -> throw SpotifyLyricsRateLimitException(
                    response.headers().firstValue("Retry-After").orElse("1").toLongOrNull() ?: 1,
                )
                else -> throw SpotifyLyricsException(response.statusCode())
            }
        }
    }

    internal fun parseSyncedLyrics(lyrics: String): List<SyncedLyricLine> = lyrics.lineSequence().mapNotNull { line ->
        val match = timestampPattern.matchEntire(line.trim()) ?: return@mapNotNull null
        val text = match.groupValues[LYRIC_TEXT_GROUP].trim().takeIf(String::isNotEmpty) ?: return@mapNotNull null
        val timestampMillis = match.groupValues[1].toLong() * MILLIS_PER_MINUTE +
            (match.groupValues[2].toDouble() * MILLIS_PER_SECOND).roundToLong()
        SyncedLyricLine(timestampMillis, text)
    }.sortedBy(SyncedLyricLine::timestampMillis).toList()

    internal fun activeLineIndex(lines: List<SyncedLyricLine>, positionMillis: Long): Int {
        val result = lines.binarySearch { line -> line.timestampMillis.compareTo(positionMillis) }
        return if (result >= 0) result else -result - BINARY_SEARCH_INSERTION_OFFSET
    }

    private fun parameters(vararg values: Pair<String, String>): String = values.joinToString("&") { (name, value) ->
        "${encode(name)}=${encode(value)}"
    }

    private fun encode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8)

    private const val LYRICS_ENDPOINT = "https://lrclib.net/api/get"
    private const val REQUEST_TIMEOUT_SECONDS = 25L
    private const val MILLIS_PER_SECOND = 1_000L
    private const val MILLIS_PER_MINUTE = 60_000L
    private const val BINARY_SEARCH_INSERTION_OFFSET = 2
    private const val LYRIC_TEXT_GROUP = 3
    private const val HTTP_OK = 200
    private const val HTTP_NOT_FOUND = 404
    private const val HTTP_TOO_MANY_REQUESTS = 429
}

internal data class SyncedLyricLine(val timestampMillis: Long, val text: String)
internal class SpotifyLyricsRateLimitException(val retryAfterSeconds: Long) : IllegalStateException("LRCLIB rate limit reached")
internal class SpotifyLyricsException(statusCode: Int) : IllegalStateException("LRCLIB returned HTTP $statusCode")
