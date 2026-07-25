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
            val rowCount = Math.ceilDiv(entryCount, ScreenshotLayoutDimensions.COLUMNS)
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
    val toolButtons: Map<ScreenshotEditorTool, Rect>,
    val zoomOut: Rect,
    val zoomValue: Rect,
    val zoomIn: Rect,
    val zoomFit: Rect,
    val undo: Rect,
    val redo: Rect,
    val reset: Rect,
    val editorContext: Rect,
    val colorSwatches: Map<ScreenshotDrawColor, Rect>,
    val brushSizes: Map<ScreenshotBrushSize, Rect>,
    val resetCrop: Rect,
    val preview: Rect,
    val previous: Rect,
    val next: Rect,
    val noticeY: Int,
    val share: Rect,
    val copy: Rect,
    val edit: Rect,
    val save: Rect,
    val delete: Rect,
    val confirmationButtons: ScreenshotConfirmationButtons,
    val saveButtons: ScreenshotSaveButtons,
) {
    companion object {
        fun create(screenWidth: Int, screenHeight: Int, isEditing: Boolean): ScreenshotFocusLayout {
            val panel = screenshotPanel(screenWidth, screenHeight)
            val back = Rect(
                panel.x + ScreenshotLayoutDimensions.PADDING,
                panel.y + ScreenshotLayoutDimensions.HEADER_BUTTON_Y,
                ScreenshotLayoutDimensions.HEADER_BUTTON_SIZE,
                ScreenshotLayoutDimensions.HEADER_BUTTON_SIZE,
            )
            val close = screenshotCloseButton(panel)
            val toolbar = createFocusToolbar(panel)
            val context = createFocusEditorContext(panel, toolbar.y)
            val actions = createFocusActions(panel, isEditing)
            val navigation = createFocusNavigation(panel, actions.noticeY, isEditing)
            return ScreenshotFocusLayout(
                panel,
                back,
                close,
                toolbar.toolButtons,
                toolbar.zoomOut,
                toolbar.zoomValue,
                toolbar.zoomIn,
                toolbar.zoomFit,
                toolbar.undo,
                toolbar.redo,
                toolbar.reset,
                context.bounds,
                context.colorSwatches,
                context.brushSizes,
                context.resetCrop,
                navigation.preview,
                navigation.previous,
                navigation.next,
                actions.noticeY,
                actions.share,
                actions.copy,
                actions.edit,
                actions.save,
                actions.delete,
                actions.confirmationButtons,
                actions.saveButtons,
            )
        }
    }
}

internal data class ScreenshotConfirmationButtons(
    val cancel: Rect,
    val confirm: Rect,
)

internal data class ScreenshotSaveButtons(
    val saveNew: Rect,
    val replace: Rect,
    val cancel: Rect,
)

private data class FocusToolbarLayout(
    val y: Int,
    val toolButtons: Map<ScreenshotEditorTool, Rect>,
    val zoomOut: Rect,
    val zoomValue: Rect,
    val zoomIn: Rect,
    val zoomFit: Rect,
    val undo: Rect,
    val redo: Rect,
    val reset: Rect,
)

private fun createFocusToolbar(panel: Rect): FocusToolbarLayout {
    val y = panel.y + ScreenshotLayoutDimensions.HEADER_HEIGHT + ScreenshotLayoutDimensions.TOOLBAR_TOP
    val toolButtons = ScreenshotEditorTool.entries.associateWith { tool ->
        Rect(
            panel.x + ScreenshotLayoutDimensions.PADDING +
                tool.ordinal * (ScreenshotLayoutDimensions.TOOL_WIDTH + ScreenshotLayoutDimensions.TOOL_GAP),
            y,
            ScreenshotLayoutDimensions.TOOL_WIDTH,
            ScreenshotLayoutDimensions.ACTION_HEIGHT,
        )
    }
    val zoomOut = Rect(
        toolButtons.getValue(ScreenshotEditorTool.DRAW).let { it.x + it.width } +
            ScreenshotLayoutDimensions.TOOLBAR_SECTION_GAP,
        y,
        ScreenshotLayoutDimensions.ZOOM_STEP_WIDTH,
        ScreenshotLayoutDimensions.ACTION_HEIGHT,
    )
    val zoomValue = nextToolbarControl(zoomOut, ScreenshotLayoutDimensions.ZOOM_VALUE_WIDTH)
    val zoomIn = nextToolbarControl(zoomValue, ScreenshotLayoutDimensions.ZOOM_STEP_WIDTH)
    val zoomFit = nextToolbarControl(zoomIn, ScreenshotLayoutDimensions.ZOOM_FIT_WIDTH)
    val historyWidth = ScreenshotLayoutDimensions.HISTORY_WIDTH * ScreenshotLayoutDimensions.HISTORY_BUTTON_COUNT +
        ScreenshotLayoutDimensions.TOOL_GAP * (ScreenshotLayoutDimensions.HISTORY_BUTTON_COUNT - 1)
    val undo = Rect(
        panel.x + panel.width - ScreenshotLayoutDimensions.PADDING - historyWidth,
        y,
        ScreenshotLayoutDimensions.HISTORY_WIDTH,
        ScreenshotLayoutDimensions.ACTION_HEIGHT,
    )
    val redo = nextToolbarControl(undo, ScreenshotLayoutDimensions.HISTORY_WIDTH)
    val reset = nextToolbarControl(redo, ScreenshotLayoutDimensions.HISTORY_WIDTH)
    return FocusToolbarLayout(y, toolButtons, zoomOut, zoomValue, zoomIn, zoomFit, undo, redo, reset)
}

