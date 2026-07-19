package com.skysoft.features.screenshot

import com.skysoft.utils.gui.Rect
import kotlin.math.max
import kotlin.math.min

internal data class ScreenshotGalleryLayout(
    val panel: Rect,
    val close: Rect,
    val content: Rect,
    val tiles: List<ScreenshotGalleryTile>,
    val scrollOffset: Int,
    val maximumScroll: Int,
    val scrollTrack: Rect,
    val scrollThumb: Rect?,
    val rowStep: Int,
) {
    companion object {
        fun create(screenWidth: Int, screenHeight: Int, entryCount: Int, requestedScroll: Int): ScreenshotGalleryLayout {
            val panel = screenshotPanel(screenWidth, screenHeight)
            val close = screenshotCloseButton(panel)
            val content = Rect(
                panel.x + ScreenshotLayoutDimensions.PADDING,
                panel.y + ScreenshotLayoutDimensions.HEADER_HEIGHT,
                panel.width - ScreenshotLayoutDimensions.PADDING * 2,
                panel.height - ScreenshotLayoutDimensions.HEADER_HEIGHT - ScreenshotLayoutDimensions.PADDING,
            )
            val scrollTrack = Rect(
                content.x + content.width - ScreenshotLayoutDimensions.SCROLLBAR_WIDTH,
                content.y,
                ScreenshotLayoutDimensions.SCROLLBAR_WIDTH,
                content.height,
            )
            val gridWidth = content.width - ScreenshotLayoutDimensions.SCROLLBAR_RESERVED_WIDTH
            val tileWidth = (
                gridWidth - ScreenshotLayoutDimensions.TILE_GAP * (ScreenshotLayoutDimensions.COLUMNS - 1)
                ) / ScreenshotLayoutDimensions.COLUMNS
            val imageHeight = max(
                ScreenshotLayoutDimensions.MINIMUM_IMAGE_HEIGHT,
                tileWidth * ScreenshotLayoutDimensions.IMAGE_ASPECT_HEIGHT /
                    ScreenshotLayoutDimensions.IMAGE_ASPECT_WIDTH,
            )
            val tileHeight = imageHeight + ScreenshotLayoutDimensions.TILE_FOOTER_HEIGHT
            val rowStep = tileHeight + ScreenshotLayoutDimensions.TILE_GAP
            val rowCount = (entryCount + ScreenshotLayoutDimensions.COLUMNS - 1) / ScreenshotLayoutDimensions.COLUMNS
            val totalHeight = max(0, rowCount * rowStep - ScreenshotLayoutDimensions.TILE_GAP)
            val maximumScroll = max(0, totalHeight - content.height)
            val scrollOffset = requestedScroll.coerceIn(0, maximumScroll)
            val tiles = (0 until entryCount).mapNotNull { index ->
                val column = index % ScreenshotLayoutDimensions.COLUMNS
                val row = index / ScreenshotLayoutDimensions.COLUMNS
                val bounds = Rect(
                    content.x + column * (tileWidth + ScreenshotLayoutDimensions.TILE_GAP),
                    content.y + row * rowStep - scrollOffset,
                    tileWidth,
                    tileHeight,
                )
                if (!bounds.intersects(content)) return@mapNotNull null
                ScreenshotGalleryTile(
                    index,
                    bounds,
                    Rect(bounds.x + 1, bounds.y + 1, bounds.width - 2, imageHeight - 1),
                    Rect(bounds.x + 1, bounds.y + imageHeight, bounds.width - 2, ScreenshotLayoutDimensions.TILE_FOOTER_HEIGHT - 1),
                )
            }
            return ScreenshotGalleryLayout(
                panel,
                close,
                content,
                tiles,
                scrollOffset,
                maximumScroll,
                scrollTrack,
                scrollThumb(scrollTrack, content.height, totalHeight, scrollOffset, maximumScroll),
                rowStep,
            )
        }
    }
}

internal data class ScreenshotGalleryTile(
    val index: Int,
    val bounds: Rect,
    val image: Rect,
    val footer: Rect,
)

