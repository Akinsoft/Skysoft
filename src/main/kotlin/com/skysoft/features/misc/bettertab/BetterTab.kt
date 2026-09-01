package com.skysoft.features.misc.bettertab

import com.skysoft.config.BETTER_TAB_DEFAULT_TOP_MARGIN
import com.skysoft.config.SkysoftConfigGui
import com.skysoft.data.hypixel.HypixelLocationState
import com.skysoft.data.hypixel.TabListApi
import com.skysoft.gui.HudEditorElement
import com.skysoft.gui.HudEditorRegistry
import com.skysoft.gui.SkysoftHudEditor
import com.skysoft.utils.ColorUtilities.toColor
import com.skysoft.utils.MinecraftClient
import com.skysoft.utils.SkysoftErrorBoundary
import com.skysoft.utils.TabListOverlay
import com.skysoft.utils.gui.OverlayPanelStyle
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.PlayerFaceExtractor
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import kotlin.math.min
import kotlin.math.roundToInt

object BetterTab {
    private const val MAXIMUM_COLUMN_ROWS = 22
    private const val LINE_HEIGHT = 9
    private const val COLUMN_GAP = 8
    private const val COLUMN_PANEL_GAP = 4
    private const val COLUMN_FRAME_INSET = 2
    private const val MIN_COLUMN_PADDING = 0
    private const val MAX_COLUMN_PADDING = 10
    private const val CONTENT_GAP = 3
    private const val HEAD_SIZE = 8
    private const val HEAD_GAP = 1
    private const val SCREEN_MARGIN = 5
    private const val TOOLTIP_TEXTURE_MARGIN = 4
    private const val TEXT_COLOR = 0xFFFFFFFF.toInt()
    private val tooltipFrameSprite = Identifier.fromNamespaceAndPath(
        "hypixel_skyblock",
        "tooltip/common_frame",
    )

    private val config get() = SkysoftConfigGui.config().gui.betterTab
    private var cachedLayoutKey: LayoutCacheKey? = null
    private var cachedLayout: MeasuredLayout? = null

    fun register() {
        TabListApi.registerConsumer("Better TAB", ::isActive)
        HudElementRegistry.replaceElement(VanillaHudElements.PLAYER_LIST) { vanilla ->
            HudElement { context, tick ->
                if (isActive()) {
                    SkysoftErrorBoundary.run("Better TAB render") { render(context) }
                } else {
                    vanilla.extractRenderState(context, tick)
                }
            }
        }
        HudEditorRegistry.register(object : HudEditorElement {
            override val id: String = "better_tab"
            override val label: String = "Better TAB"
            override val position get() = config.position
            override val hasEditorBackground: Boolean = false

            override fun width(): Int = currentLayout(Minecraft.getInstance())?.let(::editorWidth) ?: 0

            override fun height(): Int = currentLayout(Minecraft.getInstance())?.let(::editorHeight) ?: 0

            override fun isVisible(): Boolean {
                val minecraft = Minecraft.getInstance()
                return isActive() && minecraft.options.keyPlayerList.isDown && currentLayout(minecraft) != null
            }

            override fun renderEditor(context: GuiGraphicsExtractor) {
                val minecraft = Minecraft.getInstance()
                val layout = currentLayout(minecraft) ?: return
                context.pose().pushMatrix()
                try {
                    context.pose().scale(defaultScale(layout), defaultScale(layout))
                    drawLayout(context, minecraft, layout, 0, 0)
                } finally {
                    context.pose().popMatrix()
                }
            }

            override fun openConfig() = SkysoftConfigGui.open("Better TAB")
        })
    }

    private fun isActive(): Boolean = config.isEnabled && HypixelLocationState.inSkyBlock