private fun nextToolbarControl(previous: Rect, width: Int): Rect = Rect(
    previous.x + previous.width + ScreenshotLayoutDimensions.TOOL_GAP,
    previous.y,
    width,
    ScreenshotLayoutDimensions.ACTION_HEIGHT,
)

private data class FocusEditorContextLayout(
    val bounds: Rect,
    val colorSwatches: Map<ScreenshotDrawColor, Rect>,
    val brushSizes: Map<ScreenshotBrushSize, Rect>,
    val resetCrop: Rect,
)

private fun createFocusEditorContext(panel: Rect, toolbarY: Int): FocusEditorContextLayout {
    val y = toolbarY + ScreenshotLayoutDimensions.ACTION_HEIGHT + ScreenshotLayoutDimensions.TOOLBAR_ROW_GAP
    val bounds = Rect(
        panel.x + ScreenshotLayoutDimensions.PADDING,
        y,
        panel.width - ScreenshotLayoutDimensions.PADDING * 2,
        ScreenshotLayoutDimensions.ACTION_HEIGHT,
    )
    val colorStartX = bounds.x + ScreenshotLayoutDimensions.COLOR_LABEL_WIDTH
    val colorSwatches = ScreenshotDrawColor.entries.associateWith { color ->
        Rect(
            colorStartX + color.ordinal * (ScreenshotLayoutDimensions.SWATCH_SIZE + ScreenshotLayoutDimensions.SWATCH_GAP),
            y + (ScreenshotLayoutDimensions.ACTION_HEIGHT - ScreenshotLayoutDimensions.SWATCH_SIZE) / 2,
            ScreenshotLayoutDimensions.SWATCH_SIZE,
            ScreenshotLayoutDimensions.SWATCH_SIZE,
        )
    }
    val brushStartX = colorSwatches.getValue(ScreenshotDrawColor.PURPLE).let { it.x + it.width } +
        ScreenshotLayoutDimensions.TOOLBAR_SECTION_GAP + ScreenshotLayoutDimensions.BRUSH_LABEL_WIDTH
    val brushSizes = ScreenshotBrushSize.entries.associateWith { size ->
        Rect(
            brushStartX + size.ordinal * (ScreenshotLayoutDimensions.BRUSH_WIDTH + ScreenshotLayoutDimensions.TOOL_GAP),
            y,
            ScreenshotLayoutDimensions.BRUSH_WIDTH,
            ScreenshotLayoutDimensions.ACTION_HEIGHT,
        )
    }
    val resetCrop = Rect(
        bounds.x,
        y,
        ScreenshotLayoutDimensions.RESET_CROP_WIDTH,
        ScreenshotLayoutDimensions.ACTION_HEIGHT,
    )
    return FocusEditorContextLayout(bounds, colorSwatches, brushSizes, resetCrop)
}

private data class FocusActionsLayout(
    val noticeY: Int,
    val share: Rect,
    val copy: Rect,
    val edit: Rect,
    val save: Rect,
    val delete: Rect,
    val confirmationButtons: ScreenshotConfirmationButtons,
    val saveButtons: ScreenshotSaveButtons,
)

private fun createFocusActions(panel: Rect, isEditing: Boolean): FocusActionsLayout {
    val y = panel.y + panel.height - ScreenshotLayoutDimensions.ACTION_BOTTOM -
        ScreenshotLayoutDimensions.ACTION_HEIGHT
    val groupWidth = ScreenshotLayoutDimensions.SHARE_WIDTH + ScreenshotLayoutDimensions.COPY_WIDTH +
        ScreenshotLayoutDimensions.EDIT_WIDTH + ScreenshotLayoutDimensions.DELETE_WIDTH +
        (if (isEditing) ScreenshotLayoutDimensions.SAVE_WIDTH + ScreenshotLayoutDimensions.ACTION_GAP else 0) +
        ScreenshotLayoutDimensions.ACTION_GAP * ScreenshotLayoutDimensions.VIEW_ACTION_GAP_COUNT
    val share = Rect(
        panel.x + (panel.width - groupWidth) / 2,
        y,
        ScreenshotLayoutDimensions.SHARE_WIDTH,
        ScreenshotLayoutDimensions.ACTION_HEIGHT,
    )
    val copy = nextAction(share, ScreenshotLayoutDimensions.COPY_WIDTH)
    val edit = nextAction(copy, ScreenshotLayoutDimensions.EDIT_WIDTH)
    val save = nextAction(edit, ScreenshotLayoutDimensions.SAVE_WIDTH)
    val delete = nextAction(if (isEditing) save else edit, ScreenshotLayoutDimensions.DELETE_WIDTH)
    return FocusActionsLayout(
        y - ScreenshotLayoutDimensions.NOTICE_GAP,
        share,
        copy,
        edit,
        save,
        delete,
        createConfirmationButtons(panel, y),
        createSaveButtons(panel, y),
    )
}

