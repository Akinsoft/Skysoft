package com.skysoft.features.slayer

import com.skysoft.config.SkysoftConfigGui
import com.skysoft.data.hypixel.HypixelLocationState
import com.skysoft.data.skyblock.SlayerQuestState
import com.skysoft.features.combat.SkyBlockMobEntityMatcher
import com.skysoft.features.combat.SkyBlockMobSignal
import com.skysoft.utils.ColorUtilities.toColor
import com.skysoft.utils.EntityUtilities.isVisibleToPlayer
import com.skysoft.utils.SkysoftClientEvents
import com.skysoft.utils.WorldVec
import com.skysoft.utils.render.EntityHighlightRenderer
import com.skysoft.utils.render.EntityHighlightTracker
import com.skysoft.utils.render.SkysoftRenderContext
import com.skysoft.utils.render.WorldRenderDispatcher
import com.skysoft.utils.toWorldVec
import net.minecraft.client.Minecraft
import net.minecraft.world.entity.LivingEntity
import java.util.UUID

object SlayerTargetHighlighting {
    private val config get() = SkysoftConfigGui.config().slayer.targetHighlighting
    private var targets = emptyList<SlayerHighlightTarget>()
    private val highlightedEntities = EntityHighlightTracker<LivingEntity>(this)
    private var ownedBossUuid: UUID? = null
    private var ticks = 0

    fun register() {
        SkysoftClientEvents.onEndTick(
            "Slayer Target Highlighting tick",
            isActive = { isActive() || targets.isNotEmpty() || highlightedEntities.isNotEmpty() },
        ) { onTick() }
        SkysoftClientEvents.onDisconnect("Slayer Target Highlighting disconnect reset", ::clear)
        WorldRenderDispatcher.registerHandler(
            "Slayer Target Highlighting world rendering",
            isActive = { isActive() && config.settings.targetLine && targets.isNotEmpty() },
            handler = ::renderWorld,
        )
    }

    private fun onTick() {
        if (!isActive()) {
            clear()
            return
        }
        if (++ticks % TARGET_SCAN_INTERVAL_TICKS != 0) return

        val bossNames = SlayerQuestState.bossNames
        val player = Minecraft.getInstance().player ?: return
        val signals = SkyBlockMobEntityMatcher.visibleSignals(SlayerQuestState.targetNames())
        val bossEntities = signals
            .filter { signal -> bossNames.any { bossName -> signal.label.equals(bossName, ignoreCase = true) } }
            .mapNotNullTo(mutableSetOf(), SkyBlockMobSignal::entity)
        if (!SlayerQuestState.isBossSpawned) {
            ownedBossUuid = null
        } else if (ownedBossUuid == null) {
            ownedBossUuid = bossEntities.minByOrNull(player::distanceToSqr)?.uuid
        }
        targets = signals.mapNotNull { signal ->
            val entity = signal.entity
            val kind = if (entity in bossEntities) SlayerTargetKind.BOSS else SlayerTargetKind.MINIBOSS
            if (kind == SlayerTargetKind.BOSS && entity?.uuid != ownedBossUuid) return@mapNotNull null
            SlayerHighlightTarget(
                kind = kind,
                location = signal.location,
                entity = entity,
            )
        }
        updateHighlights()
    }

    private fun updateHighlights() {
        val nextEntities = targets
            .asSequence()
            .filter(::shouldHighlight)
            .mapNotNullTo(mutableSetOf()) { target -> target.entity }
        highlightedEntities.replaceWith(nextEntities)

        val color = config.details.highlightColor.get().toColor()
        nextEntities.forEach { entity ->
            EntityHighlightRenderer.setEntityColor(entity, color, source = this) {
                isActive() && entity in highlightedEntities
            }
        }
    }

    private fun shouldHighlight(target: SlayerHighlightTarget): Boolean = when (target.kind) {
        SlayerTargetKind.BOSS -> config.settings.highlightBosses
        SlayerTargetKind.MINIBOSS -> config.settings.highlightMinibosses
    }

    private fun renderWorld(context: SkysoftRenderContext) {
        val playerLocation = Minecraft.getInstance().player?.position()?.toWorldVec() ?: return
        val visibleTargets = targets.filter { target -> target.entity?.isVisibleToPlayer() == true }
        val target = selectSlayerLineTarget(visibleTargets, playerLocation) ?: return
        context.drawLineToCrosshair(
            target.currentLocation(),
            config.details.targetLineColor.get().toColor(),
            depth = true,
        )
    }

    private fun clear() {
        highlightedEntities.clear()
        targets = emptyList()
        ownedBossUuid = null
        ticks = 0
    }

    private fun isActive(): Boolean =
        config.enabled && HypixelLocationState.inSkyBlock && SlayerQuestState.isActive

    private const val TARGET_SCAN_INTERVAL_TICKS = 4
}

internal data class SlayerHighlightTarget(
    val kind: SlayerTargetKind,
    val location: WorldVec,
    val entity: LivingEntity?,
) {
    fun currentLocation(): WorldVec = entity?.position()?.toWorldVec()?.let { position ->
        position + WorldVec(0.0, entity.bbHeight.toDouble() / 2.0, 0.0)
    } ?: location
}

internal enum class SlayerTargetKind {
    BOSS,
    MINIBOSS,
}

internal fun selectSlayerLineTarget(
    targets: List<SlayerHighlightTarget>,
    playerLocation: WorldVec,
): SlayerHighlightTarget? {
    val bosses = targets.filter { target -> target.kind == SlayerTargetKind.BOSS }
    return (bosses.ifEmpty { targets }).minByOrNull { target -> target.location.distanceSq(playerLocation) }
}
