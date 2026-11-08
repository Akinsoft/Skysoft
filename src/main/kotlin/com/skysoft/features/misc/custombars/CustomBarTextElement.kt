package com.skysoft.features.misc.custombars

import com.skysoft.config.SkysoftConfigGui
import com.skysoft.config.core.HudPosition
import com.skysoft.features.misc.SkyBlockLevelBar
import com.skysoft.gui.BottomHudLayout
import com.skysoft.gui.HudEditorElement
import com.skysoft.gui.HudTransform
import com.skysoft.utils.ColorUtilities.toColor
import com.skysoft.utils.NumberUtilities.addSeparators
import com.skysoft.utils.NumberUtilities.fixedDecimals
import com.skysoft.utils.NumberUtilities.shortFormat
import com.skysoft.utils.input.InputHandlingResult
import kotlin.math.roundToInt
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor

internal class CustomBarTextElement(private val part: CustomBarPart) : HudEditorElement {
    override val id: String = "custom_bars_${part.name.lowercase()}_text"
    override val label: String = "${part.label} Text"
    override val position: HudPosition get() = part.textPosition()
    override val snapGroup: String = "custom_bars_${part.name.lowercase()}"
    override val canMove: Boolean = false
    override val canScale: Boolean = false
    override val hasEditorBackground: Boolean = false

    override fun width(): Int = Minecraft.getInstance().font.width(text())
    override fun height(): Int = Minecraft.getInstance().font.lineHeight
    override fun isVisible(): Boolean = CustomBars.isHudVisible() && part.isNumberVisible()

    override fun absoluteX(width: Int): Int {
        val barScale = part.position().scale
        val barWidth = (part.width * barScale).roundToInt()
        val barX = part.layoutOffsetX() + part.position().getAbsX0AllowingOverflow(barWidth)
        val trackCenter = ((part.trackX + part.trackWidth / 2f) * barScale).roundToInt()
        return barX + trackCenter - width / 2 + position.x
    }

    override fun absoluteY(height: Int): Int = textY(includeBottomLayout = true)

    override fun renderEditor(context: GuiGraphicsExtractor) {
        val configured = part.visualDetails.textColor.get().toColor().rgb
        val color = if (part == CustomBarPart.EXPERIENCE) SkyBlockLevelBar.displayedExperienceLevelColor(configured) else configured
        drawCustomBarText(context, text(), 0, 0, color)
    }

    override fun applyEditorDrag(deltaX: Int, deltaY: Int): InputHandlingResult {
        position.moveBy(deltaX, deltaY)
        return InputHandlingResult.CONSUMED
    }

    override fun applyEditorScroll(scrollY: Double): InputHandlingResult {
        position.scaleByScroll(scrollY)
        return InputHandlingResult.CONSUMED
    }

    override fun editorDetailsLines(): List<String> = listOf(
        "§7Offset x: §e${position.x}§7, y: §e${position.y}§7, scale: §e${position.scale.fixedDecimals(2)}",
    )

    override fun editorActionLines(): List<String> = listOf(
        "§eLeft-click drag §7to move",
        "§eScroll-Wheel §7to resize",
        "§eHold Shift §7to snap",
        "§eRight-click §7to open settings",
        "§eR §7to reset",
    )

    override fun openConfig() = SkysoftConfigGui.open("Custom Bars")

    fun renderLive(context: GuiGraphicsExtractor) {
        val scaledWidth = (width() * position.scale).roundToInt()
        HudTransform(absoluteX(scaledWidth), textY(includeBottomLayout = false), position.scale).render(context) {
            renderEditor(context)
        }
    }

    private fun textY(includeBottomLayout: Boolean): Int {
        val barScale = part.position().scale
        val barY = part.position().getAbsY0AllowingOverflow((part.height * barScale).roundToInt())
        val layoutOffset = if (includeBottomLayout) BottomHudLayout.reservedHeight() else 0
        return barY - layoutOffset + (RESOURCE_TEXT_Y * barScale).roundToInt() + position.y
    }

    private fun text(): String = when (part) {
        CustomBarPart.HEALTH -> if (inRift) {
            RiftCustomBarValues.formatHearts(CustomBarState.displayedHealth())
        } else {
            resourceText(CustomBarState.health, part.trackWidth)
        }
        CustomBarPart.MANA -> resourceText(CustomBarState.mana, part.trackWidth)
        CustomBarPart.VITALITY -> resourceText(CustomBarState.vitality, part.trackWidth)
        CustomBarPart.EXPERIENCE -> {
            val level = Minecraft.getInstance().player?.experienceLevel ?: 0
            if (showsRiftTime) {
                RiftCustomBarValues.formatTime(level)
            } else {
                SkyBlockLevelBar.displayedExperienceLevel(level).toString()
            }
        }
        CustomBarPart.DEFENSE, CustomBarPart.SPEED, CustomBarPart.AIR -> error("${part.label} has no bar text")
    }
}

private fun resourceText(value: BarValue?, width: Int): String {
    if (value == null) return "---/---"
    val exact = "${value.displayedCurrent.addSeparators()}/${value.maximum.addSeparators()}"
    if (Minecraft.getInstance().font.width(exact) <= width - TEXT_PADDING * 2) return exact
    return "${value.displayedCurrent.toLong().shortFormat()}/${value.maximum.toLong().shortFormat()}"
}

internal fun drawCustomBarText(context: GuiGraphicsExtractor, text: String, x: Int, y: Int, color: Int) {
    val font = Minecraft.getInstance().font
    if (config.details.textOutline) {
        val outlineColor = config.details.textOutlineColor.get().toColor().rgb
        context.text(font, text, x + 1, y, outlineColor, false)
        context.text(font, text, x - 1, y, outlineColor, false)
        context.text(font, text, x, y + 1, outlineColor, false)
        context.text(font, text, x, y - 1, outlineColor, false)
    }
    context.text(font, text, x, y, color, false)
}

private const val RESOURCE_TEXT_Y = 1
private const val TEXT_PADDING = 2
