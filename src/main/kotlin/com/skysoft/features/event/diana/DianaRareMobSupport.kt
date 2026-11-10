package com.skysoft.features.event.diana

import com.skysoft.data.ClientEntitySnapshot
import com.skysoft.events.entity.ClientEntityMetadataEvent
import com.skysoft.features.combat.DamageSplash
import com.skysoft.features.combat.DamageSplashAttribution
import com.skysoft.features.combat.DamageSplashTargetView
import com.skysoft.features.combat.DamageSplashText
import com.skysoft.utils.location
import com.skysoft.utils.render.EntityHighlightRenderer
import net.minecraft.client.Minecraft
import net.minecraft.world.entity.decoration.ArmorStand
import java.awt.Color

internal object DianaRareMobLootshare {
    fun handleMetadata(
        event: ClientEntityMetadataEvent,
        targets: Collection<DianaRareMobTarget>,
        localPlayerName: String?,
        now: Long,
    ) {
        val entity = Minecraft.getInstance().level?.getEntity(event.entityId) as? ArmorStand ?: return
        DamageSplashText.fromMetadata(event.entityId, entity.location, event.packedItems)
            .forEach { splash -> handleDamageSplash(splash, targets, localPlayerName, now) }
    }

    fun scan(targets: Collection<DianaRareMobTarget>, localPlayerName: String?, now: Long) {
        if (targets.none { it.shouldShowLootshare(localPlayerName) }) return
        ClientEntitySnapshot.entities()
            .filterIsInstance<ArmorStand>()
            .forEach { armorStand -> tryHandleDamageSplash(armorStand, targets, localPlayerName, now) }
    }

    fun tryHandleDamageSplash(
        armorStand: ArmorStand,
        targets: Collection<DianaRareMobTarget>,
        localPlayerName: String?,
        now: Long,
    ): Boolean {
        val splash = DamageSplashText.fromEntity(armorStand) ?: return false
        handleDamageSplash(splash, targets, localPlayerName, now)
        return true
    }

    private fun handleDamageSplash(
        splash: DamageSplash,
        targets: Collection<DianaRareMobTarget>,
        localPlayerName: String?,
        now: Long,
    ) {
        val attributedTarget = DamageSplashAttribution.attribute(splash, targets, now) { target ->
            DamageSplashTargetView(
                active = target.shouldShowLootshare(localPlayerName),
                lastAttack = target.lastLocalAttack,
                processedSplashIds = target.processedDamageSplashIds,
                attributionLocations = target.damageAttributionLocations(),
                targetEntityUuids = target.targetEntityUuids(),
                lastHealthChangeAtMillis = target.lastHealthChangeAtMillis,
            )
        } ?: return
        if (attributedTarget.addAttributedLocalDamage(splash.damage)) {
            DianaLootshareReadyMessage.broadcast()
        }
    }
}

internal object DianaRareMobGlow {
    fun apply(
        targets: Collection<DianaRareMobTarget>,
        localPlayerName: String?,
        lootshareEnabled: Boolean,
        lootshareColors: DianaLootshareColors,
    ) {
        targets.forEach { target ->
            val entity = target.entity ?: return@forEach
            val color = target.glowColor(localPlayerName, lootshareEnabled, lootshareColors)
            if (target.glowColor == color) return@forEach
            target.glowColor = color
            EntityHighlightRenderer.setEntityColor(entity, color, source = this) {
                target in targets && entity.isAlive
            }
        }
    }

    private fun DianaRareMobTarget.glowColor(
        localPlayerName: String?,
        lootshareEnabled: Boolean,
        lootshareColors: DianaLootshareColors,
    ): Color =
        when {
            isSpawner(localPlayerName) || !lootshareEnabled -> RARE_MOB_GLOW_COLOR
            else -> DianaRareMobRenderer.lootshareColor(this, lootshareColors)
        }

    private val RARE_MOB_GLOW_COLOR = Color(255, 85, 255, 230)
}
