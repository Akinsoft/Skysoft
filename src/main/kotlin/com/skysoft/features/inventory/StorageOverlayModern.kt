package com.skysoft.features.inventory

import com.skysoft.config.StorageOverlayConfigBounds
import com.skysoft.utils.EasingUtilities
import com.skysoft.utils.SmoothFloatTransition
import com.skysoft.utils.gui.Rect
import com.skysoft.utils.gui.interpolateInt
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen

private var modernScreenId: Int? = null
private var modernScreenPageIndex: Int? = null
private var modernTransitionPageIndex: Int? = null
private var pendingModernFocus: ModernFocusRequest? = null
private var isModernFocusExpanded = false
private val modernFocusTransition = SmoothFloatTransition(
    0f,
    ModernStoragePanel.FOCUS_ANIMATION_NANOS,
    EasingUtilities::easeOutCubic,
)

internal data class ModernFocusPresentation(
    val pageIndex: Int?,
    val progress: Float,
    val isExpanded: Boolean,
)

private data class ModernFocusRequest(
    val pageIndex: Int,
    val shouldAnimate: Boolean,
)

internal fun modernMeasurements(width: Int, height: Int): Measurements {
    val pageSpacing = config.details.pageSpacing
    val columns = config.details.columns.coerceIn(
        StorageOverlayConfigBounds.MIN_COLUMNS,
        maximumStorageColumns(width, isModern = true, pageSpacing = pageSpacing),
    )
    val focus = modernFocusPresentation()
    val focusedPageHeight = focus.pageIndex
        ?.let(::storageEntry)
        ?.let(::pageHeight)
        ?: StoragePages.EMPTY_HEIGHT
    val focusedPageY = modernFocusedPageY(height, focusedPageHeight)
    val playerTargetY = focusedPageY + focusedPageHeight + ModernStoragePanel.FOCUS_GAP
    val playerY = interpolateInt(
        height + ModernStoragePanel.FOCUS_GAP,
        playerTargetY,
        focus.progress,
    )
    val searchX = (width - ModernStoragePanel.SEARCH_WIDTH) / 2
    val searchY = height - ModernStoragePanel.EDGE_MARGIN - StoragePanel.PADDING - StorageSearch.HEIGHT
    val scrollPanelY = ModernStoragePanel.EDGE_MARGIN
    val scrollPanelBottom = searchY - StorageSearch.GAP - StoragePanel.PADDING
    val scrollPanelWidth = columns * ModernStoragePanel.PAGE_WIDTH + (columns - 1) * pageSpacing
    val contentWidth = scrollPanelWidth + StorageScrollbar.GAP + StorageScrollbar.WIDTH
    val scrollPanelX = (width - contentWidth) / 2
    val scrollbarX = scrollPanelX + scrollPanelWidth + StorageScrollbar.GAP
    val scrollPanelHeight = (scrollPanelBottom - scrollPanelY).coerceAtLeast(1)
    return Measurements(
        storageX = ModernStoragePanel.EDGE_MARGIN,
        storageY = ModernStoragePanel.EDGE_MARGIN,
        storageWidth = (width - ModernStoragePanel.EDGE_MARGIN * 2).coerceAtLeast(1),
        storageHeight = (height - ModernStoragePanel.EDGE_MARGIN * 2).coerceAtLeast(1),
        scrollPanel = Rect(scrollPanelX, scrollPanelY, scrollPanelWidth, scrollPanelHeight),
        scrollbar = Rect(
            scrollbarX,
            scrollPanelY,
            StorageScrollbar.WIDTH,
            scrollPanelHeight,
        ),
        search = Rect(searchX, searchY, ModernStoragePanel.SEARCH_WIDTH, StorageSearch.HEIGHT),
        playerBounds = Rect(
            width / 2 - StoragePlayerInventory.WIDTH / 2,
            playerY,
            StoragePlayerInventory.WIDTH,
            StoragePlayerInventory.HEIGHT,
        ),
        selectorBounds = Rect(StorageRuntime.OFFSCREEN, StorageRuntime.OFFSCREEN, 0, 0),
        totalBounds = Rect(0, 0, width, height),
        columns = columns,
        pageSpacing = pageSpacing,
        isModern = true,
        focusedPageIndex = focus.pageIndex,
        focusProgress = focus.progress,
        isFocusExpanded = focus.isExpanded,
    )
}

