package com.skysoft.features.fishing

import com.skysoft.config.WaypointLabelFormat
import com.skysoft.utils.WorldVec
import com.skysoft.utils.chat.ChatMessageSender
import com.skysoft.utils.toWorldVec
import com.skysoft.utils.render.BlockHighlightRenderer
import com.skysoft.utils.render.SkysoftRenderContext
import com.skysoft.utils.render.WorldLabelRenderer
import com.skysoft.utils.render.WorldLabelStyle
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import java.awt.Color
import kotlin.math.abs

internal data class FishingHotspotWaypoint(
    val share: FishingHotspotShare,
    val sharedBy: ChatMessageSender,
    val expiresAtMillis: Long,
) {
    fun shouldRemove(playerLocation: WorldVec?, now: Long): Boolean {
        if (expiresAtMillis <= now) return true
        val position = playerLocation ?: return false
        val blockCenter = share.location.blockCenter()
        val dx = position.x - blockCenter.x
        val dz = position.z - blockCenter.z
        return dx * dx + dz * dz <= ARRIVAL_HORIZONTAL_RANGE_SQ &&
            abs(position.y - share.location.y) <= ARRIVAL_VERTICAL_RANGE
    }
}

internal object FishingHotspotWaypointRenderer {
    fun renderWorld(
        context: SkysoftRenderContext,
        waypoints: Collection<FishingHotspotWaypoint>,
        drawCrosshairLine: Boolean,
        boldLabel: Boolean,
        labelFormat: WaypointLabelFormat,
    ) {
        waypoints
            .sortedByDescending { it.share.location.distanceSq(context.camera.position().toWorldVec()) }
            .forEach { waypoint ->
                BlockHighlightRenderer.drawBlock(
                    context,
                    waypoint.share.location,
                    WAYPOINT_COLOR,
                    FILL_COLOR,
                    WAYPOINT_LINE_WIDTH,
                )
                drawWaypointText(context, waypoint, boldLabel, labelFormat)
                if (drawCrosshairLine) {
                    context.drawLineToCrosshair(waypoint.share.location.blockCenter(), WAYPOINT_COLOR)
                }
            }
    }

    private fun drawWaypointText(
        context: SkysoftRenderContext,
        waypoint: FishingHotspotWaypoint,
        boldLabel: Boolean,
        labelFormat: WaypointLabelFormat,
    ) {
        WorldLabelRenderer.draw(
            context,
            waypoint.share.location + TEXT_ANCHOR_OFFSET,
            waypoint.labelLines(boldLabel, labelFormat),
            HOTSPOT_LABEL_STYLE,
        )
    }

    private fun FishingHotspotWaypoint.labelLines(
        boldLabel: Boolean,
        labelFormat: WaypointLabelFormat,
    ): List<Component> =
        listOf(
            Component.literal(labelFormat.format("Hotspot"))
                .withStyle(ChatFormatting.LIGHT_PURPLE)
                .withStyle { style -> style.withBold(boldLabel) },
            Component.literal(share.cleanStat).withStyle(ChatFormatting.AQUA),
            Component.literal("Shared by ")
                .withStyle(ChatFormatting.GRAY)
                .append(sharedBy.nameComponent()),
        )

    private const val WAYPOINT_LINE_WIDTH = 3
    private val WAYPOINT_COLOR = Color(255, 85, 255, 220)
    private val FILL_COLOR = Color(170, 0, 255, 70)
    private val HOTSPOT_LABEL_STYLE = WorldLabelStyle()
    private val TEXT_ANCHOR_OFFSET = WorldVec(0.5, 1.8, 0.5)
}

private const val ARRIVAL_HORIZONTAL_RANGE_SQ = 20.0 * 20.0
private const val ARRIVAL_VERTICAL_RANGE = 12.0
