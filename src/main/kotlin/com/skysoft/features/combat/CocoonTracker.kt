package com.skysoft.features.combat

import com.skysoft.config.DianaRareMobOption
import com.skysoft.config.SkysoftConfigGui
import com.skysoft.data.ClientEntitySnapshot
import com.skysoft.data.hypixel.HypixelLocationState
import com.skysoft.data.skyblock.SkyBlockItemUtilities.playerHeadTexture
import com.skysoft.data.skyblock.SlayerMessageParser
import com.skysoft.data.skyblock.SlayerQuestState
import com.skysoft.events.entity.EntityInteractionEvent
import com.skysoft.events.entity.EntityInteractionEvents
import com.skysoft.utils.EntityUtilities.cleanName
import com.skysoft.utils.EntityUtilities.isVisibleToPlayer
import com.skysoft.utils.SkysoftClientEvents
import com.skysoft.utils.WorldVec
import com.skysoft.utils.chat.ChatEvents
import com.skysoft.utils.chat.ChatMessageVisibility
import com.skysoft.utils.render.SkysoftRenderContext
import com.skysoft.utils.render.WorldLabelRenderer
import com.skysoft.utils.render.WorldLabelStyle
import com.skysoft.utils.render.WorldRenderDispatcher
import com.skysoft.utils.toWorldVec
import java.util.Locale
import kotlin.math.abs
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.item.ItemStack

object CocoonTracker {
    private val config get() = SkysoftConfigGui.config().combat.cocoonDisplay
    private val cocoons = mutableListOf<TrackedCocoon>()
    private val pendingMessages = mutableListOf<PendingCocoonMessage>()
    private var lastLocalAttack: RecentLocalAttack? = null
    private var ticks = 0

    fun register() {
        ChatEvents.onVisibleMessage(
            "Cocoon chat",
            isActive = ::isActive,
        ) { message ->
            if (message.isSystemLike) {
                val localCocoon = CocoonMessageParser.parseLocal(message.cleanText)
                when {
                    localCocoon != null -> handleLocalCocoon(
                        mobName = localCocoon.mobName,
                        isSlayerCocoon = SlayerQuestState.isSlayerTarget(localCocoon.mobName),
                    )
                    SlayerMessageParser.isBossCocooned(message.cleanText) -> handleLocalCocoon(
                        mobName = SlayerQuestState.bossName,
                        isSlayerCocoon = true,
                    )
                }
            }
            ChatMessageVisibility.SHOW
        }
        EntityInteractionEvents.register("Cocoon local attack", ::isActive) { event ->
            if (event.action == EntityInteractionEvent.ActionType.ATTACK) {
                lastLocalAttack = RecentLocalAttack(
                    entityId = event.clickedEntity.id,
                    location = event.clickedEntity.position().toWorldVec(),
                    attackedAtMillis = System.currentTimeMillis(),
                )
            }
            false
        }
        SkysoftClientEvents.onEndTick(
            "Cocoon tracking",
            isActive = {
                isActive() || cocoons.isNotEmpty() || pendingMessages.isNotEmpty() || lastLocalAttack != null
            },
        ) { onTick() }
        SkysoftClientEvents.onDisconnect("Cocoon disconnect reset", ::clear)
        WorldRenderDispatcher.registerHandler(
            "Cocoon world rendering",
            isActive = { isActive() && cocoons.isNotEmpty() },
            handler = ::renderWorld,
        )
    }

    internal fun handleEquipment(entityId: Int, equipment: Collection<ItemStack>) {
        if (!isActive() || equipment.none { it.playerHeadTexture() == COCOON_HEAD_TEXTURE }) return
        val entity = Minecraft.getInstance().level?.getEntity(entityId) as? ArmorStand ?: return
        if (!entity.isVisibleToPlayer()) return
        rememberCocoon(entityId, entity.position().toWorldVec(), System.currentTimeMillis())
    }

    private fun handleLocalCocoon(mobName: String?, isSlayerCocoon: Boolean) {
        val now = System.currentTimeMillis()
        val playerLocation = Minecraft.getInstance().player?.position()?.toWorldVec()
        val message = PendingCocoonMessage(
            mobName = mobName,
            killedEntityLocation = recentKilledEntityLocation(now),
            playerLocation = playerLocation,
            receivedAtMillis = now,
            isSlayerCocoon = isSlayerCocoon,
        )
        val cocoon = cocoons
            .asSequence()
            .filter { isWithinMessageLinkWindow(it.detectedAtMillis, now) }
            .filter { tracked -> !tracked.isLocal }
            .minWithOrNull(cocoonComparator(message))
        if (cocoon != null) {
            claimCocoon(cocoon, message)
        } else {
            pendingMessages += message
        }
    }

