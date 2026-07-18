package com.skysoft.utils.gui

import com.skysoft.utils.input.InputHandlingResult
import com.skysoft.utils.input.InputUtilities
import com.skysoft.utils.ColorUtilities.withScaledAlpha
import com.skysoft.utils.render.LegacyTextRenderer
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import org.lwjgl.glfw.GLFW

internal class TextFieldState(text: String = "", val maxLength: Int = 256) {
    var text: String = text.take(maxLength)
        set(value) {
            val boundedValue = value.take(maxLength)
            if (boundedValue == field) return
            val wasAtEnd = cursorIndex == field.length
            field = boundedValue
            cursorIndex = if (wasAtEnd) field.length else cursorIndex.coerceIn(0, field.length)
            selectionAnchor = null
        }

    var focused = false
        set(value) {
            if (value && !field) restartCursorBlink()
            if (!value) selectionAnchor = null
            field = value
        }

    var cursorIndex = this.text.length
        private set

    val selectedCharacterCount: Int
        get() = selection()?.let { it.endExclusive - it.start } ?: 0

    fun render(
        context: GuiGraphicsExtractor,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        placeholder: String,
        prefix: String = "",
        alpha: Double = 1.0,
        backgroundColor: Int = BACKGROUND_COLOR,
        outlineColor: Int? = null,
    ) {
        context.fill(x, y, x + width, y + height, backgroundColor.withScaledAlpha(alpha))
        context.outline(
            x,
            y,
            width,
            height,
            (outlineColor ?: if (focused) FOCUSED_OUTLINE_COLOR else OUTLINE_COLOR).withScaledAlpha(alpha),
        )
        val font = Minecraft.getInstance().font
        val contentWidth = (width - TEXT_X_OFFSET - TEXT_RIGHT_INSET).coerceAtLeast(0)
        val visibleText = visibleText(font, contentWidth, prefix)
        val isPlaceholderVisible = text.isEmpty() && !focused
        val displayText = if (isPlaceholderVisible) {
            font.plainSubstrByWidth(placeholder, contentWidth)
        } else {
            visibleText.text
        }
        if (!isPlaceholderVisible) {
            drawSelection(context, font, visibleText, x, y, height, prefix, alpha)
        }
        LegacyTextRenderer.draw(
            context,
            displayText,
            x + TEXT_X_OFFSET,
            textFieldTextY(y, height, font.lineHeight),
            shadow = false,
            defaultColor = (if (isPlaceholderVisible) PLACEHOLDER_COLOR else TEXT_COLOR).withScaledAlpha(alpha),
        )
        if (focused && isCursorVisible()) {
            val cursorText = visibleText.text.substring(0, visibleText.cursorOffset)
            val cursorX = x + TEXT_X_OFFSET + font.width(cursorText)
            context.fill(
                cursorX,
                y + CURSOR_Y_INSET,
                cursorX + CURSOR_WIDTH,
                y + height - CURSOR_Y_INSET,
                TEXT_COLOR.withScaledAlpha(alpha),
            )
        }
    }

    fun placeCursorAt(mouseX: Int, fieldX: Int, width: Int, prefix: String = "") {
        val font = Minecraft.getInstance().font
        val contentWidth = (width - TEXT_X_OFFSET - TEXT_RIGHT_INSET).coerceAtLeast(0)
        val visibleText = visibleText(font, contentWidth, prefix)
        val clickX = mouseX - fieldX - TEXT_X_OFFSET
        val clickedOffset = textFieldCursorOffsetAt(visibleText.text, clickX, font::width)
        cursorIndex = (visibleText.startIndex + clickedOffset - prefix.length).coerceIn(0, text.length)
        selectionAnchor = null
        restartCursorBlink()
    }

    fun moveCursorToEnd() {
        cursorIndex = text.length
        selectionAnchor = null
        restartCursorBlink()
    }

