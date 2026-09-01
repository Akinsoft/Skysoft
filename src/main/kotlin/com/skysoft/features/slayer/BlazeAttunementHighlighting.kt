package com.skysoft.features.slayer

import com.skysoft.config.SkysoftConfigGui
import com.skysoft.data.hypixel.HypixelLocationState
import com.skysoft.data.skyblock.SkyBlockSlayerType
import com.skysoft.data.skyblock.SlayerQuestState
import com.skysoft.features.combat.SkyBlockMobEntityMatcher
import com.skysoft.utils.EntityUtilities.cleanName
import com.skysoft.utils.SkysoftClientEvents
import com.skysoft.utils.render.EntityHighlightRenderer
import com.skysoft.utils.render.EntityHighlightTracker
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.entity.monster.Blaze
import net.minecraft.world.entity.monster.skeleton.WitherSkeleton
import net.minecraft.world.entity.monster.zombie.ZombifiedPiglin
import java.awt.Color

object BlazeAttunementHighlighting {
    private val config get() = SkysoftConfigGui.config().slayer
    private val highlightedEntities = EntityHighlightTracker<LivingEntity>(this)
    private var ticks = 0

    fun register() {
        SkysoftClientEvents.onEndTick(
            "Blaze Attunement Highlighting tick",
            isActive = { isActive() || highlightedEntities.isNotEmpty() },
        ) { onTick() }
        SkysoftClientEvents.onDisconnect("Blaze Attunement Highlighting disconnect reset", ::clear)
    }

    private fun onTick() {
        if (!isActive()) {
            clear()
            return
        }
        if (++ticks % SCAN_INTERVAL_TICKS != 0) return

        val entities = SkyBlockMobEntityMatcher.allEntities()
        val nextHighlights = entities.filterIsInstance<ArmorStand>().mapNotNull { nameplate ->
            val attunement = parseBlazeAttunement(nameplate.cleanName()) ?: return@mapNotNull null
            val entity = SkyBlockMobEntityMatcher.physicalEntityFor(nameplate, entities, ::isHellionShieldMob)
                ?.takeIf(LivingEntity::isAlive)
                ?: return@mapNotNull null
            entity to attunement.color
        }.toMap()

        highlightedEntities.replaceWith(nextHighlights.keys)

        nextHighlights.forEach { (entity, color) ->
            EntityHighlightRenderer.setEntityColor(
                entity = entity,
                color = color,
                source = this,
                fillOpacity = FILL_OPACITY,
                priority = ATTUNEMENT_PRIORITY,
            ) { isActive() && entity in highlightedEntities }
        }
    }

    private fun clear() {
        highlightedEntities.clear()
        ticks = 0
    }

    private fun isActive(): Boolean =
        config.blazeAttunementHighlights &&
            HypixelLocationState.inSkyBlock &&
            SlayerQuestState.isBossActive &&
            SlayerQuestState.slayerType == SkyBlockSlayerType.BLAZE

    private fun isHellionShieldMob(entity: LivingEntity): Boolean =
        entity is Blaze || entity is WitherSkeleton || entity is ZombifiedPiglin

    private const val SCAN_INTERVAL_TICKS = 4
    private const val FILL_OPACITY = 0.4f
    private const val ATTUNEMENT_PRIORITY = 1
}

internal enum class BlazeAttunement(val color: Color) {
    ASHEN(Color(0x555555)),
    SPIRIT(Color(0xFFFFFF)),
    AURIC(Color(0xFFFF55)),
    CRYSTAL(Color(0x55FFFF)),
}

internal fun parseBlazeAttunement(nameplate: String): BlazeAttunement? {
    val label = nameplate.substringBefore(' ')
    return BlazeAttunement.entries.firstOrNull { attunement -> attunement.name == label }
}
