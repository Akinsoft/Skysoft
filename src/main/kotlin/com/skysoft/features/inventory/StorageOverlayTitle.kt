package com.skysoft.features.inventory

import com.skysoft.data.ProfileStorageApi
import com.skysoft.utils.gui.Rect
import com.skysoft.utils.input.InputHandlingResult
import com.skysoft.utils.render.LegacyTextRenderer
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import org.lwjgl.glfw.GLFW

internal fun titlePageAt(layouts: Map<Int, PageLayout>, mouseX: Int, mouseY: Int): Int? =
    layouts.values.firstOrNull { layout ->
        if (fixedPageTitle(layout.pageIndex) != null) return@firstOrNull false
        val page = storageEntry(layout.pageIndex) ?: return@firstOrNull false
        titleBounds(layout, titleDisplayText(titleText(layout.pageIndex, page.title))).contains(mouseX, mouseY)
    }?.pageIndex

internal fun titleBounds(layout: PageLayout, title: String): Rect =
    Rect(
        layout.x + StorageTitle.X_OFFSET,
        layout.y + StorageTitle.Y_OFFSET,
        LegacyTextRenderer.width(title).coerceAtMost(layout.width - StorageTitle.MAX_WIDTH_INSET),
        StorageTitle.BOUNDS_HEIGHT,
    )

internal fun titleText(pageIndex: Int, title: String? = storageEntry(pageIndex)?.title): String {
    fixedPageTitle(pageIndex)?.let { return it }
    if (editingTitlePage == pageIndex) return editingTitleText
    return title?.ifBlank { defaultPageTitle(pageIndex) } ?: defaultPageTitle(pageIndex)
}

internal fun titleDisplayText(title: String): String {
    val builder = StringBuilder(title.length)
    var index = 0
    while (index < title.length) {
        val next = title.getOrNull(index + 1)
        if (title[index] == '&' && next != null && next.lowercaseChar() in "0123456789abcdefklmnor") {
            builder.append('§').append(next.lowercaseChar())
            index += FORMATTING_CODE_LENGTH
        } else {
            builder.append(title[index])
            index++
        }
    }
    return builder.toString()
}

internal fun startTitleEdit(pageIndex: Int) {
    editingTitleText = titleText(pageIndex)
    editingTitlePage = pageIndex
    editingTitleSelected = false
}

internal fun finishTitleEdit() {
    val pageIndex = editingTitlePage ?: return
    val page = storageEntry(pageIndex)
    val title = editingTitleText.ifBlank { defaultPageTitle(pageIndex) }
    if (page != null && page.title != title) {
        page.title = title
        ProfileStorageApi.markDirty()
    }
    editingTitlePage = null
    editingTitleText = ""
    editingTitleSelected = false
}

internal fun handleTitleEditKeyPress(screen: AbstractContainerScreen<*>, event: KeyEvent): InputHandlingResult {
    if (editingTitlePage == null) return InputHandlingResult.IGNORED
    val control = event.modifiers() and GLFW.GLFW_MOD_CONTROL != 0
    when (event.key()) {
        GLFW.GLFW_KEY_ESCAPE, GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> finishTitleEdit()
        GLFW.GLFW_KEY_BACKSPACE -> removeTitleText()
        GLFW.GLFW_KEY_DELETE -> clearTitleText()
        GLFW.GLFW_KEY_A -> if (control) editingTitleSelected = editingTitleText.isNotEmpty()
        GLFW.GLFW_KEY_V -> if (control) appendTitleText(Minecraft.getInstance().keyboardHandler.clipboard)
    }
    storageOverlayLayoutScreen(screen)
    return InputHandlingResult.CONSUMED
}

internal fun handleStorageOverlayCharTyped(
    screen: AbstractContainerScreen<*>,
    event: CharacterEvent,
): InputHandlingResult {
    if (!storageOverlayIsActive(screen)) return InputHandlingResult.IGNORED
    return when {
        editingTitlePage != null -> {
            appendTitleText(event.codepointAsString())
            storageOverlayLayoutScreen(screen)
            InputHandlingResult.CONSUMED
        }
        storageSearchField.focused && event.isAllowedChatCharacter -> {
            storageSearchField.charTyped(event)
            resetStorageScroll()
            storageOverlayLayoutScreen(screen)
            InputHandlingResult.CONSUMED
        }
        else -> InputHandlingResult.IGNORED
    }
}

private fun appendTitleText(value: String) {
    val text = if (editingTitleSelected) "" else editingTitleText
    editingTitleText = (text + value).filterTitle().take(StorageTitle.MAX_LENGTH)
    editingTitleSelected = false
}

private fun removeTitleText() {
    if (editingTitleSelected) {
        clearTitleText()
    } else if (editingTitleText.isNotEmpty()) {
        editingTitleText = editingTitleText.dropLast(1)
    }
    editingTitleSelected = false
}

private fun clearTitleText() {
    editingTitleText = ""
    editingTitleSelected = false
}

private fun String.filterTitle(): String =
    filter {
        it == '§' ||
            it.code in StorageTextInput.PRINTABLE_ASCII_START..StorageTextInput.PRINTABLE_ASCII_END ||
            it.code > StorageTextInput.EXTENDED_TEXT_START
    }

private const val FORMATTING_CODE_LENGTH = 2
