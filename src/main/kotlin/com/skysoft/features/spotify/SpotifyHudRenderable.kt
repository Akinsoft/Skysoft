package com.skysoft.features.spotify

import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.textures.FilterMode
import com.skysoft.utils.ColorUtilities.withScaledAlpha
import com.skysoft.utils.EasingUtilities
import com.skysoft.utils.gui.OverlayPanelStyle
import com.skysoft.utils.gui.PixelControlColors
import com.skysoft.utils.gui.fillOverlayBackground
import com.skysoft.utils.render.LegacyTextRenderer
import com.skysoft.utils.renderables.GuiRenderable
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.renderer.texture.DynamicTexture

internal class SpotifyHudRenderable(
    private val playback: SpotifyPlayback,
    private val artwork: DynamicTexture?,
    private val lyrics: List<SyncedLyricLine>,
    private val alpha: Double,
    private val lyricTransition: Double,
    private val activeLyricIndex: Int,
    private val previousLyricIndex: Int?,
    private val showArtwork: Boolean,
    private val showLyrics: Boolean,
    private val roundedCorners: Boolean,
    private val nowMillis: Long,
) : GuiRenderable {
    override val width: Int = DISPLAY_WIDTH
    override val height: Int = PLAYER_HEIGHT + if (hasLyrics()) LYRICS_GAP + LYRICS_HEIGHT else 0

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
        context.text(font, truncate(playback.title, titleWidth), x, TITLE_Y, TEXT_COLOR.withScaledAlpha(alpha), true)
        context.text(font, truncate(playback.subtitle, contentRight - x), x, ARTIST_Y, MUTED_COLOR.withScaledAlpha(alpha), false)
        context.text(font, truncate(playback.collection, contentRight - x), x, COLLECTION_Y, DIM_COLOR.withScaledAlpha(alpha), false)
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
        drawPanel(context, y, LYRICS_HEIGHT)
        val currentIndex = activeLyricIndex.takeIf { it in lyrics.indices } ?: PRELUDE_LYRIC_INDEX
        val previousIndex = previousLyricIndex?.takeIf {
            (it == PRELUDE_LYRIC_INDEX || it in lyrics.indices) && it != currentIndex
        }
        val transition = lyricTransition.coerceIn(0.0, 1.0)
        if (previousIndex == null) {
            val blockAlpha = if (previousLyricIndex == null) EasingUtilities.smoothStep(transition) else 1.0
            drawLyricLayout(context, y, currentIndex, blockAlpha)
        } else if (transition < TRANSITION_MIDPOINT) {
            val fadeOut = EasingUtilities.smoothStep(transition / TRANSITION_MIDPOINT)
            drawLyricLayout(context, y, previousIndex, 1.0 - fadeOut)
        } else {
            val fadeIn = EasingUtilities.smoothStep(
                (transition - TRANSITION_MIDPOINT) / TRANSITION_MIDPOINT,
            )
            drawLyricLayout(context, y, currentIndex, fadeIn)
        }
    }

    private fun drawLyricLayout(
        context: GuiGraphicsExtractor,
        panelY: Int,
        activeIndex: Int,
        blockAlpha: Double,
    ) {
        val rows = if (activeIndex == PRELUDE_LYRIC_INDEX) {
            lyrics.take(LYRICS_ROW_COUNT).mapIndexed { index, line ->
                DisplayedLyricRow(index, line.text, active = false)
            }
        } else {
            val font = Minecraft.getInstance().font
            val activeLines = LegacyTextRenderer.wrap(
                font,
                lyrics[activeIndex].text,
                DISPLAY_WIDTH - LYRICS_PADDING * 2,
                continuationPrefix = "",
            ).take(LYRICS_ROW_COUNT)
            lyricRows(
                previous = lyrics.getOrNull(activeIndex - 1)?.text,
                active = activeLines,
                next = lyrics.getOrNull(activeIndex + 1)?.text,
                maximumRows = LYRICS_ROW_COUNT,
            )
        }
        rows.forEach { row -> drawLyricRow(context, row, panelY, blockAlpha) }
    }

    private fun drawLyricRow(
        context: GuiGraphicsExtractor,
        row: DisplayedLyricRow,
        panelY: Int,
        blockAlpha: Double,
    ) {
        val color = if (row.active) PixelControlColors.ACCENT else MUTED_COLOR
        val emphasisAlpha = if (row.active) 1.0 else ADJACENT_LYRIC_ALPHA
        context.text(
            Minecraft.getInstance().font,
            truncate(row.text, DISPLAY_WIDTH - LYRICS_PADDING * 2),
            LYRICS_PADDING,
            panelY + LYRICS_PADDING + row.index * LYRICS_LINE_HEIGHT,
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

    private fun hasLyrics(): Boolean = showLyrics && lyrics.isNotEmpty()

    private fun truncate(text: String, maximumWidth: Int): String {
        val font = Minecraft.getInstance().font
        if (font.width(text) <= maximumWidth) return text
        val availableWidth = (maximumWidth - font.width(ELLIPSIS)).coerceAtLeast(0)
        return font.plainSubstrByWidth(text, availableWidth) + ELLIPSIS
    }

    private fun formatTime(milliseconds: Long): String {
        val totalSeconds = milliseconds / MILLIS_PER_SECOND
        val hours = totalSeconds / SECONDS_PER_HOUR
        val minutes = totalSeconds / SECONDS_PER_MINUTE % MINUTES_PER_HOUR
        val seconds = totalSeconds % SECONDS_PER_MINUTE
        return if (hours > 0) "$hours:${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}" else {
            "$minutes:${seconds.toString().padStart(2, '0')}"
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
        const val LYRICS_HEIGHT = 36
        const val LYRICS_PADDING = 4
        const val LYRICS_LINE_HEIGHT = 9
        const val LYRICS_ROW_COUNT = 3
        const val TRANSITION_MIDPOINT = 0.5
        const val PRELUDE_LYRIC_INDEX = -1
        const val MILLIS_PER_SECOND = 1_000L
        const val SECONDS_PER_MINUTE = 60L
        const val SECONDS_PER_HOUR = 3_600L
        const val MINUTES_PER_HOUR = 60L
        const val ADJACENT_LYRIC_ALPHA = 0.5
        const val ARTWORK_ALPHA_THRESHOLD = 0.95
        const val ELLIPSIS = "…"
        const val TEXT_COLOR = 0xFFFFFFFF.toInt()
        const val MUTED_COLOR = 0xFFABB5BF.toInt()
        const val DIM_COLOR = 0xFF737D87.toInt()
        const val TRACK_COLOR = 0xFF30363B.toInt()
        const val ARTWORK_BACKGROUND = 0xFF20262C.toInt()
    }
}

internal fun lyricRows(
    previous: String?,
    active: List<String>,
    next: String?,
    maximumRows: Int,
): List<DisplayedLyricRow> {
    if (active.size == 1) {
        return listOfNotNull(
            previous?.let { DisplayedLyricRow(0, it, active = false) },
            DisplayedLyricRow(1, active.single(), active = true),
            next?.let { DisplayedLyricRow(2, it, active = false) },
        )
    }
    return buildList {
        active.take(maximumRows).forEachIndexed { index, line ->
            add(DisplayedLyricRow(index, line, active = true))
        }
        if (active.size < maximumRows && next != null) {
            add(DisplayedLyricRow(active.size, next, active = false))
        }
    }
}

internal data class DisplayedLyricRow(val index: Int, val text: String, val active: Boolean)
