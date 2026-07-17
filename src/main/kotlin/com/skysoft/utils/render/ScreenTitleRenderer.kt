package com.skysoft.utils.render

import com.skysoft.SkysoftMod
import com.skysoft.config.SkysoftConfigGui
import com.skysoft.gui.GuiOverlay
import com.skysoft.gui.GuiOverlayContextType
import com.skysoft.gui.GuiOverlayLayer
import com.skysoft.gui.GuiOverlayRegistry
import com.skysoft.gui.HudEditorElement
import com.skysoft.gui.HudEditorRegistry
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component
import kotlin.math.roundToInt

object ScreenTitleRenderer {
    private val positionReferenceLines = listOf(
        ScreenTitleLine(Component.literal("Skysoft Title"), 2f),
        ScreenTitleLine(Component.literal("Title details"), 1.2f),
    )
    private val position get() = SkysoftConfigGui.config().gui.positionEditor.titlePosition
    private val renderTitlesInFront get() = SkysoftConfigGui.config().gui.areTitlesRenderedInFront
    private var lastRenderedTitle: RenderedTitle? = null
    private var editorTitle: List<ScreenTitleLine>? = null

    internal fun registerTitleOverlay(
        id: String,
        visible: () -> Boolean,
        render: (GuiGraphicsExtractor) -> Unit,
    ) {
        HudElementRegistry.attachElementAfter(
            VanillaHudElements.TITLE_AND_SUBTITLE,
            SkysoftMod.id(id),
            { context, _ ->
                if (!renderTitlesInFront && visible()) render(context)
            },
        )
        GuiOverlayRegistry.register(
            GuiOverlay(
                id = id,
                layer = GuiOverlayLayer.ABOVE_SCREEN,
                contexts = GuiOverlayContextType.entries.toSet(),
                visible = { renderTitlesInFront && visible() },
                render = { context, _ -> render(context) },
            ),
        )
    }

    fun beginPositionEditing(nowNanos: Long = System.nanoTime()) {
        editorTitle = lastRenderedTitle
            ?.takeIf { nowNanos - it.renderedAtNanos <= TITLE_VISIBILITY_GRACE_NANOS }
            ?.lines
    }

    fun endPositionEditing() {
        editorTitle = null
    }

    fun registerPositionEditor() {
        HudEditorRegistry.register(object : HudEditorElement {
            override val id: String = "skysoft_titles"
            override val label: String = "Skysoft Titles"
            override val position get() = ScreenTitleRenderer.position
            override fun width(): Int = editorTitle?.let(::linesWidth) ?: 0
            override fun height(): Int = editorTitle?.totalHeight() ?: 0
            override fun isVisible(): Boolean = editorTitle != null
            override fun renderDummy(context: GuiGraphicsExtractor) {
                val lines = editorTitle ?: return
                drawLinesAt(context, lines, width() / 2f, height() / 2f)
            }
            override fun openConfig() = SkysoftConfigGui.open("Position Editor")
        })
    }

    fun draw(
        context: GuiGraphicsExtractor,
        title: Component,
        scale: Float,
        yOffset: Float,
        color: Int = 0xFFFFFFFF.toInt(),
        shadow: Boolean = true,
    ) {
        recordRenderedTitle(listOf(ScreenTitleLine(title, scale, color)))
        val font = Minecraft.getInstance().font
        withTitlePosition(context) {
            context.pose().translate(0f, yOffset - DEFAULT_TITLE_Y_OFFSET)
            context.pose().scale(scale, scale)
            context.text(font, title, -font.width(title) / 2, 0, color, shadow)
        }
    }

    fun drawLines(
        context: GuiGraphicsExtractor,
        lines: List<ScreenTitleLine>,
        yOffset: Float,
        shadow: Boolean = true,
    ) {
        if (lines.isEmpty()) return
        recordRenderedTitle(lines)
        withTitlePosition(context) {
            drawLinesAt(context, lines, 0f, yOffset - DEFAULT_TITLE_Y_OFFSET, shadow)
        }
    }

    private fun drawLinesAt(
        context: GuiGraphicsExtractor,
        lines: List<ScreenTitleLine>,
        centerX: Float,
        centerY: Float,
        shadow: Boolean = true,
    ) {
        val font = Minecraft.getInstance().font
        var currentY = centerY - lines.totalHeight() / 2f
        lines.forEach { line ->
            context.pose().pushMatrix()
            context.pose().translate(centerX, currentY)
            context.pose().scale(line.scale, line.scale)
            context.text(font, line.component, -font.width(line.component) / 2, 0, line.color, shadow)
            context.pose().popMatrix()
            currentY += line.height
        }
    }

    private fun linesWidth(lines: List<ScreenTitleLine>): Int {
        val font = Minecraft.getInstance().font
        return lines.maxOfOrNull { (font.width(it.component) * it.scale).roundToInt() } ?: 0
    }

    private inline fun withTitlePosition(context: GuiGraphicsExtractor, draw: () -> Unit) {
        val previewWidth = linesWidth(positionReferenceLines)
        val previewHeight = positionReferenceLines.totalHeight()
        val scaledWidth = (previewWidth * position.scale).roundToInt()
        val scaledHeight = (previewHeight * position.scale).roundToInt()
        val centerX = position.getAbsX0AllowingOverflow(scaledWidth) + scaledWidth / 2f
        val centerY = position.getAbsY0AllowingOverflow(scaledHeight) + scaledHeight / 2f
        context.pose().pushMatrix()
        context.pose().translate(centerX, centerY)
        context.pose().scale(position.scale, position.scale)
        draw()
        context.pose().popMatrix()
    }

    private fun recordRenderedTitle(lines: List<ScreenTitleLine>) {
        lastRenderedTitle = RenderedTitle(lines.toList(), System.nanoTime())
    }
}

private data class RenderedTitle(
    val lines: List<ScreenTitleLine>,
    val renderedAtNanos: Long,
)

data class ScreenTitleLine(
    val component: Component,
    val scale: Float,
    val color: Int = 0xFFFFFFFF.toInt(),
) {
    val height: Int = (10 * scale).toInt()
}

internal fun List<ScreenTitleLine>.totalHeight(): Int = sumOf { line -> line.height }

internal const val DEFAULT_TITLE_Y_OFFSET = -82f
private const val TITLE_VISIBILITY_GRACE_NANOS = 250_000_000L
