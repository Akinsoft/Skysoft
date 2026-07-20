package com.skysoft.features.event.diana

import com.skysoft.config.DianaRareMobOption
import com.skysoft.config.SkysoftConfigGui
import com.skysoft.data.hypixel.HypixelLocationState
import com.skysoft.features.combat.SkyBlockMobEntityMatcher
import com.skysoft.features.combat.SkyBlockMobHealth
import com.skysoft.features.misc.StaleSkyBlockMobPlayerModels
import com.skysoft.utils.EntityUtilities.cleanName
import com.skysoft.utils.WorldVec
import net.minecraft.client.Minecraft
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.entity.player.Player

internal data class DianaRareMobSignal(
    val mob: DianaRareMobOption,
    val location: WorldVec,
    val entity: LivingEntity?,
    val nameplate: ArmorStand?,
    val health: SkyBlockMobHealth?,
)

internal object DianaRareMobEntityMatcher {
    fun visibleSignals(): List<DianaRareMobSignal> =
        SkyBlockMobEntityMatcher.visibleSignals(ALL_RARE_MOB_LABELS).mapNotNull { signal ->
            val rareMob = DianaRareMobOption.fromLabel(signal.label) ?: return@mapNotNull null
            DianaRareMobSignal(
                mob = rareMob,
                location = signal.location,
                entity = signal.entity,
                nameplate = signal.nameplate,
                health = signal.health,
            )
        }

    @JvmStatic
    fun shouldHideBuggedEntity(entity: Entity): Boolean =
        shouldHideStaleRarePlayerModel(entity) ||
            StaleSkyBlockMobPlayerModels.shouldHide(entity) ||
            shouldHideBuggedNameplate(entity)

    @JvmStatic
    fun shouldHideStaleRarePlayerModel(entity: Entity): Boolean {
        val config = SkysoftConfigGui.config()
        if (!shouldCheckStaleRarePlayerModels(config.fixes.hideGlitchMobs, HypixelLocationState.inSkyBlock)) {
            return false
        }
        val player = entity as? Player ?: return false
        if (player == Minecraft.getInstance().player || player.isRealPlayer()) return false
        val label = labelFromName(player.cleanName(), ALL_RARE_MOB_LABELS) ?: return false
        val labels = DianaRareMobOption.fromLabel(label)?.matchLabels ?: setOf(label)
        return SkyBlockMobEntityMatcher.visibleSignals(labels).none { signal -> signal.entity?.id == player.id }
    }

    fun shouldCheckStaleRarePlayerModels(hideGlitchMobs: Boolean, inSkyBlock: Boolean): Boolean =
        hideGlitchMobs && inSkyBlock

    fun isBuggedNameplateText(name: String): Boolean =
        name in BUGGED_NAMEPLATES

    private fun shouldHideBuggedNameplate(entity: Entity): Boolean {
        val config = SkysoftConfigGui.config()
        if (!config.fixes.hideBuggedNameplates) return false
        return entity is ArmorStand && isBuggedNameplateText(entity.cleanName())
    }

    private fun Player.isRealPlayer(): Boolean =
        uuid.version() == REAL_PLAYER_UUID_VERSION

    private fun labelFromName(name: String, labels: Collection<String>): String? =
        labels.firstOrNull { label -> name.contains(label, ignoreCase = true) }

    private const val REAL_PLAYER_UUID_VERSION = 4
    private val BUGGED_NAMEPLATES = setOf("☣ Bleeds: -")
    private val ALL_RARE_MOB_LABELS = DianaRareMobOption.entries.flatMap { it.matchLabels }
}
