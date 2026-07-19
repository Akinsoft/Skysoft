package com.skysoft.features.screenshot

import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.textures.FilterMode
import com.skysoft.utils.ColorUtilities.withScaledAlpha
import com.skysoft.utils.gui.OverlayPanelStyle
import com.skysoft.utils.gui.PixelButtonRenderer
import com.skysoft.utils.gui.PixelButtonTone
import com.skysoft.utils.gui.Rect
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.min
import kotlin.math.roundToInt
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor

internal enum class ScreenshotLoadStatus {
    LOADING,
    READY,
    FAILED,
}

internal data class ScreenshotNotice(
    val text: String,
    val isError: Boolean,
    val expiresAtMillis: Long,
)

internal object ScreenshotManagerRenderer {
    fun renderGallery(
        context: GuiGraphicsExtractor,
        font: Font,
        layout: ScreenshotGalleryLayout,
        entries: List<ScreenshotEntry>,
        loadStatus: ScreenshotLoadStatus,
        textures: ScreenshotTextureStore,
        mouseX: Int,
        mouseY: Int,
    ) {
        drawScreenAndPanel(context, layout.panel)
        drawHeader(
            context,
            font,
            layout.panel,
            "Screenshots",
            gallerySubtitle(entries.size, loadStatus),
            null,
            layout.close,
            mouseX,
            mouseY,
        )
        when {
            loadStatus == ScreenshotLoadStatus.LOADING ->
                drawCentered(context, font, layout.content, "Loading screenshots...", MUTED_TEXT)
            loadStatus == ScreenshotLoadStatus.FAILED ->
                drawCentered(context, font, layout.content, "Couldn't open screenshots.", ERROR_TEXT)
            entries.isEmpty() -> drawEmptyGallery(context, font, layout.content)
            else -> drawGalleryTiles(context, font, layout, entries, textures, mouseX, mouseY)
        }
        drawScrollbar(context, layout)
    }

    fun renderFocus(
        context: GuiGraphicsExtractor,
        font: Font,
        layout: ScreenshotFocusLayout,
        entry: ScreenshotEntry,
        textures: ScreenshotTextureStore,
        visuals: ScreenshotFocusVisuals,
        notice: ScreenshotNotice?,
        confirmation: ScreenshotConfirmation?,
        areActionsEnabled: Boolean,
        canNavigatePrevious: Boolean,
        canNavigateNext: Boolean,
        mouseX: Int,
        mouseY: Int,
    ) {
        drawScreenAndPanel(context, layout.panel)
        drawHeader(
            context,
            font,
            layout.panel,
            entry.fileName,
            SCREENSHOT_DATE_FORMAT.format(Instant.ofEpochMilli(entry.modifiedAtMillis)),
            layout.back,
            layout.close,
            mouseX,
            mouseY,
            visuals.chromeAlpha,
        )
        drawAnimatedFocusedScreenshot(context, font, layout, visuals, entry.path, textures)
        drawButton(
            context,
            font,
            layout.previous,
            "<",
            areActionsEnabled && canNavigatePrevious,
            mouseX,
            mouseY,
            alpha = visuals.chromeAlpha,
        )
        drawButton(
            context,
            font,
            layout.next,
            ">",
            areActionsEnabled && canNavigateNext,
            mouseX,
            mouseY,
            alpha = visuals.chromeAlpha,
        )
        drawFocusActions(
            context,
            font,
            layout,
            visuals,
            notice,
            confirmation,
            areActionsEnabled,
            entry.path,
            mouseX,
            mouseY,
        )
    }

