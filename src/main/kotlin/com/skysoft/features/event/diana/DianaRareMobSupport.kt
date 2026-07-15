package com.skysoft.features.event.diana

import com.skysoft.events.entity.ClientEntityMetadataEvent
import com.skysoft.features.combat.DamageSplash
import com.skysoft.features.combat.DamageSplashAttribution
import com.skysoft.features.combat.DamageSplashTargetView
import com.skysoft.features.combat.DamageSplashText
import com.skysoft.utils.WorldVec
import com.skysoft.utils.chat.ChatMessageSender
import com.skysoft.utils.chat.ChatSenderParser
import com.skysoft.utils.chat.ChatMessage
import com.skysoft.utils.chat.SkysoftPartyShare
import com.skysoft.utils.render.EntityHighlightRenderer
import com.skysoft.utils.toWorldVec
import net.minecraft.client.Minecraft
import net.minecraft.world.entity.decoration.ArmorStand
import java.awt.Color

internal object DianaRareMobRuntime {
    fun playerLocation(): WorldVec? =
        minecraftOrNull()?.player?.position()?.toWorldVec()

    fun localPlayerName(): String? =
        minecraftOrNull()?.player?.gameProfile?.name

    fun shareKey(sender: String, share: DianaRareMobShare): String =
        "${sender.lowercase()}:${share.mob.name}:${share.location.blockKey()}"

    fun senderFor(message: ChatMessage, share: DianaRareMobShare): ChatMessageSender? =
        senderFor(message, share.marker)

    fun senderFor(message: ChatMessage, marker: String): ChatMessageSender? =
        ChatSenderParser.senderBefore(message, marker)

    private fun minecraftOrNull(): Minecraft? =
        Minecraft.getInstance()
}

internal object DianaRareMobLootshare {
    fun handleMetadata(
        event: ClientEntityMetadataEvent,
        targets: Collection<DianaRareMobTarget>,
        localPlayerName: String?,
        now: Long,
    ) {
        val entity = Minecraft.getInstance().level?.getEntity(event.entityId) as? ArmorStand ?: return
        DamageSplashText.fromMetadata(event.entityId, entity.position().toWorldVec(), event.packedItems)
            .forEach { splash ->
                tryHandleDamageSplash(splash, targets, localPlayerName, now)
            }
    }

    fun scan(targets: Collection<DianaRareMobTarget>, localPlayerName: String?, now: Long) {
        if (targets.none { it.shouldShowLootshare(localPlayerName) }) return
        DianaRareMobEntityMatcher.allEntities()
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
        return tryHandleDamageSplash(splash, targets, localPlayerName, now)
    }

    fun tryHandleDamageSplash(
        splash: DamageSplash,
        targets: Collection<DianaRareMobTarget>,
        localPlayerName: String?,
        now: Long,
    ): Boolean {
        val attribution = DamageSplashAttribution.attribute(splash, targets, now) { target ->
            DamageSplashTargetView(
                active = target.shouldShowLootshare(localPlayerName),
                lastAttack = target.lastLocalAttack,
                processedSplashIds = target.processedDamageSplashIds,
                attributionLocations = target.damageAttributionLocations(),
                targetEntityIds = target.targetEntityIds(),
                lastHealthChangeAtMillis = target.lastHealthChangeAtMillis,
            )
        } ?: return true
        val target = attribution.target
        if (target.addAttributedLocalDamage(attribution.splash.damage) == LootshareEligibilityResult.BECAME_ELIGIBLE) {
            DianaLootshareReadyMessage.broadcast()
        }
        return true
    }
}

internal object DianaRareMobGlow {
    fun apply(
        targets: Collection<DianaRareMobTarget>,
        localPlayerName: String?,
        lootshareColors: DianaLootshareColors,
    ) {
        targets.forEach { target ->
            val entity = target.entity ?: return@forEach
            val color = target.glowColor(localPlayerName, lootshareColors)
            if (target.glowColor == color) return@forEach
            target.glowColor = color
            EntityHighlightRenderer.setEntityColor(entity, color) { target in targets && entity.isAlive }
        }
    }

    private fun DianaRareMobTarget.glowColor(localPlayerName: String?, lootshareColors: DianaLootshareColors): Color =
        when {
            isSpawner(localPlayerName) -> RARE_MOB_GLOW_COLOR
            else -> DianaRareMobRenderer.lootshareColor(this, lootshareColors)
        }

    private val RARE_MOB_GLOW_COLOR = Color(255, 85, 255, 230)
}

internal object DianaRareMobPartyEcho {
    fun shouldHideRecentlySent(
        message: ChatMessage,
        sender: ChatMessageSender,
        localPlayerName: String?,
        now: Long,
    ): Boolean {
        if (!sender.isLocalPlayer(localPlayerName)) return false
        return SkysoftPartyShare.consumeRecentSentMessage(message.body, now)
    }
}