private fun createConfirmationButtons(panel: Rect, y: Int): ScreenshotConfirmationButtons {
    val width = ScreenshotLayoutDimensions.CONFIRMATION_BUTTON_WIDTH * 2 + ScreenshotLayoutDimensions.ACTION_GAP
    val cancel = Rect(
        panel.x + (panel.width - width) / 2,
        y,
        ScreenshotLayoutDimensions.CONFIRMATION_BUTTON_WIDTH,
        ScreenshotLayoutDimensions.ACTION_HEIGHT,
    )
    return ScreenshotConfirmationButtons(
        cancel,
        nextAction(cancel, ScreenshotLayoutDimensions.CONFIRMATION_BUTTON_WIDTH),
    )
}

private fun createSaveButtons(panel: Rect, y: Int): ScreenshotSaveButtons {
    val width = ScreenshotLayoutDimensions.SAVE_NEW_CHOICE_WIDTH +
        ScreenshotLayoutDimensions.REPLACE_CHOICE_WIDTH +
        ScreenshotLayoutDimensions.CONFIRMATION_BUTTON_WIDTH +
        ScreenshotLayoutDimensions.ACTION_GAP * 2
    val saveNew = Rect(
        panel.x + (panel.width - width) / 2,
        y,
        ScreenshotLayoutDimensions.SAVE_NEW_CHOICE_WIDTH,
        ScreenshotLayoutDimensions.ACTION_HEIGHT,
    )
    val replace = nextAction(saveNew, ScreenshotLayoutDimensions.REPLACE_CHOICE_WIDTH)
    return ScreenshotSaveButtons(
        saveNew,
        replace,
        nextAction(replace, ScreenshotLayoutDimensions.CONFIRMATION_BUTTON_WIDTH),
    )
}

private fun nextAction(previous: Rect, width: Int): Rect = Rect(
    previous.x + previous.width + ScreenshotLayoutDimensions.ACTION_GAP,
    previous.y,
    width,
    ScreenshotLayoutDimensions.ACTION_HEIGHT,
)

private data class FocusNavigationLayout(
    val preview: Rect,
    val previous: Rect,
    val next: Rect,
)

private fun createFocusNavigation(panel: Rect, noticeY: Int, isEditing: Boolean): FocusNavigationLayout {
    val previewY = panel.y + ScreenshotLayoutDimensions.HEADER_HEIGHT +
        if (isEditing) ScreenshotLayoutDimensions.EDITOR_TOOLBAR_HEIGHT else ScreenshotLayoutDimensions.VIEW_TOP_INSET
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
    return FocusNavigationLayout(preview, previous, next)
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
    const val EDITOR_TOOLBAR_HEIGHT = 52
    const val VIEW_TOP_INSET = 8
    const val TOOLBAR_TOP = 5
    const val TOOLBAR_ROW_GAP = 4
    const val TOOLBAR_SECTION_GAP = 12
    const val TOOL_WIDTH = 48
    const val TOOL_GAP = 4
    const val ZOOM_STEP_WIDTH = 24
    const val ZOOM_VALUE_WIDTH = 44
    const val ZOOM_FIT_WIDTH = 34
    const val HISTORY_WIDTH = 46
    const val HISTORY_BUTTON_COUNT = 3
    const val COLOR_LABEL_WIDTH = 34
    const val SWATCH_SIZE = 14
    const val SWATCH_GAP = 4
    const val BRUSH_LABEL_WIDTH = 38
    const val BRUSH_WIDTH = 22
    const val RESET_CROP_WIDTH = 72
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
    const val VIEW_ACTION_GAP_COUNT = 3
    const val SHARE_WIDTH = 72
    const val COPY_WIDTH = 68
    const val EDIT_WIDTH = 62
    const val SAVE_WIDTH = 62
    const val DELETE_WIDTH = 62
    const val CONFIRMATION_BUTTON_WIDTH = 76
    const val SAVE_NEW_CHOICE_WIDTH = 78
    const val REPLACE_CHOICE_WIDTH = 72
    const val NOTICE_GAP = 13
    const val PREVIEW_BOTTOM_GAP = 7
}
