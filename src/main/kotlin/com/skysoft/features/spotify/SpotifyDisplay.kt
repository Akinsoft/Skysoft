package com.skysoft.features.spotify

import com.skysoft.SkysoftMod
import com.skysoft.config.SkysoftConfigGui
import com.skysoft.config.SpotifyDisplaySettingsConfig
import com.skysoft.config.SpotifyLyricsMode
import com.skysoft.gui.GuiOverlay
import com.skysoft.gui.GuiOverlayContextType
import com.skysoft.gui.GuiOverlayLayer
import com.skysoft.gui.GuiOverlayRegistry
import com.skysoft.gui.RenderableHudEditorElement
import com.skysoft.utils.EasingUtilities
import com.skysoft.utils.MILLIS_PER_SECOND
import com.skysoft.utils.MinecraftClient
import com.skysoft.utils.SkysoftChat
import com.skysoft.utils.SkysoftClientEvents
import com.skysoft.utils.boundedAccessOrderMap
import com.skysoft.utils.image.AsyncImageTextureCache
import com.skysoft.utils.image.ScaledImageDecoder
import com.skysoft.utils.image.mapNativeImageAsync
import com.skysoft.utils.input.InputUtilities
import com.skysoft.utils.net.AsyncRequestSlot
import com.skysoft.utils.net.SkysoftHttp
import com.skysoft.utils.net.unwrapFutureFailure
import com.skysoft.utils.renderables.renderRenderable
import java.net.URI
import java.util.EnumMap
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor

object SpotifyDisplay {
    private var playback: SpotifyPlayback? = null
    private var hiddenAtMillis: Long? = null
    private var shownAtMillis = 0L
    private val pollSchedule = SpotifyPollSchedule()
    private var nextPollAtNanos: Long? = null
    private val pollRequest = AsyncRequestSlot<SpotifyPlayback?>(completionExecutor = Minecraft.getInstance())
    private var nextTextureId = 0
    private val artworks = AsyncImageTextureCache<String>(
        Minecraft.getInstance(),
        maximumSize = 1,
        maximumPending = 1,
        textureDescription = "Skysoft Spotify artwork",
    ) {
        SkysoftMod.id("spotify/artwork_${nextTextureId++}")
    }
    private var lyrics: List<SyncedLyricLine> = emptyList()
    private val lyricsRequest = AsyncRequestSlot<List<SyncedLyricLine>>(
        completionExecutor = Minecraft.getInstance(),
    )
    private var lyricsRetryAtMillis = 0L
    private var activeLyricIndex = -1
    private var previousLyricIndex: Int? = null
    private var lyricChangedAtMillis = 0L
    private val keyStates = EnumMap<SpotifyPlaybackAction, Boolean>(SpotifyPlaybackAction::class.java)
    private val lyricsCache = boundedAccessOrderMap<String, List<SyncedLyricLine>>(LYRICS_CACHE_SIZE)

    fun register() {
        SkysoftClientEvents.onEndTick(
            "Spotify Display tick",
            { config().enabled || playback != null || pollRequest.isPending },
        ) { tick() }
        SkysoftClientEvents.onClientStopping("Spotify Display cleanup") { clear() }
        GuiOverlayRegistry.registerHud(
            GuiOverlay(
                id = "spotify_display",
                layer = GuiOverlayLayer.BELOW_SCREEN,
                contexts = setOf(
                    GuiOverlayContextType.WORLD,
                    GuiOverlayContextType.INVENTORY,
                    GuiOverlayContextType.STORAGE,
                    GuiOverlayContextType.CHAT,
                ),
                visible = { isDisplayVisible() },
                render = { context, _ -> render(context) },
            ),
            object : RenderableHudEditorElement {
                override val id: String = "spotify_display"
                override val label: String = "Spotify Display"
                override val position get() = config().position
                override val hasEditorBackground: Boolean = false
                override fun hudRenderable() = currentRenderable()
                override fun isVisible(): Boolean = isDisplayVisible()
                override fun openConfig() = SkysoftConfigGui.open("Spotify Display")
            },
        )
    }

