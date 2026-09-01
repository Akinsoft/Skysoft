package com.skysoft.features.slayer

import com.skysoft.config.SkysoftConfigGui
import com.skysoft.data.hypixel.HypixelLocationState
import com.skysoft.data.skyblock.SlayerQuestState
import com.skysoft.features.combat.SkyBlockMobEntityMatcher
import com.skysoft.features.combat.SkyBlockMobSignal
import com.skysoft.utils.ColorUtilities.toColor
import com.skysoft.utils.EntityUtilities.cleanName
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
import net.minecraft.world.entity.decoration.ArmorStand

object SlayerTargetHighlighting {
    private val config get() = SkysoftConfigGui.config().slayer.targetHighlighting
    private var targets = emptyList<SlayerHighlightTarget>()
    private val highlightedEntities = EntityHighlightTracker<LivingEntity>(this)
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
        val playerName = Minecraft.getInstance().player?.gameProfile?.name ?: return
        val entities = SkyBlockMobEntityMatcher.allEntities()
        val ownerLabels = entities.filterIsInstance<ArmorStand>().mapNotNull(ArmorStand::slayerBossOwnerLabel)
        targets = SkyBlockMobEntityMatcher.visibleSignals(SlayerQuestState.targetNames()).mapNotNull { signal ->
            val entity = signal.entity
            val kind = if (bossNames.any { bossName -> signal.label.equals(bossName, ignoreCase = true) }) {
                SlayerTargetKind.BOSS
            } else {
                SlayerTargetKind.MINIBOSS
            }
            if (kind == SlayerTargetKind.BOSS && !signal.isOwnedBy(playerName, ownerLabels)) return@mapNotNull null
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
        ticks = 0
    }

    private fun isActive(): Boolean =
        config.enabled && HypixelLocationState.inSkyBlock && SlayerQuestState.isActive

    private const val TARGET_SCAN_INTERVAL_TICKS = 4
}

private data class SlayerBossOwnerLabel(
    val playerName: String,
    val entity: ArmorStand,
)

private fun ArmorStand.slayerBossOwnerLabel(): SlayerBossOwnerLabel? {
    val name = cleanName()
    if (!name.startsWith(SLAYER_BOSS_OWNER_PREFIX)) return null
    return name.removePrefix(SLAYER_BOSS_OWNER_PREFIX)
        .trim()
        .takeIf(String::isNotEmpty)
        ?.let { playerName -> SlayerBossOwnerLabel(playerName, this) }
}

private fun SkyBlockMobSignal.isOwnedBy(
    playerName: String,
    ownerLabels: List<SlayerBossOwnerLabel>,
): Boolean {
    val bossNameplate = nameplate ?: return false
    return ownerLabels
        .asSequence()
        .filter { ownerLabel -> ownerLabel.entity.isAbove(bossNameplate) }
        .minByOrNull { ownerLabel -> ownerLabel.entity.distanceToSqr(bossNameplate) }
        ?.playerName
        ?.equals(playerName, ignoreCase = true) == true
}

private fun ArmorStand.isAbove(nameplate: ArmorStand): Boolean {
    val dx = x - nameplate.x
    val dz = z - nameplate.z
    val verticalOffset = y - nameplate.y
    return dx * dx + dz * dz <= OWNER_LABEL_HORIZONTAL_DISTANCE_SQ &&
        verticalOffset in 0.0..OWNER_LABEL_MAX_VERTICAL_DISTANCE
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

private const val SLAYER_BOSS_OWNER_PREFIX = "Spawned by: "
private const val OWNER_LABEL_HORIZONTAL_DISTANCE_SQ = 0.25
private const val OWNER_LABEL_MAX_VERTICAL_DISTANCE = 2.0