    fun keyPressed(event: KeyEvent): InputHandlingResult {
        val control = event.modifiers() and GLFW.GLFW_MOD_CONTROL != 0
        return when (event.key()) {
            GLFW.GLFW_KEY_BACKSPACE -> {
                deleteBeforeCursor(control)
                InputHandlingResult.CONSUMED
            }
            GLFW.GLFW_KEY_DELETE -> {
                deleteAfterCursor()
                InputHandlingResult.CONSUMED
            }
            GLFW.GLFW_KEY_LEFT -> moveCursor(-1)
            GLFW.GLFW_KEY_RIGHT -> moveCursor(1)
            GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                focused = false
                InputHandlingResult.CONSUMED
            }
            GLFW.GLFW_KEY_V -> if (control) pasteClipboard() else InputHandlingResult.IGNORED
            GLFW.GLFW_KEY_A -> {
                if (control) {
                    selectAll()
                    InputHandlingResult.CONSUMED
                } else {
                    InputHandlingResult.IGNORED
                }
            }
            else -> InputHandlingResult.IGNORED
        }
    }

    fun charTyped(event: CharacterEvent) {
        insertAtCursor(event.codepointAsString())
    }

    private fun pasteClipboard(): InputHandlingResult {
        insertAtCursor(InputUtilities.clipboardAscii())
        return InputHandlingResult.CONSUMED
    }

    private fun insertAtCursor(value: String) {
        val selection = selection()
        val insertionIndex = selection?.start ?: cursorIndex
        val replacedEnd = selection?.endExclusive ?: cursorIndex
        val remainingLength = text.length - (replacedEnd - insertionIndex)
        val insertion = value.take((maxLength - remainingLength).coerceAtLeast(0))
        if (insertion.isEmpty()) return
        text = text.replaceRange(insertionIndex, replacedEnd, insertion)
        cursorIndex = insertionIndex + insertion.length
        selectionAnchor = null
        restartCursorBlink()
    }

    private fun deleteBeforeCursor(control: Boolean) {
        if (removeSelection() != null) return
        if (cursorIndex == 0) return
        val textBeforeCursor = text.substring(0, cursorIndex)
        val remainingPrefix = if (control) {
            textAfterDeletingPreviousWord(textBeforeCursor)
        } else {
            textBeforeCursor.dropLast(1)
        }
        text = remainingPrefix + text.substring(cursorIndex)
        cursorIndex = remainingPrefix.length
        restartCursorBlink()
    }

    private fun deleteAfterCursor() {
        if (removeSelection() != null) return
        if (cursorIndex >= text.length) return
        text = text.removeRange(cursorIndex, cursorIndex + 1)
        restartCursorBlink()
    }

    private fun moveCursor(offset: Int): InputHandlingResult {
        val selection = selection()
        cursorIndex = when {
            selection == null -> (cursorIndex + offset).coerceIn(0, text.length)
            offset < 0 -> selection.start
            else -> selection.endExclusive
        }
        selectionAnchor = null
        restartCursorBlink()
        return InputHandlingResult.CONSUMED
    }

    private fun selectAll() {
        selectionAnchor = 0.takeIf { text.isNotEmpty() }
        cursorIndex = text.length
        restartCursorBlink()
    }

    private fun removeSelection(): TextSelection? {
        val selection = selection() ?: return null
        text = text.removeRange(selection.start, selection.endExclusive)
        cursorIndex = selection.start
        selectionAnchor = null
        restartCursorBlink()
        return selection
    }

    private fun selection(): TextSelection? {
        val anchor = selectionAnchor ?: return null
        if (anchor == cursorIndex) return null
        return TextSelection(minOf(anchor, cursorIndex), maxOf(anchor, cursorIndex))
    }

    private fun drawSelection(
        context: GuiGraphicsExtractor,
        font: net.minecraft.client.gui.Font,
        visibleText: VisibleText,
        x: Int,
        y: Int,
        height: Int,
        prefix: String,
        alpha: Double,
    ) {
        val selection = selection() ?: return
        val fullSelectionStart = prefix.length + selection.start
        val fullSelectionEnd = prefix.length + selection.endExclusive
        val visibleStart = visibleText.startIndex
        val visibleEnd = visibleStart + visibleText.text.length
        val selectedStart = maxOf(fullSelectionStart, visibleStart)
        val selectedEnd = minOf(fullSelectionEnd, visibleEnd)
        if (selectedStart >= selectedEnd) return
        val startOffset = selectedStart - visibleStart
        val endOffset = selectedEnd - visibleStart
        val selectionX = x + TEXT_X_OFFSET + font.width(visibleText.text.substring(0, startOffset))
        val selectionEndX = x + TEXT_X_OFFSET + font.width(visibleText.text.substring(0, endOffset))
        context.fill(
            selectionX,
            y + CURSOR_Y_INSET,
            selectionEndX,
            y + height - CURSOR_Y_INSET,
            SELECTION_COLOR.withScaledAlpha(alpha),
        )
    }

    private fun visibleText(font: net.minecraft.client.gui.Font, contentWidth: Int, prefix: String): VisibleText {
        val fullText = prefix + text
        val fullCursorIndex = prefix.length + cursorIndex
        val textBeforeCursor = fullText.substring(0, fullCursorIndex)
        val visiblePrefix = font.plainSubstrByWidth(textBeforeCursor, contentWidth, true)
        val startIndex = fullCursorIndex - visiblePrefix.length
        val visible = font.plainSubstrByWidth(fullText.substring(startIndex), contentWidth)
        return VisibleText(visible, startIndex, fullCursorIndex - startIndex)
    }

    private fun isCursorVisible(): Boolean =
        ((System.currentTimeMillis() - cursorBlinkStartedAt) / CURSOR_BLINK_MILLIS) % CURSOR_BLINK_PHASES == 0L

    private fun restartCursorBlink() {
        cursorBlinkStartedAt = System.currentTimeMillis()
    }

    private var cursorBlinkStartedAt = System.currentTimeMillis()
    private var selectionAnchor: Int? = null

    private companion object {
        const val TEXT_X_OFFSET = 5
        const val TEXT_RIGHT_INSET = 5
        const val CURSOR_BLINK_MILLIS = 500L
        const val CURSOR_BLINK_PHASES = 2L
        const val CURSOR_Y_INSET = 4
        const val CURSOR_WIDTH = 1

        val BACKGROUND_COLOR = 0xFF080808.toInt()
        val FOCUSED_OUTLINE_COLOR = 0xFF55FFFF.toInt()
        val OUTLINE_COLOR = 0xFF505050.toInt()
        val PLACEHOLDER_COLOR = 0xFF555555.toInt()
        val SELECTION_COLOR = 0xFF0000AA.toInt()
        val TEXT_COLOR = 0xFFFFFFFF.toInt()
    }
}

private data class VisibleText(
    val text: String,
    val startIndex: Int,
    val cursorOffset: Int,
)

private data class TextSelection(
    val start: Int,
    val endExclusive: Int,
)

internal fun textFieldTextY(fieldY: Int, fieldHeight: Int, lineHeight: Int): Int =
    fieldY + (fieldHeight - lineHeight + 2) / 2

internal fun textFieldCursorOffsetAt(text: String, clickX: Int, width: (String) -> Int): Int {
    if (clickX <= 0) return 0
    var previousWidth = 0
    text.indices.forEach { index ->
        val nextWidth = width(text.substring(0, index + 1))
        if (clickX * 2 < previousWidth + nextWidth) return index
        previousWidth = nextWidth
    }
    return text.length
}

internal fun textAfterDeletingPreviousWord(text: String): String {
    val wordEnd = text.indexOfLast { !it.isWhitespace() } + 1
    if (wordEnd == 0) return ""
    val wordStart = text.substring(0, wordEnd).indexOfLast(Char::isWhitespace) + 1
    return text.substring(0, wordStart)
}
