package com.skysoft.features.inventory

import com.skysoft.utils.input.InputHandlingResult
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.input.KeyEvent
import org.lwjgl.glfw.GLFW

internal fun handleStorageOverlayPageNavigation(
    screen: AbstractContainerScreen<*>,
    event: KeyEvent,
): InputHandlingResult {
    val direction = storageNavigationDirection(event.key()) ?: return InputHandlingResult.IGNORED
    val layoutState = storageOverlayLayoutScreen(screen)
    val activePage = layoutState?.handle?.entryIndex()
    val pageIndices = layoutState?.pageLayoutResult?.pages?.keys?.toList().orEmpty()
    val targetIndex = storageNavigationTargetIndex(
        pageIndices.indexOf(activePage),
        pageIndices.size,
        layoutState?.measurements?.columns ?: 1,
        direction,
    )
    targetIndex?.let(pageIndices::get)
        ?.takeIf { it != activePage }
        ?.let { tryNavigateTo(screen, it) }
    return InputHandlingResult.CONSUMED
}

private fun storageNavigationDirection(key: Int): Pair<Int, Int>? = when (key) {
    GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_A -> -1 to 0
    GLFW.GLFW_KEY_RIGHT, GLFW.GLFW_KEY_D -> 1 to 0
    GLFW.GLFW_KEY_UP, GLFW.GLFW_KEY_W -> 0 to -1
    GLFW.GLFW_KEY_DOWN, GLFW.GLFW_KEY_S -> 0 to 1
    else -> null
}

private fun storageNavigationTargetIndex(
    activeIndex: Int,
    pageCount: Int,
    columns: Int,
    direction: Pair<Int, Int>,
): Int? {
    if (pageCount <= 1 || activeIndex !in 0 until pageCount) return null
    val (deltaColumn, deltaRow) = direction
    val targetIndex = if (deltaRow != 0) {
        activeIndex + deltaRow * columns
    } else {
        Math.floorMod(activeIndex + deltaColumn, pageCount)
    }
    return targetIndex.takeIf { it in 0 until pageCount }
}
