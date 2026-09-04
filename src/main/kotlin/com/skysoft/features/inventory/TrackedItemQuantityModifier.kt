package com.skysoft.features.inventory

import com.skysoft.utils.ColorUtilities.RGB_MASK
import com.skysoft.utils.ColorUtilities.withScaledAlpha
import com.skysoft.utils.gui.OverlayPanelStyle
import com.skysoft.utils.gui.Rect
import com.skysoft.utils.gui.TextFieldState
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import org.lwjgl.glfw.GLFW

internal sealed interface TrackedItemQuantityAction {
    data class Modify(val amount: Long) : TrackedItemQuantityAction
    data class BeginCustom(val direction: Int) : TrackedItemQuantityAction
    data class Field(val localMouseX: Int, val bounds: Rect) : TrackedItemQuantityAction
}

internal data class TrackedItemQuantityControl(
    val action: TrackedItemQuantityAction,
    val bounds: Rect,
    val tooltipLines: List<String> = emptyList(),
)

internal class TrackedItemQuantityModifier {
    private val field = TextFieldState(maxLength = QUANTITY_MAXIMUM_LENGTH)
    private var direction = 0

    val height: Int
        get() = if (direction == 0) QUANTITY_ROW_HEIGHT else QUANTITY_FIELD_HEIGHT

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
            GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> field.text.toLongOrNull()
                ?.takeIf { it > 0L }
                ?.let { amount ->
                    modify(amount * direction)
                    cancel()
                }
            GLFW.GLFW_KEY_ESCAPE -> cancel()
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
    ): TrackedItemQuantityControl? {
        val font = Minecraft.getInstance().font
        val text = label()
        val textX = panelX + OverlayPanelStyle.PADDING
        val fieldBounds = if (direction == 0) null else {
            Rect(textX + font.width(text) + QUANTITY_FIELD_GAP, y, QUANTITY_FIELD_WIDTH, height)
        }
        val hovered = if (fieldBounds != null) {
            TrackedItemQuantityControl(
                TrackedItemQuantityAction.Field(mouseX, fieldBounds),
                fieldBounds,
                listOf("§7Press Enter to confirm. Escape to cancel."),
            ).takeIf { interactive && fieldBounds.contains(mouseX, mouseY) }
        } else {
            quantityButtons(textX, y, font).firstOrNull { control ->
                interactive && control.bounds.contains(mouseX, mouseY)
            }
        }
        hovered?.bounds?.let { bounds ->
            context.fill(
                bounds.x,
                bounds.y,
                bounds.x + bounds.width,
                bounds.y + bounds.height,
                QUANTITY_HOVER.withScaledAlpha(opacity),
            )
        }
        context.text(
            font,
            text,
            textX,
            y + (height - QUANTITY_TEXT_HEIGHT) / 2,
            QUANTITY_TEXT_COLOR.withScaledAlpha(opacity),
            false,
        )
        fieldBounds?.let { bounds ->
            field.render(
                context,
                bounds.x,
                bounds.y,
                bounds.width,
                bounds.height,
                "Quantity...",
                alpha = opacity,
                textColor = if (direction < 0) QUANTITY_REMOVE_COLOR else QUANTITY_ADD_COLOR,
            )
        }
        return hovered
    }

    private fun label(): MutableComponent {
        if (direction != 0) {
            val color = if (direction < 0) QUANTITY_REMOVE_COLOR else QUANTITY_ADD_COLOR
            return quantityText("Modify ", QUANTITY_MUTED_COLOR).append(
                quantityText(if (direction < 0) "-" else "+", color),
            )
        }
        return quantityText("Modify ", QUANTITY_MUTED_COLOR).also { text ->
            MODIFY_ITEM_AMOUNTS.forEachIndexed { index, amount ->
                if (index > 0) text.append(" ")
                val buttonDirection = if (index < MODIFY_MINUS_BUTTON_COUNT) -1 else 1
                text.append(
                    quantityText(
                        "[${amount?.let { if (it > 0) "+$it" else it.toString() } ?: if (buttonDirection < 0) "-" else "+"}]",
                        if (buttonDirection < 0) QUANTITY_REMOVE_COLOR else QUANTITY_ADD_COLOR,
                    ),
                )
            }
        }
    }

    private fun quantityButtons(
        textX: Int,
        y: Int,
        font: net.minecraft.client.gui.Font,
    ): List<TrackedItemQuantityControl> {
        var offset = font.width(quantityText("Modify ", QUANTITY_MUTED_COLOR))
        return MODIFY_ITEM_AMOUNTS.mapIndexed { index, amount ->
            if (index > 0) offset += font.width(" ")
            val buttonDirection = if (index < MODIFY_MINUS_BUTTON_COUNT) -1 else 1
            val label = "[${amount?.let { if (it > 0) "+$it" else it.toString() } ?: if (buttonDirection < 0) "-" else "+"}]"
            val width = font.width(label)
            TrackedItemQuantityControl(
                amount?.let(TrackedItemQuantityAction::Modify)
                    ?: TrackedItemQuantityAction.BeginCustom(buttonDirection),
                Rect(textX + offset, y, width, height),
            ).also { offset += width }
        }
    }
}

private fun quantityText(text: String, color: Int): MutableComponent =
    Component.literal(text).withStyle { style -> style.withColor(color and RGB_MASK) }

private val MODIFY_ITEM_AMOUNTS = listOf<Long?>(null, -4, -1, 1, 4, null)
private const val MODIFY_MINUS_BUTTON_COUNT = 3
private const val QUANTITY_MAXIMUM_LENGTH = 19
private const val QUANTITY_FIELD_WIDTH = 80
private const val QUANTITY_FIELD_HEIGHT = 18
private const val QUANTITY_FIELD_GAP = 3
private const val QUANTITY_ROW_HEIGHT = 11
private const val QUANTITY_TEXT_HEIGHT = 9
private const val QUANTITY_TEXT_COLOR = 0xFFFFFFFF.toInt()
private const val QUANTITY_MUTED_COLOR = 0xFFAAAAAA.toInt()
private const val QUANTITY_ADD_COLOR = 0xFF55FF55.toInt()
private const val QUANTITY_REMOVE_COLOR = 0xFFFF5555.toInt()
private const val QUANTITY_HOVER = 0x28FFFFFF
