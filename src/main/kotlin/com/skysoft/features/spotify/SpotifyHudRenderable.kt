package com.skysoft.features.spotify

import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.textures.FilterMode
import com.skysoft.config.SpotifyLyricsMode
import com.skysoft.utils.ColorUtilities.withScaledAlpha
import com.skysoft.utils.DurationParts
import com.skysoft.utils.EasingUtilities
import com.skysoft.utils.gui.OverlayPanelStyle
import com.skysoft.utils.gui.PixelControlColors
import com.skysoft.utils.gui.elide
import com.skysoft.utils.gui.fillOverlayBackground
import com.skysoft.utils.render.LegacyTextRenderer
import com.skysoft.utils.renderables.GuiRenderable
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.renderer.texture.DynamicTexture
import kotlin.math.roundToInt

internal class SpotifyHudRenderable(
    private val playback: SpotifyPlayback,
    private val artwork: DynamicTexture?,
    private val lyrics: List<SyncedLyricLine>,
    private val alpha: Double,
    private val lyricTransition: Double,
    private val activeLyricIndex: Int,
    private val previousLyricIndex: Int?,
    private val showArtwork: Boolean,
    private val lyricsMode: SpotifyLyricsMode,
    private val lyricLineCount: Int,
    private val roundedCorners: Boolean,
    private val nowMillis: Long,
) : GuiRenderable {
    private val lyricsHeight = LYRICS_PADDING * 2 + lyricLineCount * LYRICS_LINE_HEIGHT + PANEL_BORDER

    override val width: Int = DISPLAY_WIDTH
    override val height: Int = PLAYER_HEIGHT + if (hasLyrics()) LYRICS_GAP + lyricsHeight else 0

    override fun render(context: GuiGraphicsExtractor) {
        drawPanel(context, 0, PLAYER_HEIGHT)
        val artworkSize = if (showArtwork) ARTWORK_SIZE else 0
        if (showArtwork) drawArtwork(context)
        drawTrackDetails(context, PLAYER_PADDING + artworkSize + if (showArtwork) CONTENT_GAP else 0)
        if (hasLyrics()) drawLyrics(context, PLAYER_HEIGHT + LYRICS_GAP)
    }

    private fun drawArtwork(context: GuiGraphicsExtractor) {
        context.fill(
            PLAYER_PADDING,
            PLAYER_PADDING,
            PLAYER_PADDING + ARTWORK_SIZE,
            PLAYER_PADDING + ARTWORK_SIZE,
            ARTWORK_BACKGROUND.withScaledAlpha(alpha),
        )
        if (artwork == null || alpha < ARTWORK_ALPHA_THRESHOLD) return
        context.blit(
            artwork.textureView,
            RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR),
            PLAYER_PADDING,
            PLAYER_PADDING,
            PLAYER_PADDING + ARTWORK_SIZE,
            PLAYER_PADDING + ARTWORK_SIZE,
            0f,
            1f,
            0f,
            1f,
        )
    }

    private fun drawTrackDetails(context: GuiGraphicsExtractor, x: Int) {
        val font = Minecraft.getInstance().font
        val contentRight = DISPLAY_WIDTH - PLAYER_PADDING
        val titleWidth = contentRight - x - STATUS_WIDTH
        context.text(font, font.elide(playback.title, titleWidth), x, TITLE_Y, TEXT_COLOR.withScaledAlpha(alpha), true)
        context.text(font, font.elide(playback.subtitle, contentRight - x), x, ARTIST_Y, MUTED_COLOR.withScaledAlpha(alpha), false)
        context.text(font, font.elide(playback.collection, contentRight - x), x, COLLECTION_Y, DIM_COLOR.withScaledAlpha(alpha), false)
        drawPlaybackState(context, contentRight - STATUS_WIDTH + STATUS_INSET, TITLE_Y + 1)

        val progress = playback.positionAt(nowMillis).toDouble() / playback.durationMillis
        context.fill(x, PROGRESS_Y, contentRight, PROGRESS_Y + PROGRESS_HEIGHT, TRACK_COLOR.withScaledAlpha(alpha))
        context.fill(
            x,
            PROGRESS_Y,
            x + ((contentRight - x) * progress.coerceIn(0.0, 1.0)).toInt(),
            PROGRESS_Y + PROGRESS_HEIGHT,
            PixelControlColors.ACCENT.withScaledAlpha(alpha),
        )
        val elapsed = formatTime(playback.positionAt(nowMillis))
        val duration = formatTime(playback.durationMillis)
        context.text(font, elapsed, x, TIME_Y, DIM_COLOR.withScaledAlpha(alpha), false)
        context.text(
            font,
            duration,
            contentRight - font.width(duration),
            TIME_Y,
            DIM_COLOR.withScaledAlpha(alpha),
            false,
        )
    }

    private fun drawPlaybackState(context: GuiGraphicsExtractor, x: Int, y: Int) {
        val color = PixelControlColors.ACCENT.withScaledAlpha(alpha)
        if (playback.playing) {
            context.fill(x, y, x + PAUSE_BAR_WIDTH, y + STATUS_HEIGHT, color)
            context.fill(x + PAUSE_BAR_GAP, y, x + PAUSE_BAR_GAP + PAUSE_BAR_WIDTH, y + STATUS_HEIGHT, color)
            return
        }
        repeat(PLAY_TRIANGLE_WIDTH) { column ->
            val inset = kotlin.math.abs(PLAY_TRIANGLE_CENTER - column)
            context.fill(x + column, y + inset, x + column + 1, y + STATUS_HEIGHT - inset, color)
        }
    }

    private fun drawLyrics(context: GuiGraphicsExtractor, y: Int) {
        drawPanel(context, y, lyricsHeight)
        val currentIndex = activeLyricIndex.takeIf { it in lyrics.indices } ?: PRELUDE_LYRIC_INDEX
        val previousIndex = previousLyricIndex?.takeIf {
            (it == PRELUDE_LYRIC_INDEX || it in lyrics.indices) && it != currentIndex
        }
        val transition = lyricTransition.coerceIn(0.0, 1.0)
        when (lyricsMode) {
            SpotifyLyricsMode.OFF -> Unit
            SpotifyLyricsMode.FADE -> drawFadingLyrics(context, y, currentIndex, previousIndex, transition)
            SpotifyLyricsMode.SCROLL -> drawScrollingLyrics(context, y, currentIndex, previousIndex, transition)
        }
    }

    private fun drawFadingLyrics(
        context: GuiGraphicsExtractor,
        panelY: Int,
        currentIndex: Int,
        previousIndex: Int?,
        transition: Double,
    ) {
        if (previousIndex == null) {
            val blockAlpha = if (previousLyricIndex == null) EasingUtilities.smoothStep(transition) else 1.0
            drawLyricLayout(context, panelY, currentIndex, blockAlpha)
        } else if (transition < TRANSITION_MIDPOINT) {
            val fadeOut = EasingUtilities.smoothStep(transition / TRANSITION_MIDPOINT)
            drawLyricLayout(context, panelY, previousIndex, 1.0 - fadeOut)
        } else {
            val fadeIn = EasingUtilities.smoothStep(
                (transition - TRANSITION_MIDPOINT) / TRANSITION_MIDPOINT,
            )
            drawLyricLayout(context, panelY, currentIndex, fadeIn)
        }
    }

    private fun drawScrollingLyrics(
        context: GuiGraphicsExtractor,
        panelY: Int,
        currentIndex: Int,
        previousIndex: Int?,
        transition: Double,
    ) {
        if (previousIndex == null) {
            drawLyricLayout(context, panelY, currentIndex, 1.0)
            return
        }
        val previousRows = lyricLayout(previousIndex).associateBy { it.lyricIndex to it.segmentIndex }
        val currentRows = lyricLayout(currentIndex).associateBy { it.lyricIndex to it.segmentIndex }
        val movesUp = currentIndex > previousIndex
        val progress = EasingUtilities.smoothStep(transition)
        context.enableScissor(
            PANEL_BORDER,
            panelY + PANEL_BORDER,
            DISPLAY_WIDTH - PANEL_BORDER,
            panelY + lyricsHeight - PANEL_BORDER,
        )
        try {
            (previousRows.keys + currentRows.keys).forEach { key ->
                val previous = previousRows[key]
                val current = currentRows[key]
                val start = previous?.index?.toDouble() ?: if (movesUp) lyricLineCount.toDouble() else -1.0
                val end = current?.index?.toDouble() ?: if (movesUp) -1.0 else lyricLineCount.toDouble()
                val row = when {
                    previous == null -> current
                    current == null -> previous
                    transition < TRANSITION_MIDPOINT -> previous
                    else -> current
                } ?: return@forEach
                drawLyricRow(
                    context,
                    row,
                    panelY,
                    blockAlpha = 1.0,
                    rowPosition = start + (end - start) * progress,
                )
            }
        } finally {
            context.disableScissor()
        }
    }

    private fun lyricLayout(activeIndex: Int): List<DisplayedLyricRow> {
        val activeLines = if (activeIndex in lyrics.indices) {
            LegacyTextRenderer.wrap(
                Minecraft.getInstance().font,
                lyrics[activeIndex].text,
                DISPLAY_WIDTH - LYRICS_PADDING * 2,
                continuationPrefix = "",
            ).take(lyricLineCount)
        } else {
            emptyList()
        }
        return lyricRows(
            lyrics = lyrics.map(SyncedLyricLine::text),
            activeIndex = activeIndex,
            activeLines = activeLines,
            maximumRows = lyricLineCount,
        )
    }

    private fun drawLyricLayout(
        context: GuiGraphicsExtractor,
        panelY: Int,
        activeIndex: Int,
        blockAlpha: Double,
    ) {
        lyricLayout(activeIndex).forEach { row -> drawLyricRow(context, row, panelY, blockAlpha) }
    }

    private fun drawLyricRow(
        context: GuiGraphicsExtractor,
        row: DisplayedLyricRow,
        panelY: Int,
        blockAlpha: Double,
        rowPosition: Double = row.index.toDouble(),
    ) {
        val color = if (row.active) PixelControlColors.ACCENT else MUTED_COLOR
        val emphasisAlpha = if (row.active) 1.0 else ADJACENT_LYRIC_ALPHA
        val font = Minecraft.getInstance().font
        context.text(
            font,
            font.elide(row.text, DISPLAY_WIDTH - LYRICS_PADDING * 2),
            LYRICS_PADDING,
            panelY + LYRICS_PADDING + (rowPosition * LYRICS_LINE_HEIGHT).roundToInt(),
            color.withScaledAlpha(alpha * blockAlpha * emphasisAlpha),
            row.active,
        )
    }

    private fun drawPanel(context: GuiGraphicsExtractor, y: Int, panelHeight: Int) {
        context.fillOverlayBackground(
            0,
            y,
            DISPLAY_WIDTH,
            y + panelHeight,
            OverlayPanelStyle.OUTLINE.withScaledAlpha(alpha),
            roundedCorners,
        )
        context.fillOverlayBackground(
            PANEL_BORDER,
            y + PANEL_BORDER,
            DISPLAY_WIDTH - PANEL_BORDER,
            y + panelHeight - PANEL_BORDER,
            OverlayPanelStyle.BACKGROUND.withScaledAlpha(alpha),
            roundedCorners,
        )
    }

    private fun hasLyrics(): Boolean = lyricsMode != SpotifyLyricsMode.OFF && lyrics.isNotEmpty()

    private fun formatTime(milliseconds: Long): String {
        val duration = DurationParts.fromMilliseconds(milliseconds)
        return if (duration.totalHours > 0L) {
            "${duration.totalHours}:${duration.minutes.toString().padStart(2, '0')}:" +
                duration.seconds.toString().padStart(2, '0')
        } else {
            "${duration.minutes}:${duration.seconds.toString().padStart(2, '0')}"
        }
    }

    private companion object {
        const val DISPLAY_WIDTH = 230
        const val PLAYER_HEIGHT = 60
        const val PLAYER_PADDING = 6
        const val PANEL_BORDER = 1
        const val ARTWORK_SIZE = 48
        const val CONTENT_GAP = 7
        const val TITLE_Y = 7
        const val ARTIST_Y = 18
        const val COLLECTION_Y = 29
        const val PROGRESS_Y = 42
        const val PROGRESS_HEIGHT = 2
        const val TIME_Y = 47
        const val STATUS_WIDTH = 10
        const val STATUS_INSET = 3
        const val STATUS_HEIGHT = 7
        const val PAUSE_BAR_WIDTH = 2
        const val PAUSE_BAR_GAP = 4
        const val PLAY_TRIANGLE_WIDTH = 4
        const val PLAY_TRIANGLE_CENTER = 1
        const val LYRICS_GAP = 3
        const val LYRICS_PADDING = 4
        const val LYRICS_LINE_HEIGHT = 9
        const val TRANSITION_MIDPOINT = 0.5
        const val PRELUDE_LYRIC_INDEX = -1
        const val ADJACENT_LYRIC_ALPHA = 0.5
        const val ARTWORK_ALPHA_THRESHOLD = 0.95
        const val TEXT_COLOR = 0xFFFFFFFF.toInt()
        const val MUTED_COLOR = 0xFFABB5BF.toInt()
        const val DIM_COLOR = 0xFF737D87.toInt()
        const val TRACK_COLOR = 0xFF30363B.toInt()
        const val ARTWORK_BACKGROUND = 0xFF20262C.toInt()
    }
}