    private fun render(context: GuiGraphicsExtractor) {
        val minecraft = Minecraft.getInstance()
        val isVisible = minecraft.options.keyPlayerList.isDown
        TabListOverlay.setVisible(minecraft, isVisible)
        if (
            !isVisible ||
            !TabListApi.isLoaded ||
            MinecraftClient.screen(minecraft) is SkysoftHudEditor.EditorScreen
        ) return
        val layout = currentLayout(minecraft) ?: return
        val scale = defaultScale(layout) * config.position.scale
        val width = (layout.panelWidth * scale).roundToInt()
        val height = (layout.panelHeight * scale).roundToInt()
        val x = config.position.getAbsX0AllowingOverflow(width)
        val y = config.position.getAbsY0AllowingOverflow(height)
        context.nextStratum()
        context.pose().pushMatrix()
        try {
            context.pose().translate(x.toFloat(), y.toFloat())
            context.pose().scale(scale, scale)
            drawLayout(context, minecraft, layout, 0, 0)
        } finally {
            context.pose().popMatrix()
        }
    }

    private fun currentLayout(minecraft: Minecraft): MeasuredLayout? {
        val settings = config.settings
        val details = config.details
        val key = LayoutCacheKey(
            sessionId = TabListApi.sessionId,
            contentVersion = TabListApi.contentVersion,
            arePlayerHeadsShown = settings.arePlayerHeadsShown,
            isServerAddressHidden = settings.isServerAddressHidden,
            isStoreBannerHidden = settings.isStoreBannerHidden,
            isSecondPlayerColumnHidden = settings.isSecondPlayerColumnHidden,
            areFramesShown = details.frames,
            areColumnPanelsShown = details.columnPanels,
            columnPadding = details.columnPadding.coerceIn(MIN_COLUMN_PADDING, MAX_COLUMN_PADDING),
        )
        if (cachedLayoutKey != key) {
            cachedLayoutKey = key
            cachedLayout = measureLayout(
                minecraft,
                BetterTabLayoutBuilder.build(
                    entries = TabListApi.entries,
                    header = TabListApi.header,
                    footer = TabListApi.footer,
                    maximumRows = MAXIMUM_COLUMN_ROWS,
                    isServerAddressHidden = key.isServerAddressHidden,
                    isStoreBannerHidden = key.isStoreBannerHidden,
                    isSecondPlayerColumnHidden = key.isSecondPlayerColumnHidden,
                ),
                key.arePlayerHeadsShown,
                key.areColumnPanelsShown,
                key.areFramesShown,
                key.columnPadding,
            )
        }
        return cachedLayout
    }

    private fun measureLayout(
        minecraft: Minecraft,
        layout: BetterTabLayout,
        arePlayerHeadsShown: Boolean,
        areColumnPanelsShown: Boolean,
        areFramesShown: Boolean,
        columnPadding: Int,
    ): MeasuredLayout? {
        if (layout.columns.isEmpty()) return null
        val font = minecraft.font
        val columnWidths = layout.columns.map { column ->
            column.rows.maxOfOrNull { row ->
                font.width(row.component) + if (arePlayerHeadsShown && row.playerName != null) HEAD_SIZE + HEAD_GAP else 0
            } ?: 0
        }
        val columnInset = if (areColumnPanelsShown) {
            columnPadding + if (areFramesShown) COLUMN_FRAME_INSET else 0
        } else {
            0
        }
        val columnGap = if (areColumnPanelsShown) COLUMN_PANEL_GAP else COLUMN_GAP
        val columnsWidth = columnWidths.sum() + columnInset * 2 * columnWidths.size +
            columnGap * (columnWidths.size - 1)
        val headerWidth = layout.headerLines.maxOfOrNull(font::width) ?: 0
        val footerWidth = layout.footerLines.maxOfOrNull(font::width) ?: 0
        val contentWidth = maxOf(columnsWidth, headerWidth, footerWidth)
        val rowsHeight = layout.columns.maxOfOrNull { it.rows.size }?.times(LINE_HEIGHT) ?: 0
        val columnsHeight = rowsHeight + columnInset * 2
        val headerHeight = layout.headerLines.size * LINE_HEIGHT
        val footerHeight = layout.footerLines.size * LINE_HEIGHT
        val contentHeight = headerHeight + columnsHeight + footerHeight +
            gapBetween(headerHeight, columnsHeight) + gapBetween(columnsHeight, footerHeight)
        return MeasuredLayout(
            layout = layout,
            columnWidths = columnWidths,
            columnInset = columnInset,
            columnGap = columnGap,
            columnsWidth = columnsWidth,
            columnsHeight = columnsHeight,
            contentWidth = contentWidth,
            contentHeight = contentHeight,
            panelWidth = contentWidth + OverlayPanelStyle.PADDING * 2,
            panelHeight = contentHeight + OverlayPanelStyle.PADDING * 2,
            arePlayerHeadsShown = arePlayerHeadsShown,
        )
    }