internal fun modernStoragePageX(measurements: Measurements, column: Int): Int {
    return measurements.scrollPanel.x + column * (ModernStoragePanel.PAGE_WIDTH + measurements.pageSpacing)
}

internal fun modernFocusedPageLayout(measurements: Measurements, layout: PageLayout): PageLayout = PageLayout(
    pageIndex = layout.pageIndex,
    x = interpolateInt(layout.x, (measurements.totalBounds.width - layout.width) / 2, measurements.focusProgress),
    y = interpolateInt(
        layout.y,
        modernFocusedPageY(measurements.totalBounds.height, layout.height),
        measurements.focusProgress,
    ),
    width = layout.width,
    height = layout.height,
)

internal fun storagePageVisibleBounds(measurements: Measurements, layout: PageLayout): Rect =
    if (
        measurements.isModern &&
        measurements.focusedPageIndex == layout.pageIndex &&
        measurements.focusProgress > ModernStoragePanel.MIN_VISIBLE_PROGRESS
    ) {
        measurements.totalBounds
    } else {
        measurements.scrollPanel
    }

internal fun synchronizeModernFocus(screen: AbstractContainerScreen<*>, handle: StorageHandle) {
    if (!isModernStorageOverlay) return
    val screenId = System.identityHashCode(screen)
    val pageIndex = handle.entryIndex()
    if (screenId == modernScreenId && pageIndex == modernScreenPageIndex) return
    modernScreenId = screenId
    modernScreenPageIndex = pageIndex
    if (pageIndex == null) {
        if (pendingModernFocus == null) {
            modernTransitionPageIndex = null
            isModernFocusExpanded = false
            modernFocusTransition.snap(0f)
        }
        return
    }
    val shouldAnimate = pendingModernFocus
        ?.takeIf { it.pageIndex == pageIndex }
        ?.shouldAnimate
        ?: false
    pendingModernFocus = null
    modernTransitionPageIndex = pageIndex
    isModernFocusExpanded = true
    modernFocusTransition.snap(if (shouldAnimate) 0f else 1f)
}

internal fun modernFocusPresentation(): ModernFocusPresentation {
    if (!isModernStorageOverlay) return ModernFocusPresentation(null, 0f, false)
    val progress = modernFocusTransition.value(if (isModernFocusExpanded) 1f else 0f)
    if (!isModernFocusExpanded && progress <= ModernStoragePanel.MIN_VISIBLE_PROGRESS) {
        modernTransitionPageIndex = null
    }
    return ModernFocusPresentation(modernTransitionPageIndex, progress, isModernFocusExpanded)
}

internal fun requestModernPageFocus(pageIndex: Int, shouldAnimate: Boolean = true) {
    if (!isModernStorageOverlay) return
    pendingModernFocus = ModernFocusRequest(pageIndex, shouldAnimate)
}

internal fun expandModernPage(pageIndex: Int) {
    if (!isModernStorageOverlay) return
    pendingModernFocus = null
    modernTransitionPageIndex = pageIndex
    isModernFocusExpanded = true
    storageSearchField.focused = false
    finishTitleEdit()
}

internal fun collapseModernPage() {
    if (!isModernStorageOverlay || !isModernFocusExpanded) return
    isModernFocusExpanded = false
    storageSearchField.focused = false
    finishTitleEdit()
}

internal fun isModernPageExpanded(pageIndex: Int? = null): Boolean =
    isModernStorageOverlay &&
        isModernFocusExpanded &&
        (pageIndex == null || modernTransitionPageIndex == pageIndex)

internal fun resetModernScreenState() {
    modernScreenId = null
    modernScreenPageIndex = null
    modernTransitionPageIndex = null
    isModernFocusExpanded = false
    modernFocusTransition.snap(0f)
}

internal fun resetModernTransientState() {
    resetModernScreenState()
    pendingModernFocus = null
}

private fun modernFocusedPageY(screenHeight: Int, pageHeight: Int): Int =
    ((screenHeight - pageHeight - ModernStoragePanel.FOCUS_GAP - StoragePlayerInventory.HEIGHT) / 2)
        .coerceAtLeast(StoragePanel.TOP_MIN)
