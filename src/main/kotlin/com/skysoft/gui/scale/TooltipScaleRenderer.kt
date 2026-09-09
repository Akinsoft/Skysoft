package com.skysoft.gui.scale

import com.skysoft.utils.MinecraftClient
import com.skysoft.utils.SkysoftErrorBoundary
import com.skysoft.utils.renderables.withIsolatedPose
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import java.util.function.BiConsumer

internal object TooltipScaleRenderer {
    @JvmStatic
    fun render(context: GuiGraphicsExtractor, x: Int, y: Int, render: BiConsumer<Int, Int>) {
        val position = TooltipPosition(x, y)
        SkysoftErrorBoundary.aroundUnit(
            "Tooltip GUI scale rendering",
            position,
            { point -> render.accept(point.x, point.y) },
        ) { renderTooltip ->
            renderAtScale(context, position, renderTooltip)
        }
    }

    private fun renderAtScale(
        context: GuiGraphicsExtractor,
        position: TooltipPosition,
        render: (TooltipPosition) -> Unit,
    ) {
        val minecraft = Minecraft.getInstance()
        val screen = MinecraftClient.screen(minecraft)
        if (!GuiScaleController.usesSeparateTooltipScale(screen)) {
            render(position)
            return
        }
        val window = minecraft.window
        val tooltipScale = GuiScaleController.resolve(screen, window).tooltip()
        if (window.guiScale == tooltipScale) {
            render(position)
            return
        }
        val activeScale = window.guiScale.coerceAtLeast(1)
        val scaledPosition = TooltipPosition(
            GuiScaleController.convertCoordinate(position.x, activeScale, tooltipScale),
            GuiScaleController.convertCoordinate(position.y, activeScale, tooltipScale),
        )
        val poseScale = tooltipScale / activeScale.toFloat()
        context.withIsolatedPose {
            GuiScaleController.useTooltipScale(screen, window).use {
                context.pose().scale(poseScale, poseScale)
                render(scaledPosition)
            }
        }
    }

    private data class TooltipPosition(val x: Int, val y: Int)
}