    private fun rememberCocoon(entityId: Int, location: WorldVec, now: Long) {
        val existing = cocoons.firstOrNull { tracked ->
            now < tracked.expiresAtMillis &&
                (entityId in tracked.entityIds || areSameCocoon(tracked.location, location))
        }
        if (existing != null) {
            existing.entityIds += entityId
            return
        }

        val cocoon = TrackedCocoon(
            entityIds = mutableSetOf(entityId),
            location = location,
            detectedAtMillis = now,
            expiresAtMillis = now + COCOON_LIFETIME_MILLIS,
        )
        cocoons += cocoon
        val pending = pendingMessages
            .asSequence()
            .filter { message -> isWithinMessageLinkWindow(now, message.receivedAtMillis) }
            .minWithOrNull(messageComparator(cocoon))
        if (pending != null) {
            pendingMessages.remove(pending)
            claimCocoon(cocoon, pending)
        }
    }

    private fun updateMissingMobNames() {
        val unknownCocoons = cocoons.filter { cocoon ->
            cocoon.mobName == null && cocoon.isVisibleToPlayer()
        }
        if (unknownCocoons.isEmpty()) return

        val matches = arrayOfNulls<CocoonNameMatch>(unknownCocoons.size)
        ClientEntitySnapshot.entities().asSequence()
            .filterIsInstance<ArmorStand>()
            .forEach { entity ->
                val entityLocation = entity.position().toWorldVec()
                var nearestIndex = -1
                var nearestDistanceSq = Double.MAX_VALUE
                unknownCocoons.forEachIndexed { index, cocoon ->
                    if (!isPossibleMobNameplate(cocoon.location, entityLocation)) return@forEachIndexed
                    val distanceSq = entityLocation.distanceSq(cocoon.location)
                    if (distanceSq < nearestDistanceSq) {
                        nearestIndex = index
                        nearestDistanceSq = distanceSq
                    }
                }
                if (nearestIndex < 0) return@forEach
                val mobName = SkyBlockMobTextParser.parseName(entity.cleanName()) ?: return@forEach
                val previous = matches[nearestIndex]
                if (previous == null || nearestDistanceSq < previous.distanceSq) {
                    matches[nearestIndex] = CocoonNameMatch(mobName, nearestDistanceSq)
                }
            }
        matches.forEachIndexed { index, match ->
            match?.let { updateMobName(unknownCocoons[index], it.mobName) }
        }
    }

    private fun onTick() {
        if (!isActive()) {
            clear()
            return
        }
        val now = System.currentTimeMillis()
        pendingMessages.removeIf { now - it.receivedAtMillis > MESSAGE_LINK_WINDOW_MILLIS }
        cocoons.removeIf { now >= it.expiresAtMillis }
        if (lastLocalAttack?.let { now - it.attackedAtMillis > MESSAGE_LINK_WINDOW_MILLIS } == true) {
            lastLocalAttack = null
        }
        if (++ticks % NAME_SCAN_INTERVAL_TICKS == 0) {
            updateMissingMobNames()
        }
    }