    private fun drawFocusActions(
        context: GuiGraphicsExtractor,
        font: Font,
        layout: ScreenshotFocusLayout,
        visuals: ScreenshotFocusVisuals,
        notice: ScreenshotNotice?,
        confirmation: ScreenshotConfirmation?,
        areActionsEnabled: Boolean,
        screenshotPath: java.nio.file.Path,
        mouseX: Int,
        mouseY: Int,
    ) {
        val confirmationNotice = when (confirmation) {
            ScreenshotConfirmation.SHARE -> "Upload publicly for 30 days?"
            ScreenshotConfirmation.DELETE -> "Delete this screenshot?"
            null -> null
        }
        val visibleNotice = confirmationNotice?.let { ScreenshotNotice(it, false, Long.MAX_VALUE) } ?: notice
        visibleNotice?.let {
            drawCenteredText(
                context,
                font,
                layout.panel,
                layout.noticeY,
                it.text,
                (if (it.isError) ERROR_TEXT else MUTED_TEXT).withScaledAlpha(visuals.chromeAlpha),
            )
        }
        if (confirmation != null) {
            drawButton(
                context,
                font,
                layout.cancelDelete,
                "Cancel",
                areActionsEnabled,
                mouseX,
                mouseY,
                alpha = visuals.chromeAlpha,
            )
            drawButton(
                context,
                font,
                layout.confirmDelete,
                if (confirmation == ScreenshotConfirmation.SHARE) "Upload" else "Delete",
                areActionsEnabled,
                mouseX,
                mouseY,
                if (confirmation == ScreenshotConfirmation.SHARE) PixelButtonTone.NORMAL else PixelButtonTone.DANGER,
                visuals.chromeAlpha,
            )
            return
        }
        drawButton(
            context,
            font,
            layout.share,
            ScreenshotSharing.buttonLabel(screenshotPath),
            areActionsEnabled && ScreenshotSharing.status(screenshotPath).state != ScreenshotShareState.UPLOADING,
            mouseX,
            mouseY,
            alpha = visuals.chromeAlpha,
        )
        drawButton(
            context,
            font,
            layout.copy,
            "Copy",
            areActionsEnabled,
            mouseX,
            mouseY,
            alpha = visuals.chromeAlpha,
        )
        drawButton(
            context,
            font,
            layout.saveAs,
            "Save As",
            areActionsEnabled,
            mouseX,
            mouseY,
            alpha = visuals.chromeAlpha,
        )
        drawButton(
            context,
            font,
            layout.delete,
            "Delete",
            areActionsEnabled,
            mouseX,
            mouseY,
            PixelButtonTone.DANGER,
            visuals.chromeAlpha,
        )
    }

    private fun drawAnimatedFocusedScreenshot(
        context: GuiGraphicsExtractor,
        font: Font,
        layout: ScreenshotFocusLayout,
        visuals: ScreenshotFocusVisuals,
        path: java.nio.file.Path,
        textures: ScreenshotTextureStore,
    ) {
        if (!visuals.shouldClipImage) {
            drawFocusedScreenshot(context, font, visuals.imageBounds, path, textures)
            return
        }
        context.enableScissor(
            layout.preview.x,
            layout.preview.y,
            layout.preview.x + layout.preview.width,
            layout.preview.y + layout.preview.height,
        )
        try {
            drawFocusedScreenshot(context, font, visuals.imageBounds, path, textures)
        } finally {
            context.disableScissor()
        }
    }

    private fun drawGalleryTiles(
        context: GuiGraphicsExtractor,
        font: Font,
        layout: ScreenshotGalleryLayout,
        entries: List<ScreenshotEntry>,
        textures: ScreenshotTextureStore,
        mouseX: Int,
        mouseY: Int,
    ) {
        context.enableScissor(
            layout.content.x,
            layout.content.y,
            layout.content.x + layout.content.width,
            layout.content.y + layout.content.height,
        )
        try {
            layout.tiles.forEach { tile ->
                val entry = entries[tile.index]
                val isHovered = layout.content.contains(mouseX, mouseY) && tile.bounds.contains(mouseX, mouseY)
                drawGalleryTile(context, font, tile, entry, textures, isHovered)
            }
        } finally {
            context.disableScissor()
        }
    }

    private fun drawGalleryTile(
        context: GuiGraphicsExtractor,
        font: Font,
        tile: ScreenshotGalleryTile,
        entry: ScreenshotEntry,
        textures: ScreenshotTextureStore,
        isHovered: Boolean,
    ) {
        context.fill(
            tile.bounds.x,
            tile.bounds.y,
            tile.bounds.x + tile.bounds.width,
            tile.bounds.y + tile.bounds.height,
            if (isHovered) TILE_HOVER_BORDER else TILE_BORDER,
        )
        context.fill(
            tile.image.x,
            tile.image.y,
            tile.image.x + tile.image.width,
            tile.image.y + tile.image.height,
            IMAGE_BACKGROUND,
        )
        val texture = textures.thumbnail(entry.path)
        when {
            texture != null -> drawTextureCover(context, texture, tile.image)
            textures.isThumbnailFailed(entry.path) -> drawCentered(context, font, tile.image, "Unavailable", ERROR_TEXT)
        }
        context.fill(
            tile.footer.x,
            tile.footer.y,
            tile.footer.x + tile.footer.width,
            tile.footer.y + tile.footer.height,
            if (isHovered) TILE_HOVER_FOOTER else TILE_FOOTER,
        )
        val label = fitText(font, entry.fileName.substringBeforeLast('.'), tile.footer.width - TILE_TEXT_INSET * 2)
        context.text(
            font,
            label,
            tile.footer.x + TILE_TEXT_INSET,
            tile.footer.y + (tile.footer.height - font.lineHeight) / 2,
            if (isHovered) WHITE_TEXT else PRIMARY_TEXT,
            false,
        )
    }