internal data class ScreenshotFocusLayout(
    val panel: Rect,
    val back: Rect,
    val close: Rect,
    val preview: Rect,
    val previous: Rect,
    val next: Rect,
    val noticeY: Int,
    val copy: Rect,
    val saveAs: Rect,
    val delete: Rect,
    val cancelDelete: Rect,
    val confirmDelete: Rect,
) {
    companion object {
        fun create(screenWidth: Int, screenHeight: Int): ScreenshotFocusLayout {
            val panel = screenshotPanel(screenWidth, screenHeight)
            val back = Rect(
                panel.x + ScreenshotLayoutDimensions.PADDING,
                panel.y + ScreenshotLayoutDimensions.HEADER_BUTTON_Y,
                ScreenshotLayoutDimensions.HEADER_BUTTON_SIZE,
                ScreenshotLayoutDimensions.HEADER_BUTTON_SIZE,
            )
            val close = screenshotCloseButton(panel)
            val actionY = panel.y + panel.height - ScreenshotLayoutDimensions.ACTION_BOTTOM -
                ScreenshotLayoutDimensions.ACTION_HEIGHT
            val actionGroupWidth = ScreenshotLayoutDimensions.COPY_WIDTH + ScreenshotLayoutDimensions.SAVE_WIDTH +
                ScreenshotLayoutDimensions.DELETE_WIDTH + ScreenshotLayoutDimensions.ACTION_GAP * 2
            val actionX = panel.x + (panel.width - actionGroupWidth) / 2
            val copy = Rect(actionX, actionY, ScreenshotLayoutDimensions.COPY_WIDTH, ScreenshotLayoutDimensions.ACTION_HEIGHT)
            val saveAs = Rect(
                copy.x + copy.width + ScreenshotLayoutDimensions.ACTION_GAP,
                actionY,
                ScreenshotLayoutDimensions.SAVE_WIDTH,
                ScreenshotLayoutDimensions.ACTION_HEIGHT,
            )
            val delete = Rect(
                saveAs.x + saveAs.width + ScreenshotLayoutDimensions.ACTION_GAP,
                actionY,
                ScreenshotLayoutDimensions.DELETE_WIDTH,
                ScreenshotLayoutDimensions.ACTION_HEIGHT,
            )
            val confirmationWidth = ScreenshotLayoutDimensions.CONFIRMATION_BUTTON_WIDTH * 2 +
                ScreenshotLayoutDimensions.ACTION_GAP
            val confirmationX = panel.x + (panel.width - confirmationWidth) / 2
            val cancelDelete = Rect(
                confirmationX,
                actionY,
                ScreenshotLayoutDimensions.CONFIRMATION_BUTTON_WIDTH,
                ScreenshotLayoutDimensions.ACTION_HEIGHT,
            )
            val confirmDelete = Rect(
                cancelDelete.x + cancelDelete.width + ScreenshotLayoutDimensions.ACTION_GAP,
                actionY,
                ScreenshotLayoutDimensions.CONFIRMATION_BUTTON_WIDTH,
                ScreenshotLayoutDimensions.ACTION_HEIGHT,
            )
            val noticeY = actionY - ScreenshotLayoutDimensions.NOTICE_GAP
            val previewY = panel.y + ScreenshotLayoutDimensions.HEADER_HEIGHT
            val preview = Rect(
                panel.x + ScreenshotLayoutDimensions.PADDING + ScreenshotLayoutDimensions.NAVIGATION_GUTTER,
                previewY,
                panel.width - (ScreenshotLayoutDimensions.PADDING + ScreenshotLayoutDimensions.NAVIGATION_GUTTER) * 2,
                max(1, noticeY - ScreenshotLayoutDimensions.PREVIEW_BOTTOM_GAP - previewY),
            )
            val navigationY = preview.y + (preview.height - ScreenshotLayoutDimensions.NAVIGATION_BUTTON_HEIGHT) / 2
            val previous = Rect(
                panel.x + ScreenshotLayoutDimensions.PADDING,
                navigationY,
                ScreenshotLayoutDimensions.NAVIGATION_BUTTON_WIDTH,
                ScreenshotLayoutDimensions.NAVIGATION_BUTTON_HEIGHT,
            )
            val next = Rect(
                panel.x + panel.width - ScreenshotLayoutDimensions.PADDING -
                    ScreenshotLayoutDimensions.NAVIGATION_BUTTON_WIDTH,
                navigationY,
                ScreenshotLayoutDimensions.NAVIGATION_BUTTON_WIDTH,
                ScreenshotLayoutDimensions.NAVIGATION_BUTTON_HEIGHT,
            )
            return ScreenshotFocusLayout(
                panel,
                back,
                close,
                preview,
                previous,
                next,
                noticeY,
                copy,
                saveAs,
                delete,
                cancelDelete,
                confirmDelete,
            )
        }
    }
}

