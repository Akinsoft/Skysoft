package com.skysoft.features.misc

import com.skysoft.config.SkysoftConfigGui
import com.skysoft.gui.HudEditorElement
import com.skysoft.gui.HudEditorRegistry
import com.skysoft.gui.SkysoftHudEditor
import com.skysoft.utils.MinecraftClient
import com.skysoft.utils.input.InputHandlingResult
import java.util.Locale
import kotlin.math.roundToInt
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.numbers.StyledFormat
import net.minecraft.world.scores.Objective
import net.minecraft.world.scores.PlayerScoreEntry
import net.minecraft.world.scores.PlayerTeam

object ScoreboardPositionEditor {
    private val config get() = SkysoftConfigGui.config().gui.positionEditor
    private var isRenderingEditorPreview = false

    fun register() {
        HudEditorRegistry.register(object : HudEditorElement {
            override val id: String = "scoreboard"
            override val label: String = "Scoreboard"
            override val position get() = config.scoreboardPosition
            override val hasEditorBackground: Boolean = false

            override fun width(): Int = currentLayout()?.width ?: 0

            override fun height(): Int = currentLayout()?.height ?: 0

            override fun isVisible(): Boolean =
                !MinecraftClient.isGuiHidden(Minecraft.getInstance()) && VanillaScoreboardHud.currentObjective() != null

            override fun absoluteX(width: Int): Int = if (config.isScoreboardPositionCustomized) {
                super.absoluteX(width)
            } else {
                currentLayout()?.left ?: 0
            }

            override fun absoluteY(height: Int): Int = if (config.isScoreboardPositionCustomized) {
                super.absoluteY(height)
            } else {
                currentLayout()?.top ?: 0
            }

            override fun renderDummy(context: GuiGraphicsExtractor) {
                val objective = VanillaScoreboardHud.currentObjective() ?: return
                isRenderingEditorPreview = true
                try {
                    VanillaScoreboardHud.render(context, objective)
                } finally {
                    isRenderingEditorPreview = false
                }
            }

            override fun applyEditorDrag(deltaX: Int, deltaY: Int): InputHandlingResult {
                if (config.isScoreboardPositionCustomized) return InputHandlingResult.IGNORED
                val layout = currentLayout() ?: return InputHandlingResult.CONSUMED
                config.isScoreboardPositionCustomized = true
                position.moveToAbsoluteAllowingOverflow(
                    layout.left + deltaX,
                    layout.top + deltaY,
                    layout.width,
                    layout.height,
                )
                return InputHandlingResult.CONSUMED
            }

            override fun applyEditorScroll(scrollY: Double): InputHandlingResult {
                if (!config.isScoreboardPositionCustomized) materializeVanillaPosition()
                return InputHandlingResult.IGNORED
            }

            override fun resetEditorState() {
                position.resetToDefault()
                config.isScoreboardPositionCustomized = false
            }

            override fun editorTooltipLines(): List<String> = buildList {
                if (config.isScoreboardPositionCustomized) {
                    add(
                        "§7x: §e${position.x}§7, y: §e${position.y}§7, scale: §e${
                            "%.2f".format(Locale.US, position.scale)
                        }",
                    )
                } else {
                    add("§7Position: §eVanilla§7, scale: §e1.00")
                }
                add("§eLeft-click drag §7to move")
                add("§eRight-click §7to open settings")
                add("§eScroll-Wheel §7to resize")
                add("§eR §7to reset")
            }

            override fun openConfig() = SkysoftConfigGui.open("Position Editor")
        })
    }

    fun render(
        context: GuiGraphicsExtractor,
        objective: Objective,
        drawVanillaScoreboard: () -> Unit,
    ) {
        if (MinecraftClient.screen() is SkysoftHudEditor.EditorScreen && !isRenderingEditorPreview) return
        if (!config.isScoreboardPositionCustomized && !isRenderingEditorPreview) {
            drawVanillaScoreboard()
            return
        }

        val layout = layout(context, objective)
        context.pose().pushMatrix()
        try {
            if (isRenderingEditorPreview) {
                context.pose().translate(-layout.left.toFloat(), -layout.top.toFloat())
            } else {
                val scaledWidth = (layout.width * positionScale()).roundToInt()
                val scaledHeight = (layout.height * positionScale()).roundToInt()
                val x = config.scoreboardPosition.getAbsX0AllowingOverflow(scaledWidth)
                val y = config.scoreboardPosition.getAbsY0AllowingOverflow(scaledHeight)
                context.pose().translate(x.toFloat(), y.toFloat())
                context.pose().scale(positionScale(), positionScale())
                context.pose().translate(-layout.left.toFloat(), -layout.top.toFloat())
            }
            drawVanillaScoreboard()
        } finally {
            context.pose().popMatrix()
        }
    }

    private fun materializeVanillaPosition() {
        val layout = currentLayout() ?: return
        config.scoreboardPosition.moveToAbsoluteAllowingOverflow(
            layout.left,
            layout.top,
            layout.width,
            layout.height,
        )
        config.isScoreboardPositionCustomized = true
    }

    private fun currentLayout(): ScoreboardLayout? {
        val objective = VanillaScoreboardHud.currentObjective() ?: return null
        val minecraft = Minecraft.getInstance()
        return layout(
            minecraft.window.guiScaledWidth,
            minecraft.window.guiScaledHeight,
            objective,
        )
    }

    private fun layout(context: GuiGraphicsExtractor, objective: Objective): ScoreboardLayout =
        layout(context.guiWidth(), context.guiHeight(), objective)

    private fun layout(screenWidth: Int, screenHeight: Int, objective: Objective): ScoreboardLayout {
        val font = Minecraft.getInstance().font
        val scoreboard = objective.scoreboard
        val numberFormat = objective.numberFormatOrDefault(StyledFormat.SIDEBAR_DEFAULT)
        val entries = scoreboard.listPlayerScores(objective)
            .asSequence()
            .filterNot { it.isHidden }
            .sortedWith(SCORE_DISPLAY_ORDER)
            .take(MAX_ENTRIES)
            .toList()
        val spacerWidth = font.width(": ")
        val contentWidth = entries.fold(font.width(objective.displayName)) { widest, entry ->
            val team = scoreboard.getPlayersTeam(entry.owner())
            val nameWidth = font.width(PlayerTeam.formatNameForTeam(team, entry.ownerName()))
            val scoreWidth = font.width(entry.formatValue(numberFormat))
            maxOf(widest, nameWidth + if (scoreWidth > 0) spacerWidth + scoreWidth else 0)
        }
        val entriesHeight = entries.size * font.lineHeight
        val bottom = screenHeight / 2 + entriesHeight / VERTICAL_OFFSET_DIVISOR
        return ScoreboardLayout(
            left = screenWidth - contentWidth - HORIZONTAL_INSET,
            top = bottom - entriesHeight - HEADER_HEIGHT,
            width = contentWidth + BACKGROUND_EXTRA_WIDTH,
            height = entriesHeight + HEADER_HEIGHT,
        )
    }

    private fun positionScale(): Float = config.scoreboardPosition.scale
}

private data class ScoreboardLayout(
    val left: Int,
    val top: Int,
    val width: Int,
    val height: Int,
)

private val SCORE_DISPLAY_ORDER = compareByDescending<PlayerScoreEntry> { it.value() }
    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.owner() }
private const val MAX_ENTRIES = 15
private const val HORIZONTAL_INSET = 5
private const val BACKGROUND_EXTRA_WIDTH = 4
private const val HEADER_HEIGHT = 10
private const val VERTICAL_OFFSET_DIVISOR = 3
