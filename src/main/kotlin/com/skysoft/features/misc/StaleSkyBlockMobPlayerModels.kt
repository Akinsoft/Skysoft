package com.skysoft.features.misc

import com.skysoft.config.SkysoftConfigGui
import com.skysoft.data.hypixel.HypixelLocationState
import com.skysoft.features.combat.SkyBlockMobEntityMatcher
import com.skysoft.features.combat.SkyBlockMobTextParser
import com.skysoft.utils.EntityUtilities.cleanName
import net.minecraft.client.Minecraft
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.entity.player.Player

internal object StaleSkyBlockMobPlayerModels {
    fun shouldHide(entity: Entity): Boolean {
        val player = entity as? Player ?: return false
        if (!SkysoftConfigGui.config().fixes.hideGlitchMobs || !HypixelLocationState.inSkyBlock) return false
        return player.isStaleSkyBlockMobPlayerModel()
    }

    fun hasStaleMobHealthProfile(health: Float, maxHealth: Float): Boolean =
        maxHealth > 0f &&
            maxHealth <= PLAYER_MODEL_MAX_HEALTH_LIMIT &&
            health >= MOB_HEALTH_MINIMUM &&
            health > maxHealth * MOB_TO_PLAYER_HEALTH_RATIO

    private fun Player.isStaleSkyBlockMobPlayerModel(): Boolean =
        this != Minecraft.getInstance().player &&
            !isRealPlayer() &&
            vehicle == null &&
            tickCount >= STALE_MOB_MIN_AGE_TICKS &&
            hasStaleMobHealthProfile(health, maxHealth) &&
            !hasVisibleMobNameplate()

    private fun Player.hasVisibleMobNameplate(): Boolean {
        val entities = SkyBlockMobEntityMatcher.allEntities()
        return entities.filterIsInstance<ArmorStand>().any { nameplate ->
            SkyBlockMobEntityMatcher.canPairWithNameplate(this, nameplate) &&
                SkyBlockMobTextParser.parseHealth(nameplate.cleanName()) != null &&
                SkyBlockMobEntityMatcher.physicalEntityFor(nameplate, entities) { candidate -> candidate == this } == this
        }
    }

    private fun Player.isRealPlayer(): Boolean =
        uuid.version() == REAL_PLAYER_UUID_VERSION

    private const val REAL_PLAYER_UUID_VERSION = 4
    private const val STALE_MOB_MIN_AGE_TICKS = 20
    private const val PLAYER_MODEL_MAX_HEALTH_LIMIT = 40f
    private const val MOB_HEALTH_MINIMUM = 1_000f
    private const val MOB_TO_PLAYER_HEALTH_RATIO = 10f
}
