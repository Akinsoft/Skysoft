package com.skysoft.features.waypoints

import com.skysoft.gui.OverlayControlCycle
import com.skysoft.gui.OverlayControlTooltips
import com.skysoft.utils.ColorUtilities.withScaledAlpha
import com.skysoft.utils.gui.OverlayListScroll
import com.skysoft.utils.gui.OverlayPanelStyle
import com.skysoft.utils.gui.OverlayTextStyle
import com.skysoft.utils.gui.PixelButtonTone
import com.skysoft.utils.gui.PixelControlColors
import com.skysoft.utils.gui.Rect
import com.skysoft.utils.gui.elide
import com.skysoft.utils.render.LegacyTextRenderer
import com.skysoft.utils.render.shader.SkysoftCircleShaderRenderer
import java.awt.Color
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor

internal object WaypointPanelRenderer {
    fun render(context: GuiGraphicsExtractor, layout: WaypointPanelLayout, mouseX: Int, mouseY: Int): List<WaypointPanelControl> {
        val opacity = WaypointPanel.opacity
        if (opacity <= 0.0) return emptyList()
        val painter = WaypointPanelPainter(context, mouseX, mouseY, opacity, WaypointPanel.isInteractive)
        OverlayPanelStyle.draw(context, layout.bounds.x, layout.bounds.y, layout.bounds.width, layout.bounds.height, opacity)
        renderHeader(painter, layout)
        if (WaypointPanel.isBrowsingPresets) {
            WaypointPresetList.render(context, painter, layout)
        } else {
            val loadError = WaypointLibrary.loadError
            when {
                !Waypoints.config.enabled -> renderDisabled(painter, layout)
                loadError != null -> painter.info(layout.row(layout.list.y), loadError)
                else -> renderList(painter, layout)
            }
            if (layout.showPlacementControls) renderPlacement(painter, layout)
            if (layout.showRouteControls) renderRouteControls(painter, layout)
            if (layout.showFooter) renderFooter(painter, layout)
        }
        if (layout.hasMoreItems) painter.centeredText(
            Rect(layout.list.x, layout.list.y + layout.list.height, layout.list.width, WaypointPanelLayout.INDICATOR_HEIGHT),
            OverlayListScroll.indicator(
                WaypointPanel.scrollOffset, (WaypointPanel.itemCount - WaypointPanel.scrollOffset - layout.visibleRows).coerceAtLeast(0),
            ),
        )
        return painter.controls
    }

    private fun renderHeader(painter: WaypointPanelPainter, layout: WaypointPanelLayout) {
        val header = layout.header
        painter.text(
            Rect(header.x + WaypointPanelLayout.INSET, header.y + WaypointPanelLayout.INSET, header.width - CLOSE_WIDTH, header.height),
            OverlayTextStyle.title(if (WaypointPanel.isBrowsingPresets) "Waypoints" else Waypoints.group?.name ?: "Waypoints")
        )
        painter.textControl(
            Rect(header.x + header.width - CLOSE_WIDTH, header.y + 2, CLOSE_WIDTH, header.height), "[x]",
            tooltip = waypointTooltip("Close the Waypoints Display."), tone = PixelButtonTone.DANGER,
        ) { WaypointPanel.dismiss() }
    }

    private fun renderDisabled(painter: WaypointPanelPainter, layout: WaypointPanelLayout) {
        painter.action(layout.row(layout.list.y), "Enable feature", tone = PixelButtonTone.CONFIRM) {
            WaypointPanel.enable()
        }
    }