    fun clear() {
        clearPlayback()
        nextPollAtNanos = null
        pollSchedule.reset()
        pollRequest.cancel()
        keyStates.clear()
    }

    internal fun requestRefresh() {
        nextPollAtNanos = pollSchedule.requestedAt(System.nanoTime())
    }

    private fun tick() {
        if (!config().enabled) {
            if (playback != null || pollRequest.isPending) clear()
            return
        }
        if (Minecraft.getInstance().player == null) {
            clear()
            return
        }
        val nowMillis = System.currentTimeMillis()
        if (hiddenAtMillis?.let { nowMillis - it >= FADE_DURATION_MILLIS } == true) clearPlayback()
        val nowNanos = System.nanoTime()
        if (pollSchedule.isDue(nowNanos, nextPollAtNanos) && !pollRequest.isPending) pollPlayback()
        processPlaybackKeys()
        ensureArtwork()
        ensureLyrics(nowMillis)
        updateActiveLyric(nowMillis)
    }

    private fun pollPlayback() {
        pollRequest.startIfIdle(
            requestFactory = SpotifyWebApi::currentPlayback,
        ) { result, failure ->
            val completedAtNanos = System.nanoTime()
            val apiFailure = failure?.unwrapFutureFailure() as? SpotifyApiException
            val apiLimit = deferPollingForApiLimit(apiFailure, completedAtNanos)
            when {
                apiLimit != null -> SkysoftMod.LOGGER.debug(apiLimit.pollingLogMessage, apiFailure)
                failure != null -> {
                    nextPollAtNanos = pollSchedule.nextAfterFailure(completedAtNanos)
                    if (apiFailure == null) {
                        SkysoftMod.LOGGER.debug("Could not refresh Spotify playback", failure.unwrapFutureFailure())
                    }
                }
                result == null -> {
                    nextPollAtNanos = pollSchedule.nextAfterNoPlayback(completedAtNanos)
                    hidePlayback()
                }
                else -> {
                    val completedAtMillis = System.currentTimeMillis()
                    nextPollAtNanos = pollSchedule.nextAfterPlayback(
                        completedAtNanos,
                        result.durationMillis - result.positionAt(completedAtMillis),
                        result.playing,
                    )
                    applyPlayback(result)
                }
            }
        }
    }

    private fun applyPlayback(updated: SpotifyPlayback) {
        val now = System.currentTimeMillis()
        val changed = playback?.identity != updated.identity
        val wasHidden = hiddenAtMillis != null
        playback = updated
        hiddenAtMillis = null
        if (changed || wasHidden) shownAtMillis = now
        if (!changed) return
        lyrics = lyricsCache[updated.identity].orEmpty()
        activeLyricIndex = -1
        previousLyricIndex = null
        lyricChangedAtMillis = now
        lyricsRetryAtMillis = 0L
        artworks.clear()
        lyricsRequest.cancel()
        ensureArtwork()
        ensureLyrics(now)
    }

    private fun hidePlayback() {
        if (playback != null && hiddenAtMillis == null) hiddenAtMillis = System.currentTimeMillis()
    }

    private fun clearPlayback() {
        playback = null
        hiddenAtMillis = null
        artworks.clear()
        lyricsRequest.cancel()
        lyrics = emptyList()
        activeLyricIndex = -1
        previousLyricIndex = null
    }

    private fun ensureArtwork() {
        val current = playback ?: return
        if (!config().details.albumArtwork) {
            artworks.clear()
            return
        }
        val url = current.artworkUrl ?: return
        val uri = URI.create(url)
        if (uri.scheme != "https" || uri.host != SPOTIFY_ARTWORK_HOST || uri.userInfo != null) return
        artworks.request(url) {
            SkysoftHttp.getBytes(
                url,
                maximumResponseBytes = MAXIMUM_ARTWORK_BYTES.toLong(),
            ).mapNativeImageAsync { bytes ->
                ScaledImageDecoder.decode(bytes, MAXIMUM_ARTWORK_SIZE, MAXIMUM_ARTWORK_SIZE)
            }
        }
    }

