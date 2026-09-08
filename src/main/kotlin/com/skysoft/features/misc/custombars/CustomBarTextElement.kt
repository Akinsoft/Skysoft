package com.skysoft.features.misc.custombars

import com.skysoft.config.SkysoftConfigGui
import com.skysoft.config.core.HudPosition
import com.skysoft.data.SkyBlockIsland
import com.skysoft.features.misc.SkyBlockLevelBar
import com.skysoft.gui.BottomHudLayout
import com.skysoft.gui.HudEditorElement
import com.skysoft.gui.HudTransform
import com.skysoft.utils.ColorUtilities.toColor
import com.skysoft.utils.NumberUtilities.addSeparators
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
        val barScale = part.position().effectiveScale
        val barWidth = (part.width * barScale).roundToInt()
        val barX = part.layoutOffsetX() + part.position().getAbsX0AllowingOverflow(barWidth)
        val trackCenter = ((part.trackX + part.trackWidth / 2f) * barScale).roundToInt()
        return barX + trackCenter - width / 2 + position.x
    }

    override fun absoluteY(height: Int): Int =
        barY(includeBottomLayout = true) + (RESOURCE_TEXT_Y * part.position().effectiveScale).roundToInt() + position.y

    override fun renderEditor(context: GuiGraphicsExtractor) {
        val color = if (part == CustomBarPart.EXPERIENCE) {
            SkyBlockLevelBar.displayedExperienceLevelColor(part.visualDetails.textColor.get().toColor().rgb)
        } else {
            part.visualDetails.textColor.get().toColor().rgb
        }
        drawCustomBarText(context, text(), 0, 0, color)
    }

    override fun applyEditorDrag(deltaX: Int, deltaY: Int): InputHandlingResult {
        position.moveBy(deltaX, deltaY)
        return InputHandlingResult.CONSUMED
    }

    override fun applyEditorScroll(scrollY: Double): InputHandlingResult {
        position.scale += if (scrollY > 0.0) TEXT_SCALE_STEP else -TEXT_SCALE_STEP
        return InputHandlingResult.CONSUMED
    }

    override fun editorDetailsLines(): List<String> = listOf(
        "§7Offset x: §e${position.x}§7, y: §e${position.y}§7, scale: §e${
            "%.2f".format(java.util.Locale.US, position.scale)
        }",
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
        val scaledWidth = (width() * position.effectiveScale).roundToInt()
        val x = absoluteX(scaledWidth)
        val y = barY(includeBottomLayout = false) +
            (RESOURCE_TEXT_Y * part.position().effectiveScale).roundToInt() +
            position.y
        HudTransform(x, y, position.effectiveScale).render(context) {
            renderEditor(context)
        }
    }

    private fun barY(includeBottomLayout: Boolean): Int {
        val barHeight = (part.height * part.position().effectiveScale).roundToInt()
        val y = part.position().getAbsY0AllowingOverflow(barHeight)
        return if (includeBottomLayout) y - BottomHudLayout.reservedHeight() else y
    }

    private fun text(): String = when (part) {
        CustomBarPart.HEALTH -> if (inRift) RiftCustomBarValues.formatHearts(CustomBarState.displayedHealth()) else resourceText(
            CustomBarState.health,
            part.trackWidth,
        )
        CustomBarPart.MANA -> resourceText(CustomBarState.mana, part.trackWidth)
        CustomBarPart.VITALITY -> resourceText(CustomBarState.vitality, part.trackWidth)
        CustomBarPart.EXPERIENCE -> if (inRift && !SkyBlockLevelBar.isReplacingExperience) {
            RiftCustomBarValues.formatTime(Minecraft.getInstance().player?.experienceLevel ?: 0)
        } else {
            SkyBlockLevelBar.displayedExperienceLevel(
                Minecraft.getInstance().player?.experienceLevel ?: 0,
            ).toString()
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

private val config get() = SkysoftConfigGui.config().gui.customBars
private val inRift get() = SkyBlockIsland.THE_RIFT.isInIsland()

private const val RESOURCE_TEXT_Y = 1
private const val TEXT_PADDING = 2
private const val TEXT_SCALE_STEP = 0.1f
