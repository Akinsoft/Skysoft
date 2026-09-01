package com.skysoft.features.event.diana

import com.skysoft.config.DianaRareMobOption
import com.skysoft.data.ClientEntitySnapshot
import com.skysoft.config.SkysoftConfigGui
import com.skysoft.data.hypixel.HypixelLocationState
import com.skysoft.features.combat.SkyBlockMobEntityMatcher
import com.skysoft.features.combat.SkyBlockMob
import com.skysoft.features.combat.SkyBlockMobHealth
import com.skysoft.features.misc.StaleSkyBlockMobPlayerModels
import com.skysoft.utils.EntityUtilities.cleanName
import com.skysoft.utils.WorldVec
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.entity.player.Player

internal data class DianaRareMobSignal(
    val mob: DianaRareMobOption,
    val trackedMob: SkyBlockMob,
) {
    val location: WorldVec
        get() = trackedMob.location
    val health: SkyBlockMobHealth?
        get() = trackedMob.health
}

internal object DianaRareMobEntityMatcher {
    private var checkedLevel: ClientLevel? = null
    private var checkedTick = Long.MIN_VALUE
    private val stalePlayerModels = mutableMapOf<Int, Boolean>()

    fun visibleSignals(): List<DianaRareMobSignal> =
        SkyBlockMobEntityMatcher.visibleSignals(ALL_RARE_MOB_LABELS).mapNotNull { signal ->
            val rareMob = DianaRareMobOption.fromLabel(signal.label) ?: return@mapNotNull null
            DianaRareMobSignal(
                mob = rareMob,
                trackedMob = signal.trackedMob,
            )
        }

    @JvmStatic
    fun shouldHideBuggedEntity(entity: Entity): Boolean = when (entity) {
        is Player -> shouldHideStaleRarePlayerModel(entity) || StaleSkyBlockMobPlayerModels.shouldHide(entity)
        is ArmorStand -> shouldHideBuggedNameplate(entity)
        else -> false
    }

    @JvmStatic
    fun shouldHideStaleRarePlayerModel(entity: Entity): Boolean {
        val player = entity as? Player ?: return false
        val config = SkysoftConfigGui.config()
        if (!shouldCheckStaleRarePlayerModels(config.fixes.hideGlitchMobs, HypixelLocationState.inSkyBlock)) {
            return false
        }
        val minecraft = Minecraft.getInstance()
        if (player == minecraft.player || player.isRealPlayer() || player.vehicle != null) return false
        val level = minecraft.level ?: return false
        if (checkedLevel !== level || checkedTick != level.gameTime) {
            checkedLevel = level
            checkedTick = level.gameTime
            stalePlayerModels.clear()
        }
        return stalePlayerModels.getOrPut(player.id) {
            val label = labelFromName(player.cleanName(), ALL_RARE_MOB_LABELS) ?: return@getOrPut false
            val labels = DianaRareMobOption.fromLabel(label)?.matchLabels ?: setOf(label)
            !SkyBlockMobEntityMatcher.hasVisibleNameplateFor(player, labels, ClientEntitySnapshot.entities())
        }
    }

    fun shouldCheckStaleRarePlayerModels(hideGlitchMobs: Boolean, inSkyBlock: Boolean): Boolean =
        hideGlitchMobs && inSkyBlock

    fun isBuggedNameplateText(name: String): Boolean =
        name in BUGGED_NAMEPLATES

    private fun shouldHideBuggedNameplate(entity: Entity): Boolean {
        val nameplate = entity as? ArmorStand ?: return false
        if (!SkysoftConfigGui.config().fixes.hideBuggedNameplates) return false
        if (nameplate.customName?.string?.contains(BUGGED_NAMEPLATE_MARKER) != true) return false
        return isBuggedNameplateText(nameplate.cleanName())
    }

    private fun Player.isRealPlayer(): Boolean =
        uuid.version() == REAL_PLAYER_UUID_VERSION

    private fun labelFromName(name: String, labels: Collection<String>): String? =
        labels.firstOrNull { label -> name.contains(label, ignoreCase = true) }

    private const val REAL_PLAYER_UUID_VERSION = 4
    private const val BUGGED_NAMEPLATE_MARKER = "Bleeds"
    private val BUGGED_NAMEPLATES = setOf("☣ Bleeds: -")
    private val ALL_RARE_MOB_LABELS = DianaRareMobOption.entries.flatMap { it.matchLabels }
}