    private fun ensureLyrics(now: Long) {
        val current = playback ?: return
        if (config().details.lyricsMode == SpotifyLyricsMode.OFF || !current.supportsLyrics || now < lyricsRetryAtMillis) return
        if (current.identity in lyricsCache) {
            lyrics = lyricsCache.getValue(current.identity)
            return
        }
        if (lyricsRequest.isPending) return
        val identity = current.identity
        lyricsRequest.startIfIdle(
            requestFactory = { SpotifyLyrics.fetch(current) },
        ) { result, failure ->
            val cause = failure?.unwrapFutureFailure()
            when {
                result != null -> {
                    lyricsCache[identity] = result
                    val activePlayback = playback
                    if (activePlayback?.identity == identity) {
                        lyrics = result
                        activeLyricIndex = SpotifyLyrics.activeLineIndex(
                            result,
                            activePlayback.positionAt(System.currentTimeMillis()),
                        )
                        previousLyricIndex = null
                        lyricChangedAtMillis = System.currentTimeMillis()
                    }
                }
                cause is SpotifyLyricsRateLimitException ->
                    lyricsRetryAtMillis = System.currentTimeMillis() + cause.retryAfterSeconds * MILLIS_PER_SECOND
                cause != null -> {
                    lyricsRetryAtMillis = System.currentTimeMillis() + LYRICS_RETRY_DELAY_MILLIS
                    SkysoftMod.LOGGER.debug("Could not load Spotify lyrics", cause)
                }
            }
        }
    }

    private fun updateActiveLyric(now: Long) {
        val current = playback ?: return
        if (lyrics.isEmpty()) return
        val index = SpotifyLyrics.activeLineIndex(lyrics, current.positionAt(now))
        if (index == activeLyricIndex) return
        previousLyricIndex = activeLyricIndex
        activeLyricIndex = index
        lyricChangedAtMillis = now
    }

    private fun processPlaybackKeys() {
        val settings = config().settings
        if (!settings.playbackControls || MinecraftClient.screen() != null) {
            keyStates.clear()
            return
        }
        CONTROL_BINDINGS.forEach { action ->
            val key = keyFor(action, settings)
            val down = InputUtilities.isFeatureKeyDown(key)
            val wasDown = keyStates.put(action, down) == true
            if (down && !wasDown) control(action)
        }
    }

    private fun control(requestedAction: SpotifyPlaybackAction) {
        val action = if (requestedAction == SpotifyPlaybackAction.PLAY && playback?.playing == true) {
            SpotifyPlaybackAction.PAUSE
        } else {
            requestedAction
        }
        SpotifyWebApi.control(action).whenComplete { _, failure ->
            Minecraft.getInstance().execute {
                if (failure == null) {
                    if (action == SpotifyPlaybackAction.PLAY || action == SpotifyPlaybackAction.PAUSE) {
                        playback = playback?.let { current ->
                            current.copy(
                                playing = action == SpotifyPlaybackAction.PLAY,
                                progressMillis = current.positionAt(System.currentTimeMillis()),
                                receivedAtMillis = System.currentTimeMillis(),
                            )
                        }
                    }
                    requestRefresh()
                    return@execute
                }
                val apiFailure = failure.unwrapFutureFailure() as? SpotifyApiException
                val apiLimit = deferPollingForApiLimit(apiFailure, System.nanoTime())
                SkysoftChat.error(
                    apiLimit?.controlMessage ?: when (apiFailure?.statusCode) {
                        HTTP_FORBIDDEN -> "Spotify Premium is required for playback controls."
                        HTTP_NOT_FOUND -> "Open Spotify on a device before using playback controls."
                        HTTP_UNAUTHORIZED -> "Connect Spotify again to use playback controls."
                        else -> "Spotify could not complete that playback control."
                    },
                )
            }
        }
    }

    private fun deferPollingForApiLimit(failure: SpotifyApiException?, nowNanos: Long): SpotifyApiLimit? {
        failure ?: return null
        val limit = when {
            failure.reason == SPOTIFY_QUOTA_EXCEEDED_REASON -> SpotifyApiLimit.QUOTA
            failure.statusCode == HTTP_TOO_MANY_REQUESTS -> SpotifyApiLimit.RATE_LIMIT
            else -> return null
        }
        nextPollAtNanos = when (limit) {
            SpotifyApiLimit.QUOTA -> pollSchedule.nextAfterQuotaLimit(nowNanos, failure.retryAfterMillis)
            SpotifyApiLimit.RATE_LIMIT -> pollSchedule.nextAfterRateLimit(nowNanos, failure.retryAfterMillis)
        }
        return limit
    }