    private fun drawFocusedScreenshot(
        context: GuiGraphicsExtractor,
        font: Font,
        bounds: Rect,
        path: java.nio.file.Path,
        textures: ScreenshotTextureStore,
    ) {
        context.fill(bounds.x, bounds.y, bounds.x + bounds.width, bounds.y + bounds.height, TILE_BORDER)
        val inner = Rect(bounds.x + 1, bounds.y + 1, bounds.width - 2, bounds.height - 2)
        context.fill(inner.x, inner.y, inner.x + inner.width, inner.y + inner.height, IMAGE_BACKGROUND)
        val texture = textures.preview(path) ?: textures.thumbnail(path)
        when {
            texture != null -> drawTextureContained(context, texture, inner)
            textures.isSelectedPreviewFailed(path) && textures.isThumbnailFailed(path) -> {
                drawCentered(context, font, inner, "Couldn't load screenshot.", ERROR_TEXT)
            }
            else -> drawCentered(context, font, inner, "Loading...", MUTED_TEXT)
        }
    }

    private fun drawScreenAndPanel(context: GuiGraphicsExtractor, panel: Rect) {
        context.fill(0, 0, panel.x * 2 + panel.width + 1, panel.y * 2 + panel.height + 1, SCREEN_OVERLAY)
        OverlayPanelStyle.draw(context, panel.x, panel.y, panel.width, panel.height)
        context.fill(
            panel.x + 1,
            panel.y + 1,
            panel.x + panel.width - 1,
            panel.y + ScreenshotLayoutDimensions.HEADER_HEIGHT,
            HEADER_BACKGROUND,
        )
        context.fill(
            panel.x + 1,
            panel.y + ScreenshotLayoutDimensions.HEADER_HEIGHT - 1,
            panel.x + panel.width - 1,
            panel.y + ScreenshotLayoutDimensions.HEADER_HEIGHT,
            HEADER_LINE,
        )
    }

    private fun drawHeader(
        context: GuiGraphicsExtractor,
        font: Font,
        panel: Rect,
        title: String,
        subtitle: String,
        back: Rect?,
        close: Rect,
        mouseX: Int,
        mouseY: Int,
        alpha: Double = 1.0,
    ) {
        back?.let { drawButton(context, font, it, "<", true, mouseX, mouseY, alpha = alpha) }
        val titleX = back?.let { it.x + it.width + HEADER_GAP } ?: panel.x + HEADER_INSET
        val maximumTextWidth = close.x - HEADER_GAP - titleX
        context.text(
            font,
            fitText(font, title, maximumTextWidth),
            titleX,
            panel.y + TITLE_Y,
            WHITE_TEXT.withScaledAlpha(alpha),
            false,
        )
        context.text(
            font,
            fitText(font, subtitle, maximumTextWidth),
            titleX,
            panel.y + SUBTITLE_Y,
            MUTED_TEXT.withScaledAlpha(alpha),
            false,
        )
        drawButton(context, font, close, "X", true, mouseX, mouseY, PixelButtonTone.DANGER, alpha)
    }

    private fun drawEmptyGallery(context: GuiGraphicsExtractor, font: Font, bounds: Rect) {
        val upperBounds = Rect(bounds.x, bounds.y - EMPTY_TEXT_GAP, bounds.width, bounds.height)
        val lowerBounds = Rect(bounds.x, bounds.y + EMPTY_TEXT_GAP, bounds.width, bounds.height)
        drawCentered(context, font, upperBounds, "No screenshots yet", PRIMARY_TEXT)
        drawCentered(context, font, lowerBounds, "Press F2 to take one.", MUTED_TEXT)
    }

    private fun drawScrollbar(context: GuiGraphicsExtractor, layout: ScreenshotGalleryLayout) {
        val thumb = layout.scrollThumb ?: return
        context.fill(
            layout.scrollTrack.x,
            layout.scrollTrack.y,
            layout.scrollTrack.x + layout.scrollTrack.width,
            layout.scrollTrack.y + layout.scrollTrack.height,
            SCROLL_TRACK,
        )
        context.fill(thumb.x, thumb.y, thumb.x + thumb.width, thumb.y + thumb.height, SCROLL_THUMB)
    }