    private fun gapBetween(firstHeight: Int, secondHeight: Int): Int =
        if (firstHeight > 0 && secondHeight > 0) CONTENT_GAP else 0

    private fun defaultScale(layout: MeasuredLayout): Float {
        val window = Minecraft.getInstance().window
        return min(
            1f,
            min(
                (window.guiScaledWidth - SCREEN_MARGIN * 2).coerceAtLeast(1) / layout.panelWidth.toFloat(),
                (window.guiScaledHeight - BETTER_TAB_DEFAULT_TOP_MARGIN - SCREEN_MARGIN).coerceAtLeast(1) /
                    layout.panelHeight.toFloat(),
            ),
        )
    }

    private fun editorWidth(layout: MeasuredLayout): Int = (layout.panelWidth * defaultScale(layout)).roundToInt()

    private fun editorHeight(layout: MeasuredLayout): Int = (layout.panelHeight * defaultScale(layout)).roundToInt()

    private fun drawLayout(
        context: GuiGraphicsExtractor,
        minecraft: Minecraft,
        layout: MeasuredLayout,
        panelX: Int,
        panelY: Int,
    ) {
        drawTooltipPanel(
            context,
            panelX,
            panelY,
            layout.panelWidth,
            layout.panelHeight,
            config.details.backgroundColor.get().toColor().rgb,
        )
        drawContent(context, minecraft, layout, panelX, panelY)
    }

    private fun drawTooltipPanel(
        context: GuiGraphicsExtractor,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        color: Int,
    ) {
        context.fill(x, y, x + width, y + height, color)
        if (!config.details.frames) return
        context.blitSprite(
            RenderPipelines.GUI_TEXTURED,
            tooltipFrameSprite,
            x - TOOLTIP_TEXTURE_MARGIN,
            y - TOOLTIP_TEXTURE_MARGIN,
            width + TOOLTIP_TEXTURE_MARGIN * 2,
            height + TOOLTIP_TEXTURE_MARGIN * 2,
            config.details.frameColor.get().toColor().rgb,
        )
    }

    private fun drawContent(
        context: GuiGraphicsExtractor,
        minecraft: Minecraft,
        layout: MeasuredLayout,
        panelX: Int,
        panelY: Int,
    ) {
        val contentX = panelX + OverlayPanelStyle.PADDING
        var y = panelY + OverlayPanelStyle.PADDING
        y = drawCenteredLines(context, minecraft, layout.layout.headerLines, contentX, y, layout.contentWidth)
        if (layout.layout.headerLines.isNotEmpty()) y += CONTENT_GAP
        drawColumns(
            context,
            minecraft,
            layout,
            contentX,
            y,
            config.details.columnPanelColor.get().toColor().rgb.takeIf { config.details.columnPanels },
        )
        y += layout.columnsHeight
        if (layout.layout.footerLines.isNotEmpty()) {
            if (layout.columnsHeight > 0) y += CONTENT_GAP
            drawCenteredLines(context, minecraft, layout.layout.footerLines, contentX, y, layout.contentWidth)
        }
    }

