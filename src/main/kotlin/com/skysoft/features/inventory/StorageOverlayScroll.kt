package com.skysoft.features.inventory

import com.skysoft.utils.gui.Rect
import com.skysoft.utils.input.InputHandlingResult
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.roundToInt
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.input.MouseButtonEvent
import org.lwjgl.glfw.GLFW

internal var scroll = 0
internal var scrollPosition = 0.0
internal var scrollTarget = 0.0
internal var scrollbarDragOffset: Int? = null
internal var lastScrollUpdateNanos = 0L

internal fun focusPage(measurements: Measurements, layoutResult: PageLayoutResult, pageIndex: Int) {
    val layout = layoutResult.pages[pageIndex] ?: return
    if (layout.isVerticallyVisibleWithin(measurements.scrollPanel)) {
        freezeStorageScroll()
        return
    }
    scrollTarget = centeredScrollForPage(scroll, measurements, layoutResult, pageIndex).toDouble()
        .coerceIn(0.0, maxScroll(measurements, layoutResult.contentHeight).toDouble())
}

internal fun centeredScrollForPage(
    currentScroll: Int,
    measurements: Measurements,
    layoutResult: PageLayoutResult,
    pageIndex: Int,
): Int {
    val layout = layoutResult.pages[pageIndex] ?: return currentScroll
    val pageTop = layout.y + currentScroll - measurements.scrollPanel.y
    val centerOffset = (measurements.scrollPanel.height - layout.height).coerceAtLeast(0) / 2
    return (pageTop - centerOffset).coerceIn(0, maxScroll(measurements, layoutResult.contentHeight))
}

internal fun moveStorageScrollTarget(delta: Double, maximum: Int) {
    advanceStorageScroll()
    scrollTarget = (scrollTarget + delta).coerceIn(0.0, maximum.coerceAtLeast(0).toDouble())
}

internal fun setStorageScrollFromScrollbar(
    pointerY: Int,
    dragOffset: Int,
    bar: Rect,
    knobHeight: Int,
    maximum: Int,
) {
    snapStorageScroll(scrollbarScrollPosition(pointerY, dragOffset, bar, knobHeight, maximum))
}

internal fun snapStorageScroll(position: Double) {
    scrollPosition = position
    scrollTarget = position
    scroll = position.roundToInt()
}

internal fun scrollbarScrollPosition(
    pointerY: Int,
    dragOffset: Int,
    bar: Rect,
    knobHeight: Int,
    maximum: Int,
): Double {
    val knobTravel = (bar.height - knobHeight).coerceAtLeast(0)
    val knobY = (pointerY - dragOffset - bar.y).coerceIn(0, knobTravel)
    return if (knobTravel == 0) 0.0 else knobY.toDouble() / knobTravel * maximum
}

internal fun handleStorageOverlayMouseDrag(
    screen: AbstractContainerScreen<*>,
    click: MouseButtonEvent,
): InputHandlingResult {
    if (processStorageSettingsDrag(screen, click) == InputHandlingResult.CONSUMED) {
        return InputHandlingResult.CONSUMED
    }
    val dragOffset = scrollbarDragOffset ?: return InputHandlingResult.IGNORED
    if (click.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) return InputHandlingResult.IGNORED
    val layoutState = storageOverlayLayoutScreen(screen) ?: run {
        scrollbarDragOffset = null
        return InputHandlingResult.IGNORED
    }
    val measurements = layoutState.measurements
    val contentHeight = layoutState.pageLayoutResult.contentHeight
    setStorageScrollFromScrollbar(
        click.y().toInt(),
        dragOffset,
        measurements.scrollbar,
        scrollbarKnobHeight(measurements, contentHeight),
        maxScroll(measurements, contentHeight),
    )
    return InputHandlingResult.CONSUMED
}

internal fun handleStorageOverlayMouseRelease(click: MouseButtonEvent): InputHandlingResult {
    if (processStorageSettingsRelease(click) == InputHandlingResult.CONSUMED) {
        return InputHandlingResult.CONSUMED
    }
    if (click.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT || scrollbarDragOffset == null) {
        return InputHandlingResult.IGNORED
    }
    scrollbarDragOffset = null
    return InputHandlingResult.CONSUMED
}

internal fun shouldPreferStorageOverlayMouseScroll(
    screen: AbstractContainerScreen<*>,
    mouseX: Double,
    mouseY: Double,
    scrollY: Double,
): Boolean {
    val handle = handleFor(screen)
    if (handle == null || scrollY == 0.0) return false
    val measurements = measurements(screen.width, screen.height, handle.isSelectorVisible())
    if (measurements.isFocusExpanded) return false
    return measurements.scrollPanel.contains(mouseX.toInt(), mouseY.toInt()) ||
        storageSettingsContains(screen.width, screen.height, measurements, mouseX.toInt(), mouseY.toInt())
}

internal fun shouldPreferStorageOverlayMouseScroll(
    isActive: Boolean,
    scrollPanel: Rect,
    mouseX: Double,
    mouseY: Double,
    scrollY: Double,
): Boolean = isActive && scrollY != 0.0 && scrollPanel.contains(mouseX.toInt(), mouseY.toInt())

internal fun advanceStorageScroll(nowNanos: Long = System.nanoTime()) {
    if (lastScrollUpdateNanos == 0L) {
        lastScrollUpdateNanos = nowNanos
        return
    }
    val elapsedMillis = ((nowNanos - lastScrollUpdateNanos).coerceAtLeast(0L) / NANOS_PER_MILLISECOND)
        .coerceAtMost(StorageRuntime.MAX_SCROLL_FRAME_MILLIS)
    lastScrollUpdateNanos = nowNanos
    scrollPosition = smoothedScrollPosition(scrollPosition, scrollTarget, elapsedMillis)
    if (abs(scrollTarget - scrollPosition) <= StorageRuntime.SCROLL_SETTLE_DISTANCE) {
        scrollPosition = scrollTarget
    }
    scroll = scrollPosition.roundToInt()
}

internal fun coerceStorageScroll(maximum: Int) {
    val upperBound = maximum.coerceAtLeast(0).toDouble()
    scrollPosition = scrollPosition.coerceIn(0.0, upperBound)
    scrollTarget = scrollTarget.coerceIn(0.0, upperBound)
    scroll = scrollPosition.roundToInt()
}

internal fun resetStorageScroll() {
    scroll = 0
    scrollPosition = 0.0
    scrollTarget = 0.0
    lastScrollUpdateNanos = 0L
}

internal fun freezeStorageScroll() {
    snapStorageScroll(scrollPosition)
    lastScrollUpdateNanos = 0L
}

internal fun smoothedScrollPosition(current: Double, target: Double, elapsedMillis: Double): Double {
    if (current == target || elapsedMillis <= 0.0) return current
    val progress = 1.0 - exp(-elapsedMillis / StorageRuntime.SCROLL_RESPONSE_MILLIS)
    return current + (target - current) * progress
}

private const val NANOS_PER_MILLISECOND = 1_000_000.0
