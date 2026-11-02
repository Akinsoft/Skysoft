package com.skysoft.features.inventory

import com.mojang.blaze3d.platform.InputConstants
import com.skysoft.features.inventory.TrackedItemPanelStyle.ACTION_COLOR
import com.skysoft.features.inventory.TrackedItemPanelStyle.DANGER_COLOR
import com.skysoft.features.inventory.TrackedItemPanelStyle.MUTED_COLOR
import com.skysoft.features.inventory.TrackedItemPanelStyle.ROW_HEIGHT
import com.skysoft.features.inventory.TrackedItemPanelStyle.TEXT_HEIGHT
import com.skysoft.features.inventory.TrackedItemPanelStyle.styledText
import com.skysoft.gui.OverlayControlArea
import com.skysoft.utils.gui.OverlayPanelStyle
import com.skysoft.utils.gui.Rect
import com.skysoft.utils.gui.TextFieldState
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.network.chat.MutableComponent

internal sealed interface TrackedItemQuantityAction {
    data class Modify(val amount: Long) : TrackedItemQuantityAction
    data class BeginCustom(val direction: Int) : TrackedItemQuantityAction
    data class Field(val localMouseX: Int, val bounds: Rect) : TrackedItemQuantityAction
}

internal class TrackedItemQuantityModifier {
    private val field = TextFieldState(maxLength = QUANTITY_MAXIMUM_LENGTH)
    private var direction = 0

    val height: Int
        get() = if (direction == 0) ROW_HEIGHT else QUANTITY_FIELD_HEIGHT

    fun width(): Int {
        val font = Minecraft.getInstance().font
        val text = label()
        return font.width(text) + if (direction == 0) 0 else QUANTITY_FIELD_GAP + QUANTITY_FIELD_WIDTH
    }

    fun begin(nextDirection: Int) {
        require(nextDirection == -1 || nextDirection == 1)
        direction = nextDirection
        field.text = ""
        field.focused = true
    }

    fun focus(localMouseX: Int, bounds: Rect) {
        field.focused = true
        field.placeCursorAt(localMouseX, bounds.x, bounds.width)
    }

    fun cancel() {
        direction = 0
        field.focused = false
    }

    fun wasKeyPressHandled(event: KeyEvent, modify: (Long) -> Unit): Boolean {
        if (direction == 0 || !field.focused) return false
        when (event.key()) {
            InputConstants.KEY_RETURN, InputConstants.KEY_NUMPADENTER -> field.text.toLongOrNull()
                ?.takeIf { it > 0L }
                ?.let { amount ->
                    modify(amount * direction)
                    cancel()
                }
            InputConstants.KEY_ESCAPE -> cancel()
            else -> {
                field.keyPressed(event)
                field.text = field.text.filter { it in '0'..'9' }
            }
        }
        return true
    }

    fun wasCharTypedHandled(event: CharacterEvent): Boolean {
        if (direction == 0 || !field.focused) return false
        if (event.codepointAsString().singleOrNull() in '0'..'9') field.charTyped(event)
        return true
    }

    fun render(
        context: GuiGraphicsExtractor,
        panelX: Int,
        y: Int,
        mouseX: Int,
        mouseY: Int,
        opacity: Double,
        interactive: Boolean,
    ): OverlayControlArea<TrackedItemQuantityAction>? {
        val font = Minecraft.getInstance().font
        val text = label()
        val textX = panelX + OverlayPanelStyle.PADDING
        val fieldBounds = if (direction == 0) null else {
            Rect(textX + font.width(text) + QUANTITY_FIELD_GAP, y, QUANTITY_FIELD_WIDTH, height)
        }
        val hovered = if (fieldBounds != null) {
            OverlayControlArea<TrackedItemQuantityAction>(
                TrackedItemQuantityAction.Field(mouseX, fieldBounds),
                fieldBounds,
                listOf("§7Press Enter to confirm. Escape to cancel."),
            ).takeIf { interactive && fieldBounds.contains(mouseX, mouseY) }
        } else {
            quantityButtons(textX, y, font).firstOrNull { control ->
                interactive && control.bounds.contains(mouseX, mouseY)
            }
        }
        hovered?.bounds?.let { bounds -> TrackedItemPanelStyle.drawHover(context, bounds, opacity) }
        TrackedItemPanelStyle.drawText(context, text, textX, y + (height - TEXT_HEIGHT) / 2, opacity)
        fieldBounds?.let { bounds ->
            field.render(
                context,
                bounds.x,
                bounds.y,
                bounds.width,
                bounds.height,
                "Quantity...",
                alpha = opacity,
                textColor = directionColor(direction),
            )
        }
        return hovered
    }

    private fun label(): MutableComponent {
        val text = styledText(MODIFY_PREFIX, MUTED_COLOR)
        if (direction != 0) return text.append(styledText(directionSign(direction), directionColor(direction)))
        MODIFY_ITEM_AMOUNTS.forEachIndexed { index, amount ->
            if (index > 0) text.append(" ")
            text.append(styledText(modifyButtonLabel(index, amount), directionColor(modifyButtonDirection(index))))
        }
        return text
    }

    private fun quantityButtons(textX: Int, y: Int, font: Font): List<OverlayControlArea<TrackedItemQuantityAction>> {
        var offset = font.width(styledText(MODIFY_PREFIX, MUTED_COLOR))
        return MODIFY_ITEM_AMOUNTS.mapIndexed { index, amount ->
            if (index > 0) offset += font.width(" ")
            val width = font.width(modifyButtonLabel(index, amount))
            OverlayControlArea<TrackedItemQuantityAction>(
                amount?.let(TrackedItemQuantityAction::Modify)
                    ?: TrackedItemQuantityAction.BeginCustom(modifyButtonDirection(index)),
                Rect(textX + offset, y, width, height),
            ).also { offset += width }
        }
    }
}

private fun modifyButtonDirection(index: Int): Int = if (index < MODIFY_MINUS_BUTTON_COUNT) -1 else 1

private fun modifyButtonLabel(index: Int, amount: Long?): String {
    val value = amount?.let { if (it > 0) "+$it" else it.toString() } ?: directionSign(modifyButtonDirection(index))
    return "[$value]"
}

private fun directionSign(direction: Int): String = if (direction < 0) "-" else "+"

private fun directionColor(direction: Int): Int = if (direction < 0) DANGER_COLOR else ACTION_COLOR

private const val MODIFY_PREFIX = "Modify "
private val MODIFY_ITEM_AMOUNTS = listOf<Long?>(null, -4, -1, 1, 4, null)
private const val MODIFY_MINUS_BUTTON_COUNT = 3
private const val QUANTITY_MAXIMUM_LENGTH = 19
private const val QUANTITY_FIELD_WIDTH = 80
private const val QUANTITY_FIELD_HEIGHT = 18
private const val QUANTITY_FIELD_GAP = 3
