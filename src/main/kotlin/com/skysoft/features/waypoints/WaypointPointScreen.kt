package com.skysoft.features.waypoints

import com.skysoft.utils.MinecraftClient
import com.skysoft.utils.gui.Rect
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen

internal class WaypointPointScreen private constructor(
    private val group: WaypointGroup,
    private val point: WaypointPoint,
    parent: Screen?,
) : WaypointDialog("Edit Waypoint", parent) {
    private val name = form.field(point.name)
    private val xField = form.field(waypointCoordinate(point.x), COORDINATE_LENGTH)
    private val yField = form.field(waypointCoordinate(point.y), COORDINATE_LENGTH)
    private val zField = form.field(waypointCoordinate(point.z), COORDINATE_LENGTH)
    private val radius = form.field(point.radius?.let(::waypointCoordinate).orEmpty(), COORDINATE_LENGTH)
    private var styles = point.styles
    override val contentHeight: Int get() = FIELD_ROW * 2 + BUTTON_ROW * (if (styles == null) 3 else 4) +
        if (group.route) BUTTON_ROW else 0

    override fun drawBody(context: GuiGraphicsExtractor, painter: WaypointPanelPainter, x: Int, y: Int, width: Int) {
        form.draw(context, opacity, name, "Name (optional)", Rect(x, y, width, FIELD_HEIGHT), showLabel = false)
        val third = (width - GAP * 2) / COORDINATE_FIELDS
        listOf(xField, yField, zField).forEachIndexed { index, field ->
            form.draw(context, opacity, field, COORDINATE_LABELS[index], Rect(x + index * (third + GAP), y + BUTTON_ROW, third, FIELD_ROW))
        }
        val half = (width - GAP) / 2
        val positionY = y + BUTTON_ROW + FIELD_ROW
        val canMove = Waypoints.isInContext(group)
        painter.action(Rect(x, positionY, half, FIELD_HEIGHT), "Move to feet", enabled = canMove && WaypointPlacement.feet != null) {
            WaypointPlacement.feet?.let { position -> setPosition(position.x, position.y, position.z) }
        }
        painter.action(
            Rect(x + half + GAP, positionY, half, FIELD_HEIGHT), "Move to crosshair",
            enabled = canMove && WaypointPlacement.crosshair != null
        ) {
            WaypointPlacement.crosshair?.let { position -> setPosition(position.x, position.y, position.z) }
        }
        val detailY = positionY + BUTTON_ROW
        form.draw(context, opacity, radius, "Arrival distance", Rect(x, detailY, half, FIELD_ROW))
        painter.action(Rect(x + half + GAP, detailY + FIELD_LABEL_HEIGHT, half, FIELD_HEIGHT), "Color") {
            val saved = WaypointLibrary.groups.firstOrNull { it.id == group.id } ?: error("This preset is no longer available.")
            val current = saved.points.firstOrNull { it.id == point.id } ?: error("This point is no longer available.")
            WaypointColorPicker.open(saved, current, saved.points.indexOf(current), this)
        }
        val styleY = detailY + FIELD_ROW
        painter.cycle(
            Rect(x, styleY, width, FIELD_HEIGHT), "Styles", listOf(true, false), styles == null,
            label = { if (it) "Use preset styles" else "Custom styles" },
        ) { styles = if (it) null else group.styles }
        styles?.let { current ->
            painter.styles(Rect(x, styleY + BUTTON_ROW, width, FIELD_HEIGHT), current) { styles = it }
        }
        if (group.route) {
            val followY = styleY + BUTTON_ROW * if (styles == null) 1 else 2
            painter.action(
                Rect(x, followY, width, FIELD_HEIGHT), "Start route from this point",
                enabled = canMove && point.enabled
            ) {
                savePoint()
                Waypoints.follow(fromSelection = true)
                onClose()
            }
        }
    }

    private fun setPosition(x: Double, y: Double, z: Double) {
        xField.text = waypointCoordinate(x)
        yField.text = waypointCoordinate(y)
        zField.text = waypointCoordinate(z)
    }

    private fun savePoint() {
        val current = WaypointLibrary.groups.firstOrNull { it.id == group.id }?.points?.firstOrNull { it.id == point.id }
        require(current != null) { "This point is no longer available." }
        val updated = current.copy(
            name = name.text.trim(),
            x = parseCoordinate(xField.text), y = parseCoordinate(yField.text), z = parseCoordinate(zField.text),
            radius = radius.text.takeIf { it.isNotBlank() }?.let(::parseWaypointRadius),
            styles = styles,
        )
        Waypoints.select(group, point)
        WaypointEditing.updatePoint(updated)
    }

    override fun confirm() {
        savePoint()
        onClose()
    }

    companion object {
        fun open() {
            val group = Waypoints.group ?: return
            val point = Waypoints.point ?: return
            MinecraftClient.setScreen(WaypointPointScreen(group, point, WaypointPanel.menuParent()))
        }

        private fun parseCoordinate(text: String): Double {
            val coordinate = text.trim().toDoubleOrNull()
            require(coordinate != null && coordinate.isFinite() && kotlin.math.abs(coordinate) <= MAX_WAYPOINT_COORDINATE) {
                "Coordinates must be numbers within world bounds."
            }
            return coordinate
        }

        private const val COORDINATE_LENGTH = 24
        private const val COORDINATE_FIELDS = 3
        private val COORDINATE_LABELS = listOf("X", "Y", "Z")
    }
}
