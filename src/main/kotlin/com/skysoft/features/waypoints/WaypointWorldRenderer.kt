package com.skysoft.features.waypoints

import com.skysoft.config.DianaBurrowDistanceFormat
import com.skysoft.utils.ColorUtilities.toColor
import com.skysoft.utils.MinecraftClient
import com.skysoft.utils.WorldVec
import com.skysoft.utils.render.BlockHighlightRenderer
import com.skysoft.utils.render.LineBoxRenderer
import com.skysoft.utils.render.SkysoftRenderContext
import com.skysoft.utils.render.WorldLabelRenderer
import com.skysoft.utils.render.WorldLabelStyle
import com.skysoft.utils.render.WorldRenderDispatcher
import com.skysoft.utils.toWorldVec
import java.awt.Color
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.world.phys.shapes.CollisionContext

internal object WaypointWorldRenderer {
    fun register() {
        WorldRenderDispatcher.registerHandler(
            "Waypoints",
            isActive = { Waypoints.config.enabled && Waypoints.island != null },
            handler = ::render,
        )
    }

    private fun render(context: SkysoftRenderContext) {
        WaypointWorldSelection.beginFrame(context)
        if (MinecraftClient.isGuiHidden(Minecraft.getInstance())) return
        val details = Waypoints.config.details
        val camera = context.camera.position().toWorldVec()
        for (group in WaypointLibrary.groups) {
            if (!group.enabled || !Waypoints.isInContext(group)) continue
            val following = group.id == Waypoints.route.groupId && !Waypoints.route.complete
            val shown = if (following) Waypoints.route.visiblePoints(group, details.upcomingPoints) else null
            val rendered = group.points.withIndex().filter { (_, point) ->
                point.enabled && (shown == null || point.id in shown) &&
                    point.destination.distance(camera) <= details.renderDistance
            }
            for ((index, point) in rendered) {
                renderPoint(context, group, point, index, following)
            }
            if (group.route && group.lines) renderRouteLines(context, group, rendered.map { it.index })
        }
        if (details.showPlacementPreview && (Waypoints.route.groupId == null || Waypoints.route.complete) &&
            WaypointPanel.isEditing && Waypoints.group?.let(Waypoints::isInContext) == true
        ) {
            WaypointPlacement.crosshair?.let { target ->
                val minecraft = Minecraft.getInstance()
                val level = minecraft.level ?: return@let
                val player = minecraft.player ?: return@let
                val position = BlockPos.containing(target.x, target.y, target.z)
                val shape = level.getBlockState(position).getShape(level, position, CollisionContext.of(player))
                BlockHighlightRenderer.drawOverlay(context, target, shape, details.blockHighlight.color.get().toColor())
            }
        }
        WaypointWorldSelection.finishFrame()
    }

    private fun renderPoint(
        context: SkysoftRenderContext,
        group: WaypointGroup,
        point: WaypointPoint,
        index: Int,
        following: Boolean,
    ) {
        val isSelectedPoint = point.id == Waypoints.selection.pointId && group.id == Waypoints.selection.groupId
        val selected = WaypointPanel.isEditing && (isSelectedPoint || WaypointWorldSelection.isHovered(group, point))
        val current = following && point.id == Waypoints.route.pointId
        val rgb = if (current) CURRENT_COLOR else group.pointColor(WaypointColorPicker.previewPoint(group.id, point), index)
        val color = Color(rgb)
        val styles = group.pointStyles(point)
        if (WaypointStyle.MARKER in styles) renderMarker(context, point.destination, color, selected)
        if (WaypointStyle.BLOCK in styles) {
            BlockHighlightRenderer.drawBlock(
                context,
                point.position,
                if (selected) Color.WHITE else color,
                Color(color.red, color.green, color.blue, if (group.filled) FILL_ALPHA else 0),
                lineWidth = if (selected) SELECTED_WIDTH else LINE_WIDTH,
                depth = false,
            )
        }
        if (WaypointStyle.BEACON in styles) renderBeacon(context, point.destination, color)
        if (current && !Waypoints.route.paused) {
            context.drawLineToCrosshair(group.pointTarget(point), color, depth = true)
        }
        val details = Waypoints.config.details
        val heading = when {
            details.showNames -> point.label(index)
            group.route -> "#${index + 1}"
            else -> ""
        }
        val label = Component.literal(heading)
        if (details.showDistance) {
            val player = Minecraft.getInstance().player ?: return
            val distance = point.position.blockCenter().distance(player.position().toWorldVec())
            if (heading.isNotEmpty()) label.append(" ")
            label.append(
                Component.literal("(${DianaBurrowDistanceFormat.DECIMAL_METERS.format(distance)})")
                    .withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD)
            )
        }
        val labelAnchor = point.destination.up(LABEL_HEIGHT)
        val labelStyle = WorldLabelStyle(
            textColor = if (selected) Color.WHITE.rgb else color.rgb,
            backgroundColor = if (details.labelBackground) LABEL_BACKGROUND else 0,
            displayMode = Font.DisplayMode.SEE_THROUGH,
            maxRenderDistance = details.renderDistance.toDouble() + LABEL_HEIGHT,
            maxScale = MAX_LABEL_SCALE,
        )
        if (label.string.isNotEmpty()) WorldLabelRenderer.draw(context, labelAnchor, listOf(label), labelStyle)
        WaypointWorldSelection.collect(group, point, labelAnchor, label, labelStyle)
    }

    private fun renderMarker(context: SkysoftRenderContext, anchor: WorldVec, color: Color, selected: Boolean) {
        val radius = if (selected) SELECTED_MARKER_RADIUS else MARKER_RADIUS
        val offset = WorldVec(radius, radius, radius)
        LineBoxRenderer.draw3D(context, lineWidth = if (selected) SELECTED_WIDTH else LINE_WIDTH, depth = false) {
            drawBox(anchor - offset, anchor + offset, if (selected) Color.WHITE else color)
        }
    }

    private fun renderBeacon(context: SkysoftRenderContext, anchor: WorldVec, color: Color) {
        LineBoxRenderer.draw3D(context, lineWidth = BEACON_WIDTH, depth = false) {
            draw3DLine(anchor, anchor.up(BEACON_HEIGHT), color)
        }
    }

    private fun renderRouteLines(context: SkysoftRenderContext, group: WaypointGroup, indices: List<Int>) {
        val enabled = group.points.indices.filter { group.points[it].enabled }
        val visible = indices.toSet()
        val closingSegment = if (group.loop && enabled.size > 1) listOf(enabled.last() to enabled.first()) else emptyList()
        val segments = enabled.zipWithNext() + closingSegment
        LineBoxRenderer.draw3D(context, lineWidth = 1, depth = true) {
            for ((from, to) in segments) {
                if (from in visible && to in visible) {
                    draw3DLine(
                        group.points[from].destination, group.points[to].destination,
                        Color(group.pointColor(group.points[from], from)), Color(group.pointColor(group.points[to], to))
                    )
                }
            }
        }
    }

    private const val CURRENT_COLOR = 0x62ECA0
    private const val LABEL_BACKGROUND = 0x80000000.toInt()
    private const val FILL_ALPHA = 48
    private const val LINE_WIDTH = 2
    private const val SELECTED_WIDTH = 3
    private const val BEACON_WIDTH = 4
    private const val BEACON_HEIGHT = 24.0
    private const val LABEL_HEIGHT = 0.65
    private const val MARKER_RADIUS = 0.12
    private const val SELECTED_MARKER_RADIUS = 0.18
    private const val MAX_LABEL_SCALE = 3.0
}
