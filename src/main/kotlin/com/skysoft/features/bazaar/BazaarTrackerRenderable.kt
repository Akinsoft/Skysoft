package com.skysoft.features.bazaar

import com.skysoft.gui.OverlayControlArea
import com.skysoft.gui.OverlayControlTooltips
import com.skysoft.utils.gui.OverlayPanelStyle
import com.skysoft.utils.gui.Rect
import com.skysoft.utils.render.LegacyTextRenderer
import com.skysoft.utils.renderables.GuiRenderable
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import java.util.Locale

internal data class LineSegment(val text: String, val action: TrackerControl? = null)

internal data class DisplayLine(val tag: String?, val tagColor: Int, val segments: List<LineSegment>) {
    val text: String get() = segments.joinToString(separator = "") { it.text }

    companion object {
        fun text(text: String) = DisplayLine(null, 0, listOf(LineSegment(text)))
        fun segments(vararg segments: LineSegment) = DisplayLine(null, 0, segments.toList())
    }
}

private fun trackerControlTooltip(action: TrackerControl): List<String> = when (action) {
    TrackerControl.TOGGLE_MODE -> displayModeTooltip()
    TrackerControl.RESET -> listOf(
        "§7Clicking this will reset your ${resetTooltipModeText()}§7.",
    )
}

private fun displayModeTooltip(): List<String> {
    val selectedIndex = displayModeCycle.indexOf(BazaarDisplayState.mode)
    check(selectedIndex in displayModeCycle.indices) { "Display mode is missing from its tooltip cycle" }
    return OverlayControlTooltips.cycle("Display Mode", displayModeCycle.map { it.displayName }, selectedIndex)
}

private fun resetTooltipModeText(): String = when (BazaarDisplayState.mode) {
    TrackerDisplayMode.SESSION -> "§eSession's §7Bazaar Tracker data"
    TrackerDisplayMode.TOTAL -> "§eTotal §7Bazaar Tracker data"
}

internal class BazaarTrackerRenderable(
    private val lines: List<DisplayLine>,
    private val background: Boolean,
) : GuiRenderable {
    private val font = Minecraft.getInstance().font
    private val lineHeight = font.lineHeight + 3
    private val padding = if (background) OverlayPanelStyle.PADDING else 0

    override val width: Int = (lines.maxOfOrNull { lineWidth(it) } ?: 0) + padding * 2
    override val height: Int = lines.size * lineHeight + padding * 2

    override fun render(context: GuiGraphicsExtractor) {
        render(context, null, null)
    }

    fun render(context: GuiGraphicsExtractor, mouseX: Int?, mouseY: Int?): OverlayControlArea<TrackerControl>? {
        if (background) {
            OverlayPanelStyle.draw(context, 0, 0, width, height)
        }
        var hovered: OverlayControlArea<TrackerControl>? = null
        var y = padding
        for (line in lines) {
            hovered = renderLine(context, line, padding, y, mouseX, mouseY) ?: hovered
            y += lineHeight
        }
        return hovered
    }

    private fun renderLine(
        context: GuiGraphicsExtractor,
        line: DisplayLine,
        x: Int,
        y: Int,
        mouseX: Int?,
        mouseY: Int?,
    ): OverlayControlArea<TrackerControl>? {
        var drawX = x
        val tag = line.tag
        if (tag != null) {
            val tagText = tag.uppercase(Locale.US)
            val tagWidth = LegacyTextRenderer.width("§l$tagText") + TAG_BACKGROUND_EXTRA_WIDTH
            context.fill(x, y - 1, x + tagWidth, y + font.lineHeight + 1, line.tagColor)
            LegacyTextRenderer.draw(
                context,
                "§f§l$tagText",
                x + TAG_TEXT_X_OFFSET,
                y,
                defaultColor = WHITE_TEXT_COLOR,
            )
            drawX += tagWidth + TAG_TRAILING_GAP
        }
        var hovered: OverlayControlArea<TrackerControl>? = null
        for (segment in line.segments) {
            val width = LegacyTextRenderer.width(segment.text)
            segment.action?.let {
                val area = controlArea(it, drawX, y, width)
                if (mouseX != null && mouseY != null && area.contains(mouseX, mouseY)) hovered = area
            }
            LegacyTextRenderer.draw(context, segment.text, drawX, y, defaultColor = WHITE_TEXT_COLOR)
            drawX += width
        }
        return hovered
    }

    private fun controlArea(action: TrackerControl, x: Int, y: Int, width: Int) = OverlayControlArea(
        action = action,
        bounds = Rect(
            x = x - CONTROL_HIT_PADDING_X,
            y = y - CONTROL_HIT_PADDING_Y,
            width = width + CONTROL_HIT_PADDING_X * 2,
            height = font.lineHeight + CONTROL_HIT_PADDING_Y * 2,
        ),
        tooltipLines = trackerControlTooltip(action),
    )

    private fun lineWidth(line: DisplayLine): Int = tagOffset(line) + LegacyTextRenderer.width(line.text)

    private fun tagOffset(line: DisplayLine): Int {
        val tag = line.tag ?: return 0
        return LegacyTextRenderer.width("§l${tag.uppercase(Locale.US)}") + TAG_OFFSET_EXTRA_WIDTH
    }
}

private const val CONTROL_HIT_PADDING_X = 2
private const val CONTROL_HIT_PADDING_Y = 3
private const val TAG_BACKGROUND_EXTRA_WIDTH = 6
private const val TAG_TEXT_X_OFFSET = 3
private const val TAG_TRAILING_GAP = 4
private const val TAG_OFFSET_EXTRA_WIDTH = TAG_BACKGROUND_EXTRA_WIDTH + TAG_TRAILING_GAP
private val WHITE_TEXT_COLOR = 0xFFFFFFFF.toInt()