    private fun renderWorld(context: SkysoftRenderContext) {
        val now = System.currentTimeMillis()
        cocoons.asSequence()
            .filter { cocoon -> cocoon.isVisibleToPlayer() }
            .filter(::shouldRender)
            .forEach { cocoon ->
                val remainingMillis = (cocoon.expiresAtMillis - now).coerceAtLeast(0L)
                WorldLabelRenderer.draw(
                    context,
                    cocoon.location + LABEL_OFFSET,
                    listOf(
                        Component.literal(cocoon.mobName ?: "Unknown Mob")
                            .withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD),
                        Component.literal(
                            (if (config.settings.showTimerPrefix) "Hatches in " else "") +
                                formatCocoonTime(remainingMillis),
                        )
                            .withStyle(timerColor(remainingMillis)),
                    ),
                    LABEL_STYLE,
                )
            }
    }

    private fun shouldRender(cocoon: TrackedCocoon): Boolean {
        if (
            !shouldShowCocoonForOwnership(
                isLocal = cocoon.isLocal,
                isSlayerCocoon = cocoon.isSlayerCocoon,
                isSlayerQuestActive = SlayerQuestState.isActive,
                hasMobName = cocoon.mobName != null,
            )
        ) {
            return false
        }
        if (!config.settings.onlySlayerTargets || !SlayerQuestState.isActive) return true
        if (cocoon.matchedTargetFilter) return true
        val mobName = cocoon.mobName ?: return false
        return shouldShowFilteredCocoon(
            wasPreviouslyMatched = false,
            isSlayerTarget = SlayerQuestState.isSlayerTarget(mobName),
            isDianaRareMob = DianaRareMobOption.fromMobName(mobName) != null,
        ).also { cocoon.matchedTargetFilter = it }
    }

    private fun updateMobName(cocoon: TrackedCocoon, mobName: String) {
        cocoon.mobName = mobName
        val isSlayerTarget = SlayerQuestState.isSlayerTarget(mobName)
        cocoon.isSlayerCocoon = cocoon.isSlayerCocoon || isSlayerTarget
        cocoon.matchedTargetFilter = shouldShowFilteredCocoon(
            wasPreviouslyMatched = cocoon.matchedTargetFilter,
            isSlayerTarget = isSlayerTarget,
            isDianaRareMob = DianaRareMobOption.fromMobName(mobName) != null,
        )
    }

    private fun claimCocoon(cocoon: TrackedCocoon, message: PendingCocoonMessage) {
        cocoon.isLocal = true
        if (message.isSlayerCocoon) {
            cocoon.isSlayerCocoon = true
            cocoon.matchedTargetFilter = true
        }
        message.mobName?.let { updateMobName(cocoon, it) }
    }

    private fun TrackedCocoon.isVisibleToPlayer(): Boolean {
        val level = Minecraft.getInstance().level ?: return false
        return entityIds.asSequence()
            .mapNotNull { entityId -> level.getEntity(entityId) }
            .any { entity -> !entity.isRemoved && entity.isVisibleToPlayer() }
    }

    private fun recentKilledEntityLocation(now: Long): WorldVec? {
        val attack = lastLocalAttack
            ?.takeIf { recent -> now - recent.attackedAtMillis in 0..MESSAGE_LINK_WINDOW_MILLIS }
            ?: return null
        val level = Minecraft.getInstance().level ?: return null
        val attackedEntity = level.getEntity(attack.entityId)
        return attack.location.takeIf {
            attackedEntity == null || (attackedEntity is LivingEntity && attackedEntity.isDeadOrDying)
        }
    }

    private fun clear() {
        cocoons.clear()
        pendingMessages.clear()
        lastLocalAttack = null
        ticks = 0
    }

    private fun isActive(): Boolean =
        config.enabled && HypixelLocationState.inSkyBlock

    private fun timerColor(remainingMillis: Long): ChatFormatting = when {
        remainingMillis > TIMER_GREEN_THRESHOLD_MILLIS -> ChatFormatting.GREEN
        remainingMillis > TIMER_YELLOW_THRESHOLD_MILLIS -> ChatFormatting.YELLOW
        else -> ChatFormatting.RED
    }

    private data class TrackedCocoon(
        val entityIds: MutableSet<Int>,
        val location: WorldVec,
        val detectedAtMillis: Long,
        val expiresAtMillis: Long,
        var mobName: String? = null,
        var matchedTargetFilter: Boolean = false,
        var isLocal: Boolean = false,
        var isSlayerCocoon: Boolean = false,
    )

    private data class PendingCocoonMessage(
        val mobName: String?,
        val killedEntityLocation: WorldVec?,
        val playerLocation: WorldVec?,
        val receivedAtMillis: Long,
        val isSlayerCocoon: Boolean,
    )

    private data class RecentLocalAttack(
        val entityId: Int,
        val location: WorldVec,
        val attackedAtMillis: Long,
    )

    private data class CocoonNameMatch(
        val mobName: String,
        val distanceSq: Double,
    )

    private const val COCOON_LIFETIME_MILLIS = 6_400L
    private const val TIMER_GREEN_THRESHOLD_MILLIS = 3_000L
    private const val TIMER_YELLOW_THRESHOLD_MILLIS = 1_000L
    private const val NAME_SCAN_INTERVAL_TICKS = 4
    private val LABEL_OFFSET = WorldVec(0.0, 1.6, 0.0)
    private val LABEL_STYLE = WorldLabelStyle(
        maxRenderDistance = 80.0,
        maxScale = 6.0,
        displayMode = Font.DisplayMode.NORMAL,
    )
    private const val COCOON_HEAD_TEXTURE =
        "eyJ0aW1lc3RhbXAiOjE1ODMxMjMyODkwNTMsInByb2ZpbGVJZCI6IjkxZjA0ZmU5MGYzNjQzYjU4ZjIwZTMzNzVmODZkMzll" +
            "IiwicHJvZmlsZU5hbWUiOiJTdG9ybVN0b3JteSIsInNpZ25hdHVyZVJlcXVpcmVkIjp0cnVlLCJ0ZXh0dXJlcyI6eyJTS0lOIjp7" +
            "InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNGNlYjBlZDhmYzIyNzJiM2QzZDgyMDY3NmQ1MmEz" +
            "OGU3YjJlOGRhOGM2ODdhMjMzZTBkYWJhYTE2YzBlOTZkZiJ9fX0="

    private fun cocoonComparator(message: PendingCocoonMessage): Comparator<TrackedCocoon> =
        compareBy<TrackedCocoon> { cocoon -> mobNameMatchRank(message.mobName, cocoon.mobName) }
            .thenBy { cocoon -> message.killedEntityLocation?.distanceSq(cocoon.location) ?: 0.0 }
            .thenBy { cocoon -> abs(message.receivedAtMillis - cocoon.detectedAtMillis) }
            .thenBy { cocoon -> message.playerLocation?.distanceSq(cocoon.location) ?: 0.0 }

    private fun messageComparator(cocoon: TrackedCocoon): Comparator<PendingCocoonMessage> =
        compareBy<PendingCocoonMessage> { message -> mobNameMatchRank(message.mobName, cocoon.mobName) }
            .thenBy { message -> message.killedEntityLocation?.distanceSq(cocoon.location) ?: 0.0 }
            .thenBy { message -> abs(message.receivedAtMillis - cocoon.detectedAtMillis) }
            .thenBy { message -> message.playerLocation?.distanceSq(cocoon.location) ?: 0.0 }
}

