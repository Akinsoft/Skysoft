package com.skysoft.features.misc

import com.skysoft.config.SkysoftConfigGui
import com.skysoft.features.misc.bettertab.BetterTab
import com.skysoft.gui.HudEditorElement
import com.skysoft.gui.HudEditorRegistry
import com.skysoft.gui.SkysoftHudEditor
import com.skysoft.gui.transform
import com.skysoft.mixin.PlayerTabOverlayAccessor
import com.skysoft.utils.MinecraftClient
import com.skysoft.utils.TabListOverlay
import com.skysoft.utils.renderables.withIsolatedPose
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.numbers.StyledFormat
import net.minecraft.world.scores.DisplaySlot
import net.minecraft.world.scores.ScoreHolder
import net.minecraft.world.scores.criteria.ObjectiveCriteria

object TabListPositionEditor {
    private val position get() = SkysoftConfigGui.config().gui.positionEditor.tabListPosition
    private var isRenderingEditor = false

    fun register() {
        HudEditorRegistry.register(object : HudEditorElement {
            override val id = "tab_list"
            override val label = "Tab List"
            override val position get() = TabListPositionEditor.position
            override val hasEditorBackground = false

            override fun width(): Int = currentLayout()?.width ?: 0

            override fun height(): Int = currentLayout()?.height ?: 0

            override fun isVisible(): Boolean =
                !BetterTab.isActive() && TabListPositionEditor.isVisible() && currentLayout() != null

            override fun renderEditor(context: GuiGraphicsExtractor) {
                val minecraft = Minecraft.getInstance()
                val scoreboard = minecraft.level?.scoreboard ?: return
                isRenderingEditor = true
                try {
                    TabListOverlay.overlay(minecraft).extractRenderState(
                        context,
                        minecraft.window.guiScaledWidth,
                        scoreboard,
                        scoreboard.getDisplayObjective(DisplaySlot.LIST),
                    )
                } finally {
                    isRenderingEditor = false
                }
            }

            override fun openConfig() = SkysoftConfigGui.open("Position Editor")
        })
    }

    fun isVisible(): Boolean {
        val minecraft = Minecraft.getInstance()
        if (MinecraftClient.isGuiHidden(minecraft)) return false
        return (MinecraftClient.screen(minecraft) as? SkysoftHudEditor.EditorScreen)?.isTabListVisible
            ?: minecraft.options.keyPlayerList.isDown
    }

    fun render(context: GuiGraphicsExtractor, drawVanilla: () -> Unit) {
        if (MinecraftClient.screen() is SkysoftHudEditor.EditorScreen && !isRenderingEditor) return
        val layout = currentLayout() ?: return
        val drawAtOrigin = {
            context.pose().translate(-layout.left.toFloat(), -TOP.toFloat())
            drawVanilla()
        }
        if (isRenderingEditor) {
            context.withIsolatedPose { drawAtOrigin() }
        } else {
            position.transform(layout.width, layout.height).render(context) { drawAtOrigin() }
        }
    }

    private fun currentLayout(): TabLayout? {
        val minecraft = Minecraft.getInstance()
        val scoreboard = minecraft.level?.scoreboard ?: return null
        if (minecraft.player == null || minecraft.connection == null) return null
        val overlay = TabListOverlay.overlay(minecraft)
        val players = (overlay as PlayerTabOverlayAccessor).skysoftGetPlayerInfos()
        val font = minecraft.font
        val objective = scoreboard.getDisplayObjective(DisplaySlot.LIST)
        val nameWidth = players.maxOfOrNull { font.width(overlay.getNameForDisplay(it)) } ?: 0
        val scoreWidth = when {
            objective == null -> 0
            objective.renderType == ObjectiveCriteria.RenderType.HEARTS -> HEARTS_WIDTH
            else -> players.maxOfOrNull { player ->
                val score = scoreboard.getPlayerScoreInfo(ScoreHolder.fromGameProfile(player.profile), objective)
                val width = score?.let {
                    font.width(it.formatValue(objective.numberFormatOrDefault(StyledFormat.PLAYER_LIST_DEFAULT)))
                } ?: 0
                if (width > 0) font.width(" ") + width else 0
            } ?: 0
        }
        var columns = 1
        var rows = players.size
        while (rows > MAX_ROWS) {
            columns++
            rows = (players.size + columns - 1) / columns
        }
        val screenWidth = minecraft.window.guiScaledWidth
        val headWidth = if (TabListOverlay.areHeadsVisible(minecraft)) HEAD_WIDTH else 0
        val slotWidth = minOf(columns * (headWidth + nameWidth + scoreWidth + PING_WIDTH), screenWidth - WIDTH_MARGIN) / columns
        val header = TabListOverlay.readHeader(minecraft)?.let { font.split(it, screenWidth - WIDTH_MARGIN) }
        val footer = TabListOverlay.readFooter(minecraft)?.let { font.split(it, screenWidth - WIDTH_MARGIN) }
        val lineWidth = maxOf(
            slotWidth * columns + (columns - 1) * COLUMN_GAP,
            header?.maxOfOrNull(font::width) ?: 0,
            footer?.maxOfOrNull(font::width) ?: 0,
        )
        return TabLayout(
            left = screenWidth / 2 - lineWidth / 2 - 1,
            width = lineWidth / 2 * 2 + 2,
            height = rows * LINE_HEIGHT + 1 +
                (header?.let { it.size * LINE_HEIGHT + 1 } ?: 0) +
                (footer?.let { it.size * LINE_HEIGHT + 1 } ?: 0),
        )
    }
}

private data class TabLayout(val left: Int, val width: Int, val height: Int)

private const val TOP = 9
private const val MAX_ROWS = 20
private const val LINE_HEIGHT = 9
private const val HEARTS_WIDTH = 90
private const val HEAD_WIDTH = 9
private const val PING_WIDTH = 13
private const val WIDTH_MARGIN = 50
private const val COLUMN_GAP = 5
