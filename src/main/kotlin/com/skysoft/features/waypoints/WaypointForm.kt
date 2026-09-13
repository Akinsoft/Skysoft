package com.skysoft.features.waypoints

import com.skysoft.utils.ColorUtilities.withScaledAlpha
import com.skysoft.utils.gui.PixelControlColors
import com.skysoft.utils.gui.Rect
import com.skysoft.utils.gui.TextFieldState
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor

internal class WaypointForm {
    private val fields = mutableListOf<TextFieldState>()
    private val bounds = mutableMapOf<TextFieldState, Rect>()
    val focused: TextFieldState? get() = fields.firstOrNull { it.focused }
    val isEmpty: Boolean get() = fields.isEmpty()

    fun field(text: String, maxLength: Int = MAX_WAYPOINT_NAME): TextFieldState =
        TextFieldState(text, maxLength).also(fields::add)

    fun beginFrame() = bounds.clear()

    fun draw(
        context: GuiGraphicsExtractor,
        opacity: Double,
        field: TextFieldState,
        label: String,
        bounds: Rect,
        showLabel: Boolean = true,
    ) {
        if (showLabel) context.text(
            Minecraft.getInstance().font, label, bounds.x, bounds.y, PixelControlColors.MUTED_TEXT.withScaledAlpha(opacity), false,
        )
        val input = Rect(bounds.x, bounds.y + if (showLabel) LABEL_HEIGHT else 0, bounds.width, FIELD_HEIGHT)
        field.render(context, input.x, input.y, input.width, input.height, if (showLabel) "" else label, alpha = opacity)
        this.bounds[field] = input
    }

    fun didClick(x: Int, y: Int, viewport: Rect): Boolean {
        val clicked = bounds.entries.firstOrNull { viewport.contains(x, y) && it.value.contains(x, y) }
        fields.forEach { it.focused = it === clicked?.key }
        clicked?.key?.placeCursorAt(x, clicked.value.x, clicked.value.width)
        return clicked != null
    }

    fun focusNext(backwards: Boolean, viewport: Rect): Int {
        val index = fields.indexOfFirst { it.focused }
        fields.forEach { it.focused = false }
        val next = if (backwards) index.coerceAtLeast(0) - 1 else index + 1
        val target = fields[Math.floorMod(next, fields.size)]
        target.focused = true
        val bounds = bounds[target] ?: return 0
        return when {
            bounds.y < viewport.y -> bounds.y - viewport.y - LABEL_HEIGHT
            bounds.y + bounds.height > viewport.y + viewport.height -> bounds.y + bounds.height - viewport.y - viewport.height
            else -> 0
        }
    }

    companion object {
        const val FIELD_HEIGHT = 18
        const val LABEL_HEIGHT = 12
    }
}