    private fun isDisplayVisible(): Boolean =
        config().enabled && playback != null && displayAlpha(System.currentTimeMillis()) > 0.0 &&
            !MinecraftClient.isGuiHidden(Minecraft.getInstance())

    private fun render(context: GuiGraphicsExtractor) {
        val renderable = currentRenderable() ?: return
        config().position.renderRenderable(context, renderable)
    }

    private fun currentRenderable(): SpotifyHudRenderable? {
        if (!isDisplayVisible()) return null
        val current = playback ?: return null
        val now = System.currentTimeMillis()
        return renderable(current, displayAlpha(now), now)
    }

    private fun renderable(current: SpotifyPlayback, alpha: Double, now: Long) = SpotifyHudRenderable(
        playback = current,
        artwork = current.artworkUrl?.let(artworks::texture)?.texture,
        lyrics = lyrics,
        alpha = alpha,
        lyricTransition = (now - lyricChangedAtMillis).toDouble() / LYRIC_TRANSITION_DURATION_MILLIS,
        activeLyricIndex = activeLyricIndex,
        previousLyricIndex = previousLyricIndex,
        showArtwork = config().details.albumArtwork,
        lyricsMode = config().details.lyricsMode,
        lyricLineCount = config().details.lyricLineCount,
        roundedCorners = config().details.roundedCorners,
        nowMillis = now,
    )

    private fun displayAlpha(now: Long): Double {
        val hiddenAt = hiddenAtMillis
        val alpha = if (hiddenAt != null) {
            1.0 - EasingUtilities.smoothStep((now - hiddenAt).toDouble() / FADE_DURATION_MILLIS)
        } else {
            EasingUtilities.easeOutCubic((now - shownAtMillis).toDouble() / FADE_DURATION_MILLIS)
        }
        val settings = config().settings
        if (!settings.autoHide) return alpha
        val autoHideAt = shownAtMillis + settings.autoHideDelaySeconds * MILLIS_PER_SECOND
        return minOf(
            alpha,
            1.0 - EasingUtilities.smoothStep((now - autoHideAt).toDouble() / FADE_DURATION_MILLIS),
        )
    }

    private fun config() = SkysoftConfigGui.config().gui.information.spotifyDisplay

    private const val FADE_DURATION_MILLIS = 400L
    private const val LYRIC_TRANSITION_DURATION_MILLIS = 260L
    private const val LYRICS_RETRY_DELAY_MILLIS = 30_000L
    private const val MAXIMUM_ARTWORK_BYTES = 4 * 1024 * 1024
    private const val MAXIMUM_ARTWORK_SIZE = 300
    private const val SPOTIFY_ARTWORK_HOST = "i.scdn.co"
    private const val LYRICS_CACHE_SIZE = 20
    private val CONTROL_BINDINGS = listOf(
        SpotifyPlaybackAction.PLAY,
        SpotifyPlaybackAction.PREVIOUS,
        SpotifyPlaybackAction.NEXT,
    )
}

private fun keyFor(
    action: SpotifyPlaybackAction,
    settings: SpotifyDisplaySettingsConfig,
): Int = when (action) {
    SpotifyPlaybackAction.PLAY, SpotifyPlaybackAction.PAUSE -> settings.playPauseKey
    SpotifyPlaybackAction.PREVIOUS -> settings.previousKey
    SpotifyPlaybackAction.NEXT -> settings.nextKey
}

private enum class SpotifyApiLimit(val pollingLogMessage: String, val controlMessage: String) {
    QUOTA(
        "Spotify playback polling reached the API quota",
        "Spotify's API quota was reached. Spotify Display will retry later.",
    ),
    RATE_LIMIT(
        "Spotify playback polling was rate limited",
        "Spotify is temporarily rate limited. Try again shortly.",
    ),
}
