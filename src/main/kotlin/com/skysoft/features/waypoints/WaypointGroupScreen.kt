package com.skysoft.features.waypoints

import com.skysoft.data.SkyBlockIsland
import com.skysoft.utils.MinecraftClient
import com.skysoft.utils.gui.PixelButtonTone
import com.skysoft.utils.gui.Rect
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen

internal class WaypointGroupScreen private constructor(
    private val original: WaypointGroup?,
    private var island: SkyBlockIsland,
    parent: Screen?,
) : WaypointDialog(if (original == null) "New Waypoint Preset" else "Edit Waypoint Preset", parent) {
    private val name = form.field(original?.name.orEmpty())
    private val radius = form.field(waypointCoordinate(original?.radius ?: DEFAULT_WAYPOINT_RADIUS), NUMBER_LENGTH)
    private val color = form.field(waypointColorText(original?.color ?: DEFAULT_WAYPOINT_COLOR), COLOR_LENGTH)
    private var scope = original?.scope ?: Waypoints.defaultScope(island)
    private var styles = original?.styles ?: setOf(WaypointStyle.MARKER)
    private var isRoute = original?.route ?: true
    private var loop = original?.loop ?: true
    private var skipAhead = original?.skipAhead ?: false
    private var filled = original?.filled ?: false
    private var lines = original?.lines ?: true
    private var deletePending = false
    override val headerHeight = 44
    override val contentHeight: Int get() = optionsHeight + if (original == null) 0 else BUTTON_ROW
    private val routeOptionsHeight: Int get() = if (isRoute) BUTTON_ROW else 0
    private val optionsHeight: Int get() = BUTTON_ROW * 4 + FIELD_ROW + routeOptionsHeight

    override fun drawHeader(painter: WaypointPanelPainter, bounds: Rect) {
        super.drawHeader(painter, bounds)
        val y = bounds.y + TITLE_ROW
        val onWidth = font.width("on ")
        val forWidth = font.width(" for ")
        val scopeWidth = font.width("[${scope.label}]") + GAP
        val islandWidth = minOf(
            font.width("[${island.displayName}]") + GAP, bounds.width - onWidth - forWidth - scopeWidth,
        ).coerceAtLeast(1)
        painter.text(Rect(bounds.x, y + GAP, onWidth, TEXT_HEIGHT), "on ")
        painter.textControl(Rect(bounds.x + onWidth, y, islandWidth, FIELD_HEIGHT), "[${island.displayName}]", leftAligned = true) {
            WaypointIslandScreen.open(island) { target ->
                island = target
                if (original == null) scope = Waypoints.defaultScope(target)
            }
        }
        val forX = bounds.x + onWidth + islandWidth
        painter.text(Rect(forX, y + GAP, forWidth, TEXT_HEIGHT), " for ")
        painter.cycle(
            Rect(forX + forWidth, y, scopeWidth, FIELD_HEIGHT), "Scope", WaypointScope.entries, scope, WaypointScope::label,
        ) { scope = it }
    }

    override fun drawBody(context: GuiGraphicsExtractor, painter: WaypointPanelPainter, x: Int, y: Int, width: Int) {
        form.draw(context, opacity, name, "Preset name", Rect(x, y, width, FIELD_HEIGHT), showLabel = false)
        val half = (width - GAP) / 2
        painter.cycle(
            Rect(x, y + BUTTON_ROW, width, FIELD_HEIGHT), "Preset mode", listOf(true, false), isRoute,
            label = { if (it) "Ordered route" else "Fixed markers" },
        ) { isRoute = it }
        if (isRoute) {
            painter.toggle(Rect(x, y + BUTTON_ROW * 2, half, FIELD_HEIGHT), "Loop route", loop) { loop = !loop }
            painter.toggle(
                Rect(x + half + GAP, y + BUTTON_ROW * 2, half, FIELD_HEIGHT), "Skip ahead", skipAhead,
                description = "Standing on a later waypoint's block skips the points before it.",
            ) { skipAhead = !skipAhead }
        }
        val detailsY = y + BUTTON_ROW * 2 + routeOptionsHeight
        form.draw(context, opacity, radius, "Arrival distance", Rect(x, detailsY, half, FIELD_ROW))
        form.draw(context, opacity, color, "Color (#RRGGBB)", Rect(x + half + GAP, detailsY, half, FIELD_ROW))
        val styleY = detailsY + FIELD_ROW
        painter.styles(Rect(x, styleY, width, FIELD_HEIGHT), styles) { styles = it }
        painter.toggle(
            Rect(x, styleY + BUTTON_ROW, half, FIELD_HEIGHT), "Fill block", filled, enabled = WaypointStyle.BLOCK in styles,
        ) { filled = !filled }
        painter.toggle(
            Rect(x + half + GAP, styleY + BUTTON_ROW, half, FIELD_HEIGHT), "Connect route", lines, enabled = isRoute,
            description = "Draw lines between consecutive waypoints.",
        ) { lines = !lines }
        if (original != null) renderGroupActions(painter, x, y + optionsHeight, width)
    }

    private fun renderGroupActions(painter: WaypointPanelPainter, x: Int, y: Int, width: Int) {
        val half = (width - GAP) / 2
        painter.action(Rect(x, y, half, FIELD_HEIGHT), "Duplicate") {
            selectOriginal()
            WaypointEditing.duplicateGroup()
            onClose()
        }
        painter.action(
            Rect(x + half + GAP, y, half, FIELD_HEIGHT), if (deletePending) "Confirm delete" else "Delete preset",
            tone = PixelButtonTone.DANGER, tooltip = "Delete the preset and its points. Undo can restore it."
        ) {
            if (deletePending) {
                selectOriginal()
                WaypointEditing.removeGroup()
                onClose()
            } else deletePending = true
        }
    }

    override fun confirm() {
        val base = if (original == null) WaypointGroup(name = name.text.trim(), island = island) else selectOriginal()
        val edited = base.copy(
            name = name.text.trim(), radius = parseWaypointRadius(radius.text), color = parseWaypointColor(color.text),
            route = isRoute, loop = loop, skipAhead = skipAhead, styles = styles, filled = filled, lines = lines, island = island,
        )
        val updated = if (original != null && base.scope == scope && base.island == island) edited else Waypoints.scoped(edited, scope)
        if (original == null) Waypoints.updateGroups(WaypointLibrary.groups + updated, WaypointSelection(updated.id))
        else Waypoints.updateGroup(updated)
        Waypoints.browseIsland = island
        onClose()
    }

    private fun selectOriginal(): WaypointGroup {
        val saved = WaypointLibrary.groups.firstOrNull { it.id == original?.id } ?: error("This preset is no longer available.")
        Waypoints.select(saved)
        return saved
    }

    companion object {
        fun create() {
            val island = Waypoints.browseIsland ?: Waypoints.island ?: error("Choose an island first.")
            MinecraftClient.setScreen(WaypointGroupScreen(null, island, MinecraftClient.screen()))
        }

        fun edit() {
            val group = Waypoints.group ?: return
            MinecraftClient.setScreen(WaypointGroupScreen(group, group.island, MinecraftClient.screen()))
        }

        private const val TITLE_ROW = 16
        private const val NUMBER_LENGTH = 12
        private const val COLOR_LENGTH = 7
    }
}

internal fun parseWaypointRadius(value: String): Double = WaypointValidation.validateRadius(value.trim().toDoubleOrNull())

internal fun parseWaypointColor(value: String): Int {
    val clean = value.trim().removePrefix("#")
    require(clean.length == COLOR_DIGITS && clean.all { it.digitToIntOrNull(HEX_RADIX) != null }) { "Use a color such as #45A3FF." }
    return clean.toInt(HEX_RADIX)
}

internal fun waypointColorText(color: Int): String = "#" + color.toString(HEX_RADIX).padStart(COLOR_DIGITS, '0').uppercase()

private const val COLOR_DIGITS = 6
private const val HEX_RADIX = 16
