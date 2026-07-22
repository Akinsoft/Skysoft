package com.skysoft.features.misc.bettertab

import com.skysoft.config.SkysoftConfigGui
import com.skysoft.data.hypixel.HypixelLocationState
import com.skysoft.data.hypixel.TabListApi
import com.skysoft.utils.SkysoftErrorBoundary
import com.skysoft.utils.TabListOverlay
import com.skysoft.utils.gui.OverlayPanelStyle
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.PlayerFaceExtractor
import net.minecraft.network.chat.Component
import java.util.UUID
import kotlin.math.min
import kotlin.math.roundToInt

object BetterTab {
    private const val MAXIMUM_COLUMN_ROWS = 22
    private const val LINE_HEIGHT = 9
    private const val COLUMN_GAP = 8
    private const val CONTENT_GAP = 3
    private const val HEAD_SIZE = 8
    private const val HEAD_GAP = 1
    private const val SCREEN_MARGIN = 5
    private const val TOP_MARGIN = 7
    private const val TEXT_COLOR = 0xFFFFFFFF.toInt()

    private var cachedSessionId = Long.MIN_VALUE
    private var cachedContentVersion = Long.MIN_VALUE
    private var cachedPlayerHeadsShown = false
    private var cachedSecondPlayerColumnHidden = false
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
    }

    private fun isActive(): Boolean =
        SkysoftConfigGui.config().gui.betterTab.isEnabled && HypixelLocationState.inSkyBlock

    private fun render(context: GuiGraphicsExtractor) {
        val minecraft = Minecraft.getInstance()
        val isVisible = minecraft.options.keyPlayerList.isDown
        TabListOverlay.setVisible(minecraft, isVisible)
        if (!isVisible || !TabListApi.isLoaded) return
        val layout = currentLayout(minecraft) ?: return
        context.nextStratum()
        drawLayout(context, minecraft, layout)
    }

    private fun currentLayout(minecraft: Minecraft): MeasuredLayout? {
        val settings = SkysoftConfigGui.config().gui.betterTab.settings
        val arePlayerHeadsShown = settings.arePlayerHeadsShown
        val isSecondPlayerColumnHidden = settings.isSecondPlayerColumnHidden
        if (
            cachedSessionId != TabListApi.sessionId ||
            cachedContentVersion != TabListApi.contentVersion ||
            cachedPlayerHeadsShown != arePlayerHeadsShown ||
            cachedSecondPlayerColumnHidden != isSecondPlayerColumnHidden
        ) {
            cachedSessionId = TabListApi.sessionId
            cachedContentVersion = TabListApi.contentVersion
            cachedPlayerHeadsShown = arePlayerHeadsShown
            cachedSecondPlayerColumnHidden = isSecondPlayerColumnHidden
            cachedLayout = measureLayout(
                minecraft,
                BetterTabLayoutBuilder.build(
                    entries = TabListApi.entries,
                    header = TabListApi.header,
                    footer = TabListApi.footer,
                    maximumRows = MAXIMUM_COLUMN_ROWS,
                    isSecondPlayerColumnHidden = isSecondPlayerColumnHidden,
                ),
                arePlayerHeadsShown,
            )
        }
        return cachedLayout
    }

    private fun measureLayout(
        minecraft: Minecraft,
        layout: BetterTabLayout,
        arePlayerHeadsShown: Boolean,
    ): MeasuredLayout? {
        if (layout.columns.isEmpty()) return null
        val font = minecraft.font
        val columnWidths = layout.columns.map { column ->
            column.rows.maxOfOrNull { row ->
                font.width(row.component) + if (arePlayerHeadsShown && row.playerId != null) HEAD_SIZE + HEAD_GAP else 0
            } ?: 0
        }
        val columnsWidth = columnWidths.sum() + COLUMN_GAP * (columnWidths.size - 1)
        val headerWidth = layout.headerLines.maxOfOrNull(font::width) ?: 0
        val footerWidth = layout.footerLines.maxOfOrNull(font::width) ?: 0
        val contentWidth = maxOf(columnsWidth, headerWidth, footerWidth)
        val columnsHeight = layout.columns.maxOfOrNull { it.rows.size }?.times(LINE_HEIGHT) ?: 0
        val headerHeight = layout.headerLines.size * LINE_HEIGHT
        val footerHeight = layout.footerLines.size * LINE_HEIGHT
        val contentHeight = headerHeight + columnsHeight + footerHeight +
            gapBetween(headerHeight, columnsHeight) + gapBetween(columnsHeight, footerHeight)
        return MeasuredLayout(
            layout = layout,
            columnWidths = columnWidths,
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

    private fun drawLayout(context: GuiGraphicsExtractor, minecraft: Minecraft, layout: MeasuredLayout) {
        val scale = min(
            1f,
            min(
                (context.guiWidth() - SCREEN_MARGIN * 2).coerceAtLeast(1) / layout.panelWidth.toFloat(),
                (context.guiHeight() - TOP_MARGIN - SCREEN_MARGIN).coerceAtLeast(1) / layout.panelHeight.toFloat(),
            ),
        )
        val virtualGuiWidth = context.guiWidth() / scale
        val panelX = ((virtualGuiWidth - layout.panelWidth) / 2f).roundToInt()
        val panelY = (TOP_MARGIN / scale).roundToInt()
        context.pose().pushMatrix()
        try {
            context.pose().scale(scale, scale)
            OverlayPanelStyle.draw(context, panelX, panelY, layout.panelWidth, layout.panelHeight)
            drawContent(context, minecraft, layout, panelX, panelY)
        } finally {
            context.pose().popMatrix()
        }
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
        drawColumns(context, minecraft, layout, contentX, y)
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
    ) {
        var x = contentX + (layout.contentWidth - layout.columnsWidth) / 2
        for ((index, column) in layout.layout.columns.withIndex()) {
            val width = layout.columnWidths[index]
            var rowY = y
            for (row in column.rows) {
                drawRow(context, minecraft, row, x, rowY, width, layout.arePlayerHeadsShown)
                rowY += LINE_HEIGHT
            }
            x += width + COLUMN_GAP
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
            row.playerId != null &&
            drawPlayerHead(context, minecraft, row.playerId, x, y) == PlayerHeadRenderResult.DRAWN
        ) {
            textX += HEAD_SIZE + HEAD_GAP
        }
        context.text(minecraft.font, row.component, textX, y, TEXT_COLOR, false)
    }

    private fun drawPlayerHead(
        context: GuiGraphicsExtractor,
        minecraft: Minecraft,
        playerId: UUID,
        x: Int,
        y: Int,
    ): PlayerHeadRenderResult {
        val playerInfo = minecraft.connection?.getPlayerInfo(playerId) ?: return PlayerHeadRenderResult.UNAVAILABLE
        PlayerFaceExtractor.extractRenderState(
            context,
            playerInfo.skin.body().texturePath(),
            x,
            y,
            HEAD_SIZE,
            playerInfo.showHat(),
            false,
            TEXT_COLOR,
        )
        return PlayerHeadRenderResult.DRAWN
    }

    private enum class PlayerHeadRenderResult {
        DRAWN,
        UNAVAILABLE,
    }

    private data class MeasuredLayout(
        val layout: BetterTabLayout,
        val columnWidths: List<Int>,
        val columnsWidth: Int,
        val columnsHeight: Int,
        val contentWidth: Int,
        val contentHeight: Int,
        val panelWidth: Int,
        val panelHeight: Int,
        val arePlayerHeadsShown: Boolean,
    )
}
