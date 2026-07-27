package com.skysoft.gui

import com.skysoft.gui.tooltip.TooltipViewportExcludedPositioner
import com.skysoft.utils.gui.Rect
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner
import org.joml.Vector2i
import org.joml.Vector2ic

internal class HudEditorHelpPositioner(
    private val avoid: Rect?,
) : ClientTooltipPositioner, TooltipViewportExcludedPositioner {
    override fun positionTooltip(
        screenWidth: Int,
        screenHeight: Int,
        x: Int,
        y: Int,
        tooltipWidth: Int,
        tooltipHeight: Int,
    ): Vector2ic {
        val right = (screenWidth - tooltipWidth - SCREEN_MARGIN).coerceAtLeast(SCREEN_MARGIN)
        val bottom = (screenHeight - tooltipHeight - SCREEN_MARGIN).coerceAtLeast(SCREEN_MARGIN)
        val candidates = listOf(
            Rect(SCREEN_MARGIN, SCREEN_MARGIN, tooltipWidth, tooltipHeight),
            Rect(right, SCREEN_MARGIN, tooltipWidth, tooltipHeight),
            Rect(SCREEN_MARGIN, bottom, tooltipWidth, tooltipHeight),
            Rect(right, bottom, tooltipWidth, tooltipHeight),
        )
        val blocked = avoid?.let {
            Rect(it.x - ELEMENT_GAP, it.y - ELEMENT_GAP, it.width + ELEMENT_GAP * 2, it.height + ELEMENT_GAP * 2)
        }
        val position = candidates.firstOrNull { blocked?.intersects(it) != true } ?: candidates.first()
        return Vector2i(position.x, position.y)
    }

    private companion object {
        const val SCREEN_MARGIN = 6
        const val ELEMENT_GAP = 4
    }
}
