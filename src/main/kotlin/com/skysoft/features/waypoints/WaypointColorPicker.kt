package com.skysoft.features.waypoints

import com.skysoft.utils.ColorUtilities.RGB_MASK
import com.skysoft.utils.ColorUtilities.toChromaColor
import com.skysoft.utils.MinecraftClient
import com.skysoft.utils.gui.OverlayTextStyle
import com.skysoft.utils.gui.Rect
import com.skysoft.utils.input.InputUtilities
import io.github.notenoughupdates.moulconfig.ChromaColour
import io.github.notenoughupdates.moulconfig.GuiTextures
import io.github.notenoughupdates.moulconfig.gui.GuiContext
import io.github.notenoughupdates.moulconfig.gui.GuiImmediateContext
import io.github.notenoughupdates.moulconfig.gui.KeyboardEvent
import io.github.notenoughupdates.moulconfig.gui.MouseEvent
import io.github.notenoughupdates.moulconfig.gui.component.ColorSelectComponent
import io.github.notenoughupdates.moulconfig.platform.MoulConfigPlatform
import io.github.notenoughupdates.moulconfig.platform.MoulConfigRenderContext
import java.awt.Color
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import org.lwjgl.glfw.GLFW

internal class WaypointColorPicker private constructor(
    private val group: WaypointGroup,
    private val original: WaypointPoint,
    private val index: Int,
    parent: Screen?,
) : WaypointDialog("${original.label(index)} Color", parent, primaryLabel = null) {
    private var draft = original
    private var picker = createPicker()
    private var guiContext = GuiContext(picker)
    private var pickerBounds = Rect(0, 0, picker.width, picker.height)
    override val preferredWidth: Int get() = picker.width + PANEL_INSET * 2
    override val contentHeight: Int get() = picker.height

    private fun createPicker(): ColorSelectComponent {
        val color = draft.customColor ?: Color(group.defaultPointColor(index)).toChromaColor()
        return ColorSelectComponent(
            0, 0, color.toLegacyString(),
            { value ->
                val selected = ChromaColour.forLegacyString(value)
                draft = draft.copy(
                    color = Color.HSBtoRGB(selected.hue, selected.saturation, selected.brightness) and RGB_MASK,
                    chromaMillis = selected.timeForFullRotationInMillis,
                )
            },
            { onClose() }, false, true,
        )
    }

    override fun drawBody(context: GuiGraphicsExtractor, painter: WaypointPanelPainter, x: Int, y: Int, width: Int) {
        pickerBounds = Rect(x, y, picker.width, picker.height)
        val immediate = pickerContext(context)
        immediate.renderContext.pushMatrix()
        try {
            immediate.renderContext.translate(x.toFloat(), y.toFloat())
            picker.render(immediate)
        } finally {
            immediate.renderContext.popMatrix()
        }
        immediate.renderContext.renderExtraLayers()
    }

    override fun drawFooter(context: GuiGraphicsExtractor, painter: WaypointPanelPainter, bounds: Rect) {
        super.drawFooter(context, painter, bounds)
        val reset = Rect(bounds.x + bounds.width - RESET_SIZE, bounds.y, RESET_SIZE, bounds.height)
        val mouse = InputUtilities.scaledMousePosition(Minecraft.getInstance())
        if (reset.contains(mouse.x, mouse.y)) OverlayTextStyle.drawControlHover(context, reset, 1.0)
        MoulConfigRenderContext(context).drawTexturedRect(
            GuiTextures.RESET, reset.x.toFloat(), (reset.y + (reset.height - RESET_SIZE) / 2).toFloat(),
            RESET_SIZE.toFloat(), RESET_SIZE.toFloat()
        )
        painter.controls += WaypointPanelControl(reset, tooltip = listOf("Reset to the preset's default color.")) {
            draft = draft.copy(color = null, chromaMillis = 0)
            guiContext.onAfterClose()
            picker = createPicker()
            guiContext = GuiContext(picker)
        }
    }

    private fun pickerContext(context: GuiGraphicsExtractor? = null): GuiImmediateContext {
        val mouse = InputUtilities.scaledMousePosition(Minecraft.getInstance())
        return GuiImmediateContext(
            MoulConfigRenderContext(context ?: MoulConfigPlatform.makeDrawContext()),
            pickerBounds.x, pickerBounds.y, pickerBounds.width, pickerBounds.height,
            mouse.x - pickerBounds.x, mouse.y - pickerBounds.y, mouse.x, mouse.y,
            (mouse.x - pickerBounds.x).toFloat(), (mouse.y - pickerBounds.y).toFloat(),
        )
    }

    override fun mouseClicked(click: MouseButtonEvent, doubled: Boolean): Boolean {
        if (interactive && pickerBounds.contains(click.x().toInt(), click.y().toInt()) && click.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return picker.mouseEvent(MouseEvent.Click(click.button(), true), pickerContext())
        }
        return super.mouseClicked(click, doubled)
    }

    override fun mouseMoved(mouseX: Double, mouseY: Double) {
        if (interactive) picker.mouseEvent(MouseEvent.Move(0f, 0f), pickerContext())
    }

    override fun mouseReleased(click: MouseButtonEvent): Boolean =
        super.mouseReleased(click) || picker.mouseEvent(MouseEvent.Click(click.button(), false), pickerContext())

    override fun keyPressed(event: KeyEvent): Boolean {
        if (event.key() == GLFW.GLFW_KEY_ESCAPE) return super.keyPressed(event)
        if (!interactive) return true
        return picker.keyboardEvent(KeyboardEvent.KeyPressed(event.key(), event.scancode(), true), pickerContext()) ||
            super.keyPressed(event)
    }

    override fun charTyped(event: CharacterEvent): Boolean =
        !interactive || picker.keyboardEvent(KeyboardEvent.CharTyped(event.codepoint().toChar()), pickerContext())

    override fun confirm() = Unit

    override fun removed() {
        super.removed()
        guiContext.onAfterClose()
        if (draft.color == original.color && draft.chromaMillis == original.chromaMillis) return
        WaypointPanel.perform {
            val saved = WaypointLibrary.groups.firstOrNull { it.id == group.id } ?: return@perform
            val current = saved.points.firstOrNull { it.id == original.id } ?: return@perform
            val updated = current.copy(color = draft.color, chromaMillis = draft.chromaMillis)
            Waypoints.updateGroup(saved.copy(points = saved.points.map { if (it.id == updated.id) updated else it }))
            WaypointLibrary.save()
        }
    }

    companion object {
        fun open(group: WaypointGroup, point: WaypointPoint, index: Int, parent: Screen? = WaypointPanel.menuParent()) {
            Waypoints.select(group, point)
            MinecraftClient.setScreen(WaypointColorPicker(group, point, index, parent))
        }

        fun previewPoint(groupId: String, point: WaypointPoint): WaypointPoint {
            val picker = MinecraftClient.screen() as? WaypointColorPicker ?: return point
            return picker.draft.takeIf { picker.group.id == groupId && it.id == point.id } ?: point
        }

        private const val PANEL_INSET = 8
        private const val RESET_SIZE = 11
    }
}
