package com.skysoft.features.event.diana

import com.skysoft.config.DianaLootshareDetailsConfig
import com.skysoft.utils.ColorUtilities.RGB_MASK
import com.skysoft.utils.ColorUtilities.toColor
import com.skysoft.utils.EntityUtilities.isVisibleToPlayer
import com.skysoft.utils.WorldVec
import com.skysoft.utils.render.BlockHighlightRenderer
import com.skysoft.utils.render.EntityLabelRenderer
import com.skysoft.utils.render.SkysoftRenderContext
import com.skysoft.utils.render.WorldCircleRenderer
import com.skysoft.utils.render.WorldLabelRenderer
import com.skysoft.utils.render.WorldLabelStyle
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.TextColor
import java.awt.Color

internal object DianaRareMobRenderer {
    fun renderWorld(
        context: SkysoftRenderContext,
        targets: Collection<DianaRareMobTarget>,
        currentTarget: DianaRareMobTarget?,
        showRareMobSharing: Boolean,
        showLootshare: Boolean,
        drawCrosshairLine: Boolean,
        drawLootshareRadius: Boolean,
        localPlayerName: String?,
        lootshareColors: DianaLootshareColors,
    ) {
        targets.forEach { target ->
            renderTarget(
                context,
                target,
                showRareMobSharing,
                showLootshare,
                drawLootshareRadius,
                localPlayerName,
                lootshareColors,
            )
        }
        if (showRareMobSharing && currentTarget != null && drawCrosshairLine) {
            val lineEntity = currentTarget.entity ?: currentTarget.nameplate
            if (lineEntity == null || lineEntity.isVisibleToPlayer()) {
                context.drawLineToCrosshair(
                    currentTarget.lineLocation(),
                    RARE_MOB_COLOR,
                    RARE_MOB_LINE_WIDTH,
                    depth = true,
                )
            }
        }
    }

    private fun renderTarget(
        context: SkysoftRenderContext,
        target: DianaRareMobTarget,
        showRareMobSharing: Boolean,
        showLootshare: Boolean,
        drawLootshareRadius: Boolean,
        localPlayerName: String?,
        lootshareColors: DianaLootshareColors,
    ) {
        if (!target.hasVisibleSignal()) {
            if (showRareMobSharing) renderWaypoint(context, target)
            return
        }
        if (showLootshare) renderLootshareUi(context, target, drawLootshareRadius, localPlayerName, lootshareColors)
    }

    private fun renderWaypoint(context: SkysoftRenderContext, target: DianaRareMobTarget) {
        BlockHighlightRenderer.drawBlock(
            context,
            target.location,
            RARE_MOB_COLOR,
            RARE_MOB_FILL_COLOR,
            RARE_MOB_LINE_WIDTH,
        )
        WorldLabelRenderer.draw(
            context,
            target.location + WAYPOINT_LABEL_OFFSET,
            listOf(
                Component.literal(target.mob.label.uppercase()).withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD),
                Component.literal("Found by ").withStyle(ChatFormatting.GRAY).append(target.sharedBy.nameComponent()),
            ),
            WAYPOINT_LABEL_STYLE,
        )
    }

    private fun renderLootshareUi(
        context: SkysoftRenderContext,
        target: DianaRareMobTarget,
        drawLootshareRadius: Boolean,
        localPlayerName: String?,
        lootshareColors: DianaLootshareColors,
    ) {
        val anchor = target.nameplate ?: target.entity ?: return
        if (!target.shouldShowLootshare(localPlayerName)) return
        EntityLabelRenderer.drawAboveNameTag(
            context,
            anchor,
            listOf(target.lootshareComponent(lootshareColors)),
            LOOTSHARE_LABEL_STYLE,
        )
        if (drawLootshareRadius && target.isCloseEnoughForLootshareRadius()) {
            val center = target.lineLocation()
            WorldCircleRenderer.drawHorizontalCircle(
                context,
                WorldVec(center.x, center.y + CIRCLE_Y_OFFSET, center.z),
                LOOTSHARE_RADIUS,
                lootshareColor(target, lootshareColors),
                CIRCLE_LINE_WIDTH,
            )
        }
    }

    private fun DianaRareMobTarget.lootshareComponent(lootshareColors: DianaLootshareColors): Component =
        if (lootshareEligible) {
            Component.literal("Lootsharing").withStyle { style ->
                style.withColor(TextColor.fromRgb(lootshareColors.ready.rgb and RGB_MASK)).withBold(true)
            }
        } else {
            Component.literal("Lootsharing").withStyle { style ->
                style.withColor(TextColor.fromRgb(lootshareColors.missing.rgb and RGB_MASK)).withStrikethrough(true)
            }
        }

    internal fun lootshareColor(target: DianaRareMobTarget, lootshareColors: DianaLootshareColors): Color =
        if (target.lootshareEligible) lootshareColors.ready else lootshareColors.missing

    private fun DianaRareMobTarget.isCloseEnoughForLootshareRadius(): Boolean {
        val playerLocation = DianaRareMobRuntime.playerLocation() ?: return false
        return lineLocation().distance(playerLocation) <= LOOTSHARE_RADIUS_RENDER_DISTANCE
    }

    private const val RARE_MOB_LINE_WIDTH = 3
    private const val CIRCLE_LINE_WIDTH = 2
    private const val LOOTSHARE_RADIUS = 30.0
    private const val LOOTSHARE_RADIUS_RENDER_DISTANCE = 50.0
    private const val CIRCLE_Y_OFFSET = 0.05
    private val RARE_MOB_COLOR = Color(255, 85, 255, 230)
    private val RARE_MOB_FILL_COLOR = Color(170, 0, 255, 60)
    private val WAYPOINT_LABEL_OFFSET = WorldVec(0.5, 1.8, 0.5)
    private val WAYPOINT_LABEL_STYLE = WorldLabelStyle(maxRenderDistance = 100.0, maxScale = 7.0)
    private val LOOTSHARE_LABEL_STYLE = WorldLabelStyle(maxRenderDistance = 80.0, maxScale = 6.0)
}

internal data class DianaLootshareColors(
    val missing: Color,
    val ready: Color,
)

internal fun DianaLootshareDetailsConfig.lootshareColors(): DianaLootshareColors =
    DianaLootshareColors(
        missing = lootshareMissingColor.get().toColor(),
        ready = lootshareReadyColor.get().toColor(),
    )