private fun screenshotPanel(screenWidth: Int, screenHeight: Int): Rect {
    val width = min(ScreenshotLayoutDimensions.MAXIMUM_WIDTH, max(1, screenWidth - ScreenshotLayoutDimensions.SCREEN_INSET))
    val height = min(ScreenshotLayoutDimensions.MAXIMUM_HEIGHT, max(1, screenHeight - ScreenshotLayoutDimensions.SCREEN_INSET))
    return Rect((screenWidth - width) / 2, (screenHeight - height) / 2, width, height)
}

private fun screenshotCloseButton(panel: Rect): Rect = Rect(
    panel.x + panel.width - ScreenshotLayoutDimensions.PADDING - ScreenshotLayoutDimensions.HEADER_BUTTON_SIZE,
    panel.y + ScreenshotLayoutDimensions.HEADER_BUTTON_Y,
    ScreenshotLayoutDimensions.HEADER_BUTTON_SIZE,
    ScreenshotLayoutDimensions.HEADER_BUTTON_SIZE,
)

private fun scrollThumb(
    track: Rect,
    viewportHeight: Int,
    totalHeight: Int,
    scrollOffset: Int,
    maximumScroll: Int,
): Rect? {
    if (maximumScroll <= 0 || totalHeight <= 0) return null
    val height = max(
        ScreenshotLayoutDimensions.MINIMUM_SCROLL_THUMB_HEIGHT,
        track.height * viewportHeight / totalHeight,
    ).coerceAtMost(track.height)
    val travel = track.height - height
    val y = track.y + travel * scrollOffset / maximumScroll
    return Rect(track.x, y, track.width, height)
}

internal object ScreenshotLayoutDimensions {
    const val MAXIMUM_WIDTH = 920
    const val MAXIMUM_HEIGHT = 620
    const val SCREEN_INSET = 24
    const val PADDING = 10
    const val HEADER_HEIGHT = 38
    const val HEADER_BUTTON_Y = 10
    const val HEADER_BUTTON_SIZE = 16
    const val COLUMNS = 3
    const val TILE_GAP = 8
    const val IMAGE_ASPECT_WIDTH = 16
    const val IMAGE_ASPECT_HEIGHT = 9
    const val TILE_FOOTER_HEIGHT = 18
    const val MINIMUM_IMAGE_HEIGHT = 24
    const val SCROLLBAR_WIDTH = 4
    const val SCROLLBAR_RESERVED_WIDTH = 10
    const val MINIMUM_SCROLL_THUMB_HEIGHT = 20
    const val NAVIGATION_GUTTER = 28
    const val NAVIGATION_BUTTON_WIDTH = 22
    const val NAVIGATION_BUTTON_HEIGHT = 32
    const val ACTION_HEIGHT = 18
    const val ACTION_BOTTOM = 10
    const val ACTION_GAP = 6
    const val COPY_WIDTH = 82
    const val SAVE_WIDTH = 82
    const val DELETE_WIDTH = 62
    const val CONFIRMATION_BUTTON_WIDTH = 76
    const val NOTICE_GAP = 13
    const val PREVIEW_BOTTOM_GAP = 7
}