    private fun drawCenteredLines(
        context: GuiGraphicsExtractor,
        minecraft: Minecraft,
        lines: List<Component>,
        x: Int,
        startY: Int,
        width: Int,
    ): Int {
        var y = startY
        for (line in lines) {
            context.text(minecraft.font, line, x + (width - minecraft.font.width(line)) / 2, y, TEXT_COLOR, false)
            y += LINE_HEIGHT
        }
        return y
    }

    private fun drawColumns(
        context: GuiGraphicsExtractor,
        minecraft: Minecraft,
        layout: MeasuredLayout,
        contentX: Int,
        y: Int,
        panelColor: Int?,
    ) {
        var panelX = contentX + (layout.contentWidth - layout.columnsWidth) / 2
        for ((index, column) in layout.layout.columns.withIndex()) {
            val width = layout.columnWidths[index]
            if (panelColor != null) {
                drawTooltipPanel(
                    context,
                    panelX,
                    y,
                    width + layout.columnInset * 2,
                    layout.columnsHeight,
                    panelColor,
                )
            }
            val textX = panelX + layout.columnInset
            var rowY = y + layout.columnInset
            for (row in column.rows) {
                drawRow(context, minecraft, row, textX, rowY, width, layout.arePlayerHeadsShown)
                rowY += LINE_HEIGHT
            }
            panelX += width + layout.columnInset * 2 + layout.columnGap
        }
    }

    private fun drawRow(
        context: GuiGraphicsExtractor,
        minecraft: Minecraft,
        row: BetterTabRow,
        x: Int,
        y: Int,
        width: Int,
        arePlayerHeadsShown: Boolean,
    ) {
        if (row.component.string.isEmpty()) return
        if (row.isTitle) {
            context.text(minecraft.font, row.component, x + (width - minecraft.font.width(row.component)) / 2, y, TEXT_COLOR, false)
            return
        }
        var textX = x
        if (
            arePlayerHeadsShown &&
            row.playerName != null &&
            drawPlayerHead(context, row.playerName, x, y) == PlayerHeadRenderResult.DRAWN
        ) {
            textX += HEAD_SIZE + HEAD_GAP
        }
        context.text(minecraft.font, row.component, textX, y, TEXT_COLOR, false)
    }

    private fun drawPlayerHead(
        context: GuiGraphicsExtractor,
        playerName: String,
        x: Int,
        y: Int,
    ): PlayerHeadRenderResult {
        val playerProfile = TabListApi.playerProfile(playerName) ?: return PlayerHeadRenderResult.UNAVAILABLE
        PlayerFaceExtractor.extractRenderState(
            context,
            playerProfile.skin().body().texturePath(),
            x,
            y,
            HEAD_SIZE,
            playerProfile.showHat,
            false,
            TEXT_COLOR,
        )
        return PlayerHeadRenderResult.DRAWN
    }

    private enum class PlayerHeadRenderResult {
        DRAWN,
        UNAVAILABLE,
    }

    private data class LayoutCacheKey(
        val sessionId: Long,
        val contentVersion: Long,
        val arePlayerHeadsShown: Boolean,
        val isServerAddressHidden: Boolean,
        val isStoreBannerHidden: Boolean,
        val isSecondPlayerColumnHidden: Boolean,
        val areFramesShown: Boolean,
        val areColumnPanelsShown: Boolean,
        val columnPadding: Int,
    )

    private data class MeasuredLayout(
        val layout: BetterTabLayout,
        val columnWidths: List<Int>,
        val columnInset: Int,
        val columnGap: Int,
        val columnsWidth: Int,
        val columnsHeight: Int,
        val contentWidth: Int,
        val contentHeight: Int,
        val panelWidth: Int,
        val panelHeight: Int,
        val arePlayerHeadsShown: Boolean,
    )
}