internal fun isWithinMessageLinkWindow(firstMillis: Long, secondMillis: Long): Boolean =
    abs(firstMillis - secondMillis) <= MESSAGE_LINK_WINDOW_MILLIS

private fun mobNameMatchRank(messageMobName: String?, cocoonMobName: String?): Int = when {
    messageMobName == null || cocoonMobName == null -> 1
    messageMobName.equals(cocoonMobName, ignoreCase = true) -> 0
    else -> 2
}

internal fun shouldShowCocoonForOwnership(
    isLocal: Boolean,
    isSlayerCocoon: Boolean,
    isSlayerQuestActive: Boolean,
    hasMobName: Boolean,
): Boolean =
    isLocal || (!isSlayerCocoon && (!isSlayerQuestActive || hasMobName))

internal fun areSameCocoon(first: WorldVec, second: WorldVec): Boolean {
    val dx = first.x - second.x
    val dz = first.z - second.z
    return dx * dx + dz * dz <= COCOON_GROUP_HORIZONTAL_DISTANCE_SQ
}

private fun isPossibleMobNameplate(cocoon: WorldVec, nameplate: WorldVec): Boolean {
    val dx = cocoon.x - nameplate.x
    val dz = cocoon.z - nameplate.z
    return dx * dx + dz * dz <= NAMEPLATE_HORIZONTAL_DISTANCE_SQ &&
        kotlin.math.abs(cocoon.y - nameplate.y) <= NAMEPLATE_VERTICAL_DISTANCE
}

private fun formatCocoonTime(remainingMillis: Long): String =
    String.format(Locale.ROOT, "%.1fs", remainingMillis.coerceAtLeast(0L) / MILLIS_PER_SECOND)

internal fun shouldShowFilteredCocoon(
    wasPreviouslyMatched: Boolean,
    isSlayerTarget: Boolean,
    isDianaRareMob: Boolean,
): Boolean = wasPreviouslyMatched || isSlayerTarget || isDianaRareMob

private const val COCOON_GROUP_HORIZONTAL_DISTANCE_SQ = 1.0
private const val NAMEPLATE_HORIZONTAL_DISTANCE_SQ = 1.0
private const val NAMEPLATE_VERTICAL_DISTANCE = 4.0
private const val MILLIS_PER_SECOND = 1_000.0
private const val MESSAGE_LINK_WINDOW_MILLIS = 1_500L