    private fun renderList(painter: WaypointPanelPainter, layout: WaypointPanelLayout) {
        val group = Waypoints.group
        val points = group?.points.orEmpty()
        val offset = WaypointPanel.scrollOffset
        val selectedIndex = points.indexOfFirst { it.id == Waypoints.selection.pointId }
        if (points.isEmpty()) {
            val hint = if (group == null) "No preset selected." else "No waypoints yet."
            painter.text(layout.row(layout.list.y), hint)
        }
        if (group == null) return
        val currentPoint = Waypoints.route.pointId.takeIf { group.id == Waypoints.route.groupId && !Waypoints.route.complete }
        points.drop(offset).take(layout.visibleRows).forEachIndexed { row, point ->
            val index = offset + row
            val dot = Rect(layout.list.x, layout.list.y + row * WaypointPanelLayout.ROW_HEIGHT, DOT_WIDTH, WaypointPanelLayout.ROW_HEIGHT)
            val bounds = Rect(dot.x + dot.width, dot.y, layout.list.width - DOT_WIDTH - VISIBILITY_WIDTH - DELETE_WIDTH, dot.height)
            painter.colorDot(
                dot, group.pointColor(WaypointColorPicker.previewPoint(group.id, point), index),
                points.getOrNull(index + 1)?.takeIf { row + 1 < layout.visibleRows }?.let {
                    group.pointColor(WaypointColorPicker.previewPoint(group.id, it), index + 1)
                }
            ) {
                WaypointColorPicker.open(group, point, index)
            }
            painter.textControl(
                bounds, "${point.label(index)} (${waypointCoordinates(point)})",
                tooltip = waypointTooltip("§eDrag §7to reorder\n§eRight-click §7to edit"),
                leftAligned = true, pointId = point.id, suffix = if (point.id == currentPoint) " §e◀" else "",
                rightClick = {
                    Waypoints.select(group, point)
                    WaypointPointScreen.open()
                }
            ) {}
            if (index == WaypointPointDrag.targetIndex) painter.dropIndicator(bounds, after = index > selectedIndex)
            val visibility = Rect(bounds.x + bounds.width, bounds.y, VISIBILITY_WIDTH, bounds.height)
            painter.cycle(
                visibility, "Visibility", listOf(true, false), point.enabled, label = { if (it) "On" else "Off" },
                color = if (point.enabled) 0xFF55FF55.toInt() else PixelControlColors.MUTED_TEXT,
            ) { enabled ->
                Waypoints.select(group, point)
                WaypointEditing.updatePoint(point.copy(enabled = enabled))
            }
            val pending = WaypointPanel.pendingDelete == point.id
            painter.textControl(
                Rect(visibility.x + visibility.width, bounds.y, DELETE_WIDTH, bounds.height), if (pending) "?" else "x",
                selected = pending, tone = PixelButtonTone.DANGER,
                tooltip = waypointTooltip(
                    "§eClick ${if (pending) "again " else "twice "}§7to delete ${point.label(index)}.\n" +
                        "§eShift-click §7to delete immediately.\n§eUndo §7to restore it."
                )
            ) { WaypointPanel.deletePoint(group, point) }
        }
    }

    private fun renderPlacement(painter: WaypointPanelPainter, layout: WaypointPanelLayout) {
        val cells = WaypointPanelLayout.columns(layout.row(layout.footerTop), "At feet", "At crosshair")
        val canPlace = Waypoints.group?.let(Waypoints::isInContext) == true
        painter.action(
            cells[0], "At feet", enabled = canPlace && WaypointPlacement.feet != null,
            tooltip = "Add the block under your feet.\nSet a keybind in Waypoints > Settings."
        ) {
            WaypointPlacement.feet?.let { WaypointEditing.add(it) }
        }
        painter.action(
            cells[1], "At crosshair", enabled = canPlace && WaypointPlacement.crosshair != null,
            tooltip = "Add the aimed block. The target stays fixed while chat or inventory is open."
        ) {
            WaypointPlacement.crosshair?.let { WaypointEditing.add(it) }
        }
    }

    private fun renderRouteControls(painter: WaypointPanelPainter, layout: WaypointPanelLayout) {
        val group = Waypoints.group
        val following = group != null && group.id == Waypoints.route.groupId
        val label = when {
            !following -> "Start"
            Waypoints.route.complete -> "Restart"
            Waypoints.route.paused -> "Resume"
            else -> "Pause"
        }
        val cells = WaypointPanelLayout.columns(layout.row(layout.routeControlsTop), label, "Previous", "Next", "Stop")
        painter.action(
            cells[0], label, enabled = group?.let(Waypoints::isInContext) == true && group.points.any { it.enabled },
            selected = following && !Waypoints.route.paused, tone = PixelButtonTone.CONFIRM
        ) { Waypoints.toggleFollowing() }
        painter.action(cells[1], "Previous", enabled = following) { Waypoints.step(-1) }
        painter.action(cells[2], "Next", enabled = following) { Waypoints.step(1) }
        painter.action(cells.last(), "Stop", enabled = following, tone = PixelButtonTone.DANGER) { Waypoints.route.stop() }
    }

