package com.skysoft.features.inventory

import com.skysoft.utils.input.InputHandlingResult
import net.minecraft.client.input.MouseButtonEvent
import org.lwjgl.glfw.GLFW

internal fun processModernFocusCollapse(
    click: MouseButtonEvent,
    measurements: Measurements,
    layouts: Map<Int, PageLayout>,
    activePage: Int?,
    mouseX: Int,
    mouseY: Int,
): InputHandlingResult {
    if (
        !measurements.isModern ||
        !measurements.isFocusExpanded ||
        click.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT
    ) {
        return InputHandlingResult.IGNORED
    }
    val focusedPage = activePage?.let(layouts::get)
    if (focusedPage?.contains(mouseX, mouseY) == true || measurements.playerBounds.contains(mouseX, mouseY)) {
        return InputHandlingResult.IGNORED
    }
    collapseModernPage()
    return InputHandlingResult.CONSUMED
}

internal fun routeModernForegroundClick(
    click: MouseButtonEvent,
    measurements: Measurements,
    layouts: Map<Int, PageLayout>,
    activePage: Int?,
    mouseX: Int,
    mouseY: Int,
): InputHandlingResult? {
    if (!measurements.isModern || !measurements.isFocusExpanded) return null
    if (measurements.playerBounds.contains(mouseX, mouseY)) return InputHandlingResult.IGNORED
    val focusedLayout = activePage?.let(layouts::get)
    if (focusedLayout?.contains(mouseX, mouseY) != true) return null
    val titlePage = if (click.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
        titlePageAt(layouts, mouseX, mouseY)
    } else {
        null
    }
    if (titlePage != null) {
        startTitleEdit(titlePage)
        storageSearchField.focused = false
        return InputHandlingResult.CONSUMED
    }
    return InputHandlingResult.IGNORED
}