    private fun drawButton(
        context: GuiGraphicsExtractor,
        font: Font,
        bounds: Rect,
        label: String,
        isEnabled: Boolean,
        mouseX: Int,
        mouseY: Int,
        tone: PixelButtonTone = PixelButtonTone.NORMAL,
        alpha: Double = 1.0,
    ) {
        PixelButtonRenderer.draw(
            context,
            font,
            bounds,
            label,
            false,
            isEnabled && bounds.contains(mouseX, mouseY),
            isEnabled,
            tone,
            alpha,
        )
    }

    private fun drawCentered(context: GuiGraphicsExtractor, font: Font, bounds: Rect, text: String, color: Int) {
        context.text(
            font,
            text,
            bounds.x + (bounds.width - font.width(text)) / 2,
            bounds.y + (bounds.height - font.lineHeight) / 2,
            color,
            false,
        )
    }

    private fun drawCenteredText(context: GuiGraphicsExtractor, font: Font, panel: Rect, y: Int, text: String, color: Int) {
        context.text(font, text, panel.x + (panel.width - font.width(text)) / 2, y, color, false)
    }

    private fun drawTextureCover(context: GuiGraphicsExtractor, texture: ScreenshotTexture, bounds: Rect) {
        val sourceAspect = texture.width.toFloat() / texture.height
        val boundsAspect = bounds.width.toFloat() / bounds.height
        val uInset = if (sourceAspect > boundsAspect) (1f - boundsAspect / sourceAspect) / 2f else 0f
        val vInset = if (sourceAspect < boundsAspect) (1f - sourceAspect / boundsAspect) / 2f else 0f
        context.blit(
            texture.texture.textureView,
            RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR),
            bounds.x,
            bounds.y,
            bounds.x + bounds.width,
            bounds.y + bounds.height,
            uInset,
            1f - uInset,
            vInset,
            1f - vInset,
        )
    }

    private fun drawTextureContained(context: GuiGraphicsExtractor, texture: ScreenshotTexture, bounds: Rect) {
        val scale = min(bounds.width.toDouble() / texture.width, bounds.height.toDouble() / texture.height)
        val width = (texture.width * scale).roundToInt().coerceAtLeast(1)
        val height = (texture.height * scale).roundToInt().coerceAtLeast(1)
        val x = bounds.x + (bounds.width - width) / 2
        val y = bounds.y + (bounds.height - height) / 2
        context.blit(
            texture.texture.textureView,
            RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR),
            x,
            y,
            x + width,
            y + height,
            0f,
            1f,
            0f,
            1f,
        )
    }

    private fun gallerySubtitle(entryCount: Int, loadStatus: ScreenshotLoadStatus): String = when (loadStatus) {
        ScreenshotLoadStatus.LOADING -> "Minecraft screenshots"
        ScreenshotLoadStatus.FAILED -> "Screenshot folder unavailable"
        ScreenshotLoadStatus.READY -> if (entryCount == 1) "1 screenshot" else "$entryCount screenshots"
    }

    private fun fitText(font: Font, text: String, maximumWidth: Int): String {
        if (font.width(text) <= maximumWidth) return text
        val suffix = "..."
        return font.plainSubstrByWidth(text, (maximumWidth - font.width(suffix)).coerceAtLeast(0)) + suffix
    }

    private const val HEADER_INSET = 12
    private const val HEADER_GAP = 6
    private const val TITLE_Y = 8
    private const val SUBTITLE_Y = 21
    private const val TILE_TEXT_INSET = 5
    private const val EMPTY_TEXT_GAP = 9
    private val SCREEN_OVERLAY = 0xD8000000.toInt()
    private val HEADER_BACKGROUND = 0xE0181B1E.toInt()
    private val HEADER_LINE = 0xFF2B91C9.toInt()
    private val TILE_BORDER = 0xFF30373C.toInt()
    private val TILE_HOVER_BORDER = 0xFF58B8EA.toInt()
    private val TILE_FOOTER = 0xF0202529.toInt()
    private val TILE_HOVER_FOOTER = 0xFF2C3941.toInt()
    private val IMAGE_BACKGROUND = 0xFF090B0C.toInt()
    private const val SCROLL_TRACK = 0x60343A3F
    private val SCROLL_THUMB = 0xFF5A6870.toInt()
    private val WHITE_TEXT = 0xFFFFFFFF.toInt()
    private val PRIMARY_TEXT = 0xFFE1E6EA.toInt()
    private val MUTED_TEXT = 0xFF8D99A1.toInt()
    private val ERROR_TEXT = 0xFFFF777D.toInt()
    private val SCREENSHOT_DATE_FORMAT = DateTimeFormatter.ofPattern("MMM d, yyyy 'at' h:mm a", Locale.ENGLISH)
        .withZone(ZoneId.systemDefault())
}
