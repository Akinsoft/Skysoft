package com.skysoft.features.spotify

import com.skysoft.SkysoftMod
import com.skysoft.config.SkysoftConfigGui
import com.skysoft.config.SpotifyLyricsMode
import com.skysoft.gui.GuiOverlay
import com.skysoft.gui.GuiOverlayContextType
import com.skysoft.gui.GuiOverlayLayer
import com.skysoft.gui.GuiOverlayRegistry
import com.skysoft.gui.HudEditorElement
import com.skysoft.utils.EasingUtilities
import com.skysoft.utils.MinecraftClient
import com.skysoft.utils.SkysoftChat
import com.skysoft.utils.SkysoftClientEvents
import com.skysoft.utils.boundedAccessOrderMap
import com.skysoft.utils.image.AsyncImageTextureCache
import com.skysoft.utils.image.RegisteredImageTexture
import com.skysoft.utils.image.ScaledImageDecoder
import com.skysoft.utils.input.InputUtilities
import com.skysoft.utils.net.AsyncRequestSlot
import com.skysoft.utils.net.SkysoftHttp
import com.skysoft.utils.renderables.renderRenderable
import java.net.URI
import java.util.EnumMap
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.util.Util
import org.lwjgl.glfw.GLFW

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
    ) { _, image ->
        val id = SkysoftMod.id("spotify/artwork_${nextTextureId++}")
        RegisteredImageTexture.register(id, "Skysoft Spotify artwork", image)
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
                visible = {
                    playback != null && displayAlpha(System.currentTimeMillis()) > 0.0 &&
                        !MinecraftClient.isGuiHidden(Minecraft.getInstance())
                },
                render = { context, _ -> render(context) },
            ),
            object : HudEditorElement {
                override val id: String = "spotify_display"
                override val label: String = "Spotify Display"
                override val position get() = config().position
                override val hasEditorBackground: Boolean = false
                override fun width(): Int = editorRenderable().width
                override fun height(): Int = editorRenderable().height
                override fun isVisible(): Boolean = config().enabled
                override fun renderEditor(context: GuiGraphicsExtractor) = editorRenderable().render(context)
                override fun openConfig() = SkysoftConfigGui.open("Spotify Display")
            },
        )
    }

    fun clear() {
        playback = null
        hiddenAtMillis = null
        nextPollAtNanos = null
        pollSchedule.reset()
        pollRequest.cancel()
        artworks.clear()
        lyricsRequest.cancel()
        lyrics = emptyList()
        activeLyricIndex = -1
        previousLyricIndex = null
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
            val apiFailure = failure?.unwrap() as? SpotifyApiException
            when {
                apiFailure?.reason == SPOTIFY_QUOTA_EXCEEDED_REASON -> {
                    nextPollAtNanos = pollSchedule.nextAfterQuotaLimit(
                        completedAtNanos,
                        apiFailure.retryAfterMillis,
                    )
                    SkysoftMod.LOGGER.debug("Spotify playback polling reached the API quota", apiFailure)
                }
                apiFailure?.statusCode == HTTP_TOO_MANY_REQUESTS -> {
                    nextPollAtNanos = pollSchedule.nextAfterRateLimit(
                        completedAtNanos,
                        apiFailure.retryAfterMillis,
                    )
                    SkysoftMod.LOGGER.debug("Spotify playback polling was rate limited", apiFailure)
                }
                apiFailure != null -> {
                    nextPollAtNanos = pollSchedule.nextAfterFailure(completedAtNanos)
                }
                failure != null -> {
                    nextPollAtNanos = pollSchedule.nextAfterFailure(completedAtNanos)
                    SkysoftMod.LOGGER.debug("Could not refresh Spotify playback", failure.unwrap())
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
            ).thenApplyAsync(
                { bytes -> ScaledImageDecoder.decode(bytes, MAXIMUM_ARTWORK_SIZE, MAXIMUM_ARTWORK_SIZE) },
                Util.ioPool(),
            )
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
                failure?.unwrap() is SpotifyLyricsRateLimitException -> {
                    val rateLimit = failure.unwrap() as SpotifyLyricsRateLimitException
                    lyricsRetryAtMillis = System.currentTimeMillis() + rateLimit.retryAfterSeconds * MILLIS_PER_SECOND
                }
                failure != null -> {
                    lyricsRetryAtMillis = System.currentTimeMillis() + LYRICS_RETRY_DELAY_MILLIS
                    SkysoftMod.LOGGER.debug("Could not load Spotify lyrics", failure.unwrap())
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
            val down = key != GLFW.GLFW_KEY_UNKNOWN && key != GLFW.GLFW_KEY_ENTER && InputUtilities.isActionBindingDown(key)
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
                        playback = playback?.copy(
                            playing = action == SpotifyPlaybackAction.PLAY,
                            progressMillis = playback?.positionAt(System.currentTimeMillis()) ?: 0,
                            receivedAtMillis = System.currentTimeMillis(),
                        )
                    }
                    requestRefresh()
                    return@execute
                }
                val apiFailure = failure.unwrap() as? SpotifyApiException
                when {
                    apiFailure?.reason == SPOTIFY_QUOTA_EXCEEDED_REASON -> {
                        nextPollAtNanos = pollSchedule.nextAfterQuotaLimit(
                            System.nanoTime(),
                            apiFailure.retryAfterMillis,
                        )
                        SkysoftChat.error("Spotify's API quota was reached. Spotify Display will retry later.")
                    }
                    apiFailure?.statusCode == HTTP_TOO_MANY_REQUESTS -> {
                        nextPollAtNanos = pollSchedule.nextAfterRateLimit(
                            System.nanoTime(),
                            apiFailure.retryAfterMillis,
                        )
                        SkysoftChat.error("Spotify is temporarily rate limited. Try again shortly.")
                    }
                    apiFailure?.statusCode == HTTP_FORBIDDEN ->
                        SkysoftChat.error("Spotify Premium is required for playback controls.")
                    apiFailure?.statusCode == HTTP_NOT_FOUND ->
                        SkysoftChat.error("Open Spotify on a device before using playback controls.")
                    apiFailure?.statusCode == HTTP_UNAUTHORIZED ->
                        SkysoftChat.error("Connect Spotify again to use playback controls.")
                    else -> SkysoftChat.error("Spotify could not complete that playback control.")
                }
            }
        }
    }

    private fun render(context: GuiGraphicsExtractor) {
        val current = playback ?: return
        val now = System.currentTimeMillis()
        val alpha = displayAlpha(now)
        if (alpha <= 0.0) return
        config().position.renderRenderable(context, renderable(current, alpha, now))
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

    private fun editorRenderable(): SpotifyHudRenderable {
        val now = System.currentTimeMillis()
        playback?.takeIf { hiddenAtMillis == null }?.let { current ->
            return renderable(current, 1.0, now)
        }
        return SpotifyHudRenderable(
            playback = SpotifyPlayback(
                identity = "skysoft-preview",
                title = "Skysoft Radio",
                subtitle = "Akinsoft",
                collection = "Now Playing",
                artworkUrl = null,
                durationMillis = PREVIEW_DURATION_MILLIS,
                progressMillis = PREVIEW_PROGRESS_MILLIS,
                playing = true,
                receivedAtMillis = now,
                supportsLyrics = true,
            ),
            artwork = null,
            lyrics = listOf(
                SyncedLyricLine(0, "This Display is dedicated to Mashclash"),
                SyncedLyricLine(PREVIEW_PROGRESS_MILLIS, "Lyrics and stuff appear here"),
                SyncedLyricLine(PREVIEW_PROGRESS_MILLIS * 2, "Enjoy it nerds"),
            ),
            alpha = 1.0,
            lyricTransition = 1.0,
            activeLyricIndex = 1,
            previousLyricIndex = 1,
            showArtwork = config().details.albumArtwork,
            lyricsMode = config().details.lyricsMode,
            lyricLineCount = config().details.lyricLineCount,
            roundedCorners = config().details.roundedCorners,
            nowMillis = now,
        )
    }

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

    private fun config() = SkysoftConfigGui.config().gui.spotifyDisplay

    private const val FADE_DURATION_MILLIS = 400L
    private const val LYRIC_TRANSITION_DURATION_MILLIS = 260L
    private const val LYRICS_RETRY_DELAY_MILLIS = 30_000L
    private const val MILLIS_PER_SECOND = 1_000L
    private const val MAXIMUM_ARTWORK_BYTES = 4 * 1024 * 1024
    private const val MAXIMUM_ARTWORK_SIZE = 300
    private const val SPOTIFY_ARTWORK_HOST = "i.scdn.co"
    private const val LYRICS_CACHE_SIZE = 20
    private const val PREVIEW_DURATION_MILLIS = 213_000L
    private const val PREVIEW_PROGRESS_MILLIS = 68_000L
    private const val HTTP_UNAUTHORIZED = 401
    private const val HTTP_FORBIDDEN = 403
    private const val HTTP_TOO_MANY_REQUESTS = 429
    private const val HTTP_NOT_FOUND = 404
    private val CONTROL_BINDINGS = listOf(
        SpotifyPlaybackAction.PLAY,
        SpotifyPlaybackAction.PREVIOUS,
        SpotifyPlaybackAction.NEXT,
    )
}

private fun keyFor(
    action: SpotifyPlaybackAction,
    settings: com.skysoft.config.SpotifyDisplaySettingsConfig,
): Int = when (action) {
    SpotifyPlaybackAction.PLAY, SpotifyPlaybackAction.PAUSE -> settings.playPauseKey
    SpotifyPlaybackAction.PREVIOUS -> settings.previousKey
    SpotifyPlaybackAction.NEXT -> settings.nextKey
}

private fun Throwable.unwrap(): Throwable = (this as? java.util.concurrent.CompletionException)?.cause ?: this
