package com.skysoft.features.waypoints

import com.skysoft.utils.gui.OverlayListScroll
import com.skysoft.utils.gui.OverlayPanelStyle
import com.skysoft.utils.gui.OverlayTextStyle
import com.skysoft.utils.gui.PixelButtonTone
import com.skysoft.utils.gui.PixelControlColors
import com.skysoft.utils.gui.PixelSliderRenderer
import com.skysoft.utils.gui.Rect
import com.skysoft.utils.input.InputUtilities
import net.minecraft.client.gui.GuiGraphicsExtractor

internal object WaypointSettingsRenderer {
    fun render(context: GuiGraphicsExtractor, layout: WaypointSettingsLayout, mouseX: Int, mouseY: Int): List<WaypointPanelControl> {
        val opacity = WaypointSettingsPanel.opacity
        if (!WaypointSettingsPanel.isOpen) return emptyList()
        val painter = WaypointPanelPainter(context, mouseX, mouseY, opacity, WaypointSettingsPanel.isInteractive)
        painter.controls += WaypointPanelControl(layout.bounds, enabled = false, action = {})
        OverlayPanelStyle.draw(context, layout.bounds.x, layout.bounds.y, layout.bounds.width, layout.bounds.height, opacity)
        painter.text(
            Rect(layout.bounds.x + WaypointSettingsLayout.INSET, layout.bounds.y + TITLE_Y, TITLE_WIDTH, TITLE_HEIGHT),
            OverlayTextStyle.title("Waypoints Settings"), PixelControlColors.TEXT
        )
        painter.action(layout.close, "x", tone = PixelButtonTone.DANGER) { WaypointSettingsPanel.close() }
        val tabs = layout.tabs
        val half = (tabs.width - TAB_GAP) / 2
        painter.action(Rect(tabs.x, tabs.y, half, tabs.height), "Settings", selected = !WaypointSettingsPanel.showDetails) {
            WaypointSettingsPanel.selectTab(false)
        }
        painter.action(Rect(tabs.x + half + TAB_GAP, tabs.y, half, tabs.height), "Details", selected = WaypointSettingsPanel.showDetails) {
            WaypointSettingsPanel.selectTab(true)
        }
        if (WaypointSettingsPanel.showDetails) {
            renderDetails(context, painter, layout, mouseX, mouseY)
        } else {
            renderBindings(painter, layout)
        }
        painter.action(layout.back, "Back") { WaypointSettingsPanel.close() }
        if (layout.maximumOffset > 0) {
            painter.text(
                layout.scrollIndicator,
                OverlayListScroll.indicator(layout.optionOffset, layout.maximumOffset - layout.optionOffset)
            )
        }
        return painter.controls
    }

    private fun renderBindings(painter: WaypointPanelPainter, layout: WaypointSettingsLayout) {
        WaypointBinding.entries.forEach { binding ->
            if (!layout.isOptionVisible(binding.ordinal)) return@forEach
            val row = layout.optionRow(binding.ordinal)
            val awaiting = WaypointSettingsPanel.awaitingBinding == binding
            val key = if (awaiting) "Press a key..." else InputUtilities.bindingName(binding.value())
            painter.text(layout.label(row), binding.label)
            painter.action(
                layout.value(row), key, selected = awaiting,
                tooltip = "§eClick §7then press a key or mouse button.\n§eDelete / Backspace §7to clear\n§eEscape §7to cancel"
            ) { WaypointSettingsPanel.bind(binding) }
        }
    }

    private fun renderDetails(
        context: GuiGraphicsExtractor,
        painter: WaypointPanelPainter,
        layout: WaypointSettingsLayout,
        mouseX: Int,
        mouseY: Int,
    ) {
        WaypointSlider.entries.forEach { setting ->
            if (!layout.isOptionVisible(setting.ordinal)) return@forEach
            val row = layout.optionRow(setting.ordinal)
            val label = layout.label(row)
            painter.text(label, setting.label)
            painter.text(layout.value(row).copy(y = label.y), setting.value().toString())
            val progress = (setting.value() - setting.range.first).toFloat() / (setting.range.last - setting.range.first)
            PixelSliderRenderer.draw(context, layout.track(setting), progress, row.contains(mouseX, mouseY), WaypointSettingsPanel.opacity)
            painter.controls += WaypointPanelControl(
                row, enabled = WaypointSettingsPanel.isInteractive, tooltip = waypointTooltip("§eDrag or scroll §7to change"),
            ) {
                WaypointSettingsPanel.startDrag(setting, mouseX)
            }
        }
        val toggles = WaypointDetailToggle.visibleEntries
        toggles.forEachIndexed { toggleIndex, setting ->
            val index = WaypointSlider.entries.size + toggleIndex
            if (!layout.isOptionVisible(index)) return@forEachIndexed
            val row = layout.optionRow(index)
            painter.text(layout.label(row), setting.label)
            painter.cycle(layout.value(row), setting.label, listOf(false, true), setting.isEnabled(), label = { if (it) "On" else "Off" }) {
                setting.toggle()
                WaypointSettingsPanel.save()
            }
        }
    }

    private const val TITLE_Y = 8
    private const val TITLE_WIDTH = 220
    private const val TITLE_HEIGHT = 12
    private const val TAB_GAP = 3
}