internal fun lyricRows(
    lyrics: List<String>,
    activeIndex: Int,
    activeLines: List<String>,
    maximumRows: Int,
): List<DisplayedLyricRow> {
    require(maximumRows > 0)
    if (activeIndex !in lyrics.indices) {
        val visibleLyrics = lyrics.take(maximumRows)
        val offset = (maximumRows - visibleLyrics.size) / 2
        return visibleLyrics.mapIndexed { index, text ->
            DisplayedLyricRow(offset + index, text, active = false, lyricIndex = index)
        }
    }
    require(activeLines.isNotEmpty())
    val visibleActiveLines = activeLines.take(maximumRows)
    val surroundingRows = maximumRows - visibleActiveLines.size
    var previousCount = minOf(activeIndex, surroundingRows / 2)
    val nextCount = minOf(lyrics.lastIndex - activeIndex, surroundingRows - previousCount)
    previousCount = minOf(activeIndex, surroundingRows - nextCount)
    val visibleRowCount = previousCount + visibleActiveLines.size + nextCount
    var rowIndex = (maximumRows - visibleRowCount) / 2
    return buildList {
        for (lyricIndex in activeIndex - previousCount until activeIndex) {
            add(DisplayedLyricRow(rowIndex++, lyrics[lyricIndex], active = false, lyricIndex = lyricIndex))
        }
        visibleActiveLines.forEachIndexed { segmentIndex, text ->
            add(
                DisplayedLyricRow(
                    rowIndex++,
                    text,
                    active = true,
                    lyricIndex = activeIndex,
                    segmentIndex = segmentIndex,
                ),
            )
        }
        repeat(nextCount) { offset ->
            val lyricIndex = activeIndex + offset + 1
            add(DisplayedLyricRow(rowIndex++, lyrics[lyricIndex], active = false, lyricIndex = lyricIndex))
        }
    }
}

internal data class DisplayedLyricRow(
    val index: Int,
    val text: String,
    val active: Boolean,
    val lyricIndex: Int,
    val segmentIndex: Int = 0,
)