    private fun renderFooter(painter: WaypointPanelPainter, layout: WaypointPanelLayout) {
        val cells = WaypointPanelLayout.columns(layout.row(layout.actionsTop), "Back", "Undo", "Redo", "Settings")
        painter.action(cells[0], "Back") { WaypointPanel.showPage(presets = true) }
        painter.action(cells[1], "Undo", enabled = Waypoints.history.canUndo, tooltip = "Undo the last waypoint change.\nCtrl+Z") {
            Waypoints.undo()
        }
        painter.action(
            cells[REDO_COLUMN], "Redo", enabled = Waypoints.history.canRedo, tooltip = "Redo the last waypoint change.\nCtrl+Y",
        ) {
            Waypoints.redo()
        }
        painter.action(cells.last(), "Settings", enabled = Waypoints.group != null, color = 0xFF55FFFF.toInt()) {
            WaypointGroupScreen.edit()
        }
    }

    private const val REDO_COLUMN = 2
    private const val CLOSE_WIDTH = 22
    private const val DOT_WIDTH = 14
    private const val VISIBILITY_WIDTH = 25
    private const val DELETE_WIDTH = 16
}

internal class WaypointPanelPainter(
    private val context: GuiGraphicsExtractor,
    private val mouseX: Int,
    private val mouseY: Int,
    private val opacity: Double,
    private val interactive: Boolean,
) {
    val controls = mutableListOf<WaypointPanelControl>()
    private val font get() = Minecraft.getInstance().font

    fun action(
        bounds: Rect,
        label: String,
        enabled: Boolean = true,
        selected: Boolean = false,
        tone: PixelButtonTone = PixelButtonTone.NORMAL,
        tooltip: String = "",
        color: Int = 0xFFFFFF55.toInt(),
        action: () -> Unit,
    ) = textControl(bounds, "[$label]", enabled, selected, waypointTooltip(tooltip), tone = tone, textColor = color, action = action)

    fun <T> cycle(
        bounds: Rect,
        name: String,
        options: List<T>,
        current: T,
        label: (T) -> String,
        enabled: Boolean = true,
        leftAligned: Boolean = false,
        color: Int = ACTION_COLOR,
        change: (T) -> Unit,
    ) = textControl(
        bounds, "[${label(current)}]", enabled, leftAligned = leftAligned, textColor = color,
        tooltip = OverlayControlTooltips.cycle(name, options.map(label), options.indexOf(current)),
        rightClick = { change(OverlayControlCycle.next(options, current, backwards = true)) },
    ) { change(OverlayControlCycle.next(options, current, backwards = false)) }

    fun toggle(
        bounds: Rect,
        name: String,
        value: Boolean,
        enabled: Boolean = true,
        description: String = "",
        change: () -> Unit,
    ) = textControl(
        bounds, "[$name]", enabled, selected = value, textColor = ACTION_COLOR,
        tooltip = OverlayControlTooltips.cycle(name, listOf("Off", "On"), if (value) 1 else 0) +
            if (description.isEmpty()) emptyList() else listOf("", "§7$description"),
        rightClick = change, action = change,
    )

    fun styles(bounds: Rect, selected: Set<WaypointStyle>, change: (Set<WaypointStyle>) -> Unit) {
        val width = (bounds.width - STYLE_GAP * (WaypointStyle.entries.size - 1)) / WaypointStyle.entries.size
        WaypointStyle.entries.forEachIndexed { index, option ->
            toggle(Rect(bounds.x + index * (width + STYLE_GAP), bounds.y, width, bounds.height), option.label, option in selected) {
                change(if (option in selected) selected - option else selected + option)
            }
        }
    }

    fun textControl(
        bounds: Rect,
        label: String,
        enabled: Boolean = true,
        selected: Boolean = false,
        tooltip: List<String> = emptyList(),
        leftAligned: Boolean = false,
        pointId: String? = null,
        rightClick: (() -> Unit)? = null,
        tone: PixelButtonTone = PixelButtonTone.NORMAL,
        textColor: Int = PixelControlColors.TEXT,
        suffix: String = "",
        action: () -> Unit,
    ) {
        val labelText = font.elide(label, bounds.width - TEXT_INSET - font.width(suffix))
        val text = labelText + suffix
        val color = when {
            !enabled -> PixelControlColors.MUTED_TEXT
            tone == PixelButtonTone.DANGER -> DANGER_TEXT
            selected || tone == PixelButtonTone.CONFIRM -> SELECTED_TEXT
            else -> textColor
        }
        val x = if (leftAligned) bounds.x + TEXT_INSET / 2 else bounds.x + (bounds.width - font.width(text)) / 2
        val hitBounds = if (pointId != null) bounds else Rect(x - 1, bounds.y, font.width(text) + 2, bounds.height)
        if (interactive && enabled && hitBounds.contains(mouseX, mouseY)) OverlayTextStyle.drawControlHover(context, hitBounds, opacity)
        LegacyTextRenderer.draw(
            context, text, x, bounds.y + (bounds.height - font.lineHeight) / 2, defaultColor = color.withScaledAlpha(opacity),
        )
        val lines = if (labelText != label && tooltip.isEmpty()) listOf(label) else tooltip
        controls += WaypointPanelControl(hitBounds, enabled && interactive, lines, pointId, rightClick, action)
    }

    fun colorDot(bounds: Rect, rgb: Int, nextRgb: Int?, action: () -> Unit) {
        if (interactive && bounds.contains(mouseX, mouseY)) OverlayTextStyle.drawControlHover(context, bounds, opacity)
        val x = bounds.x + bounds.width / 2
        val y = bounds.y + bounds.height / 2
        if (nextRgb != null) context.fillGradient(
            x - 1, y, x + 1, y + bounds.height,
            (rgb or OPAQUE_ALPHA).withScaledAlpha(opacity), (nextRgb or OPAQUE_ALPHA).withScaledAlpha(opacity),
        )
        SkysoftCircleShaderRenderer.drawFilledCircle(
            context, x - DOT_RADIUS, y - DOT_RADIUS, Color((rgb or OPAQUE_ALPHA).withScaledAlpha(opacity), true), DOT_RADIUS,
        )
        controls += WaypointPanelControl(
            bounds, enabled = interactive, tooltip = waypointTooltip("§eClick §7to choose a color."), action = action
        )
    }

    fun centeredText(bounds: Rect, text: String) {
        this.text(Rect(bounds.x + (bounds.width - font.width(text)) / 2, bounds.y, bounds.width, bounds.height), text)
    }

    fun dropIndicator(bounds: Rect, after: Boolean) {
        val y = if (after) bounds.y + bounds.height - 1 else bounds.y
        context.fill(bounds.x, y, bounds.x + bounds.width, y + 1, PixelControlColors.TEXT.withScaledAlpha(opacity))
    }

    fun text(bounds: Rect, text: String, color: Int = PixelControlColors.MUTED_TEXT) {
        LegacyTextRenderer.draw(context, font.elide(text, bounds.width), bounds.x, bounds.y, defaultColor = color.withScaledAlpha(opacity))
    }

    fun note(text: String, x: Int, y: Int, width: Int) {
        font.split(net.minecraft.network.chat.Component.literal(text), width).forEachIndexed { index, line ->
            context.text(
                font, line, x, y + index * WAYPOINT_NOTE_LINE_HEIGHT, PixelControlColors.MUTED_TEXT.withScaledAlpha(opacity), false,
            )
        }
    }

    fun info(bounds: Rect, text: String) {
        this.text(bounds, text)
        controls += WaypointPanelControl(bounds, enabled = false, tooltip = waypointTooltip(text), action = {})
    }

    private companion object {
        const val ACTION_COLOR = 0xFFFFFF55.toInt()
        const val STYLE_GAP = 4
        const val TEXT_INSET = 2
        const val DOT_RADIUS = 3
        const val DANGER_TEXT = 0xFFFF5555.toInt()
        const val SELECTED_TEXT = 0xFF55FF55.toInt()
        const val OPAQUE_ALPHA = 0xFF000000.toInt()
    }
}

private const val WAYPOINT_NOTE_LINE_HEIGHT = 10

internal fun waypointNoteHeight(text: String, width: Int): Int =
    if (text.isEmpty()) 0 else Minecraft.getInstance().font.split(
        net.minecraft.network.chat.Component.literal(text), width,
    ).size * WAYPOINT_NOTE_LINE_HEIGHT

internal fun waypointCoordinates(point: WaypointPoint): String =
    "${waypointCoordinate(point.x)}, ${waypointCoordinate(point.y)}, ${waypointCoordinate(point.z)}"

internal fun waypointCoordinate(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()
