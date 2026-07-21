package com.skysoft.features.combat

import com.skysoft.config.DianaRareMobOption
import com.skysoft.config.SkysoftConfigGui
import com.skysoft.data.hypixel.HypixelLocationState
import com.skysoft.data.skyblock.SkyBlockItemUtilities.playerHeadTexture
import com.skysoft.data.skyblock.SlayerQuestState
import com.skysoft.utils.EntityUtilities.cleanName
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
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.item.ItemStack

object CocoonTracker {
    private val config get() = SkysoftConfigGui.config().combat.cocoonDisplay
    private val cocoons = mutableListOf<TrackedCocoon>()
    private val pendingMessages = mutableListOf<PendingCocoonMessage>()
    private var ticks = 0

    fun register() {
        ChatEvents.onVisibleMessage(
            "Cocoon chat",
            isActive = ::isActive,
        ) { message ->
            if (message.isSystemLike) {
                CocoonMessageParser.parseLocal(message.cleanText)?.let(::handleMessage)
            }
            ChatMessageVisibility.SHOW
        }
        SkysoftClientEvents.onEndTick(
            "Cocoon tracking",
            isActive = { isActive() || cocoons.isNotEmpty() || pendingMessages.isNotEmpty() },
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
        rememberCocoon(entityId, entity.position().toWorldVec(), System.currentTimeMillis())
    }

    private fun handleMessage(message: CocoonMessage) {
        val now = System.currentTimeMillis()
        val playerLocation = Minecraft.getInstance().player?.position()?.toWorldVec()
        val cocoon = cocoons
            .asSequence()
            .filter { now - it.detectedAtMillis <= MESSAGE_LINK_WINDOW_MILLIS }
            .minByOrNull { tracked -> playerLocation?.distanceSq(tracked.location) ?: -tracked.detectedAtMillis.toDouble() }
        if (cocoon != null) {
            updateMobName(cocoon, message.mobName)
        } else {
            pendingMessages += PendingCocoonMessage(message.mobName, playerLocation, now)
        }
    }

    private fun rememberCocoon(entityId: Int, location: WorldVec, now: Long) {
        val existing = cocoons.firstOrNull { tracked ->
            now < tracked.expiresAtMillis &&
                (entityId in tracked.entityIds || areSameCocoon(tracked.location, location))
        }
        if (existing != null) {
            existing.entityIds += entityId
            if (existing.mobName == null) updateMobName(existing, nearbyMobName(existing.location))
            return
        }

        val pending = pendingMessages.minByOrNull { message ->
            message.location?.distanceSq(location) ?: -message.receivedAtMillis.toDouble()
        }
        pending?.let(pendingMessages::remove)
        val cocoon = TrackedCocoon(
            entityIds = mutableSetOf(entityId),
            location = location,
            detectedAtMillis = now,
            expiresAtMillis = now + COCOON_LIFETIME_MILLIS,
        )
        updateMobName(cocoon, pending?.mobName ?: nearbyMobName(location))
        cocoons += cocoon
    }

    private fun nearbyMobName(location: WorldVec): String? =
        Minecraft.getInstance().level
            ?.entitiesForRendering()
            ?.asSequence()
            ?.filterIsInstance<ArmorStand>()
            ?.mapNotNull { entity ->
                val entityLocation = entity.position().toWorldVec()
                if (!isPossibleMobNameplate(location, entityLocation)) return@mapNotNull null
                SkyBlockMobTextParser.parseName(entity.cleanName())?.let { it to entityLocation.distanceSq(location) }
            }
            ?.minByOrNull { (_, distanceSq) -> distanceSq }
            ?.first

    private fun onTick() {
        if (!isActive()) {
            clear()
            return
        }
        val now = System.currentTimeMillis()
        pendingMessages.removeIf { now - it.receivedAtMillis > MESSAGE_LINK_WINDOW_MILLIS }
        cocoons.removeIf { now >= it.expiresAtMillis }
        if (++ticks % NAME_SCAN_INTERVAL_TICKS == 0) {
            cocoons.filter { it.mobName == null }.forEach { updateMobName(it, nearbyMobName(it.location)) }
        }
    }

    private fun renderWorld(context: SkysoftRenderContext) {
        val now = System.currentTimeMillis()
        cocoons.filter(::shouldRender).forEach { cocoon ->
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
        if (!config.settings.onlySlayerTargets || !SlayerQuestState.isActive) return true
        if (cocoon.matchedTargetFilter) return true
        val mobName = cocoon.mobName ?: return false
        return shouldShowFilteredCocoon(
            wasPreviouslyMatched = false,
            isSlayerTarget = SlayerQuestState.isSlayerTarget(mobName),
            isDianaRareMob = DianaRareMobOption.fromMobName(mobName) != null,
        ).also { cocoon.matchedTargetFilter = it }
    }

    private fun updateMobName(cocoon: TrackedCocoon, mobName: String?) {
        cocoon.mobName = mobName
        if (mobName != null) {
            cocoon.matchedTargetFilter = shouldShowFilteredCocoon(
                wasPreviouslyMatched = cocoon.matchedTargetFilter,
                isSlayerTarget = SlayerQuestState.isSlayerTarget(mobName),
                isDianaRareMob = DianaRareMobOption.fromMobName(mobName) != null,
            )
        }
    }

    private fun clear() {
        cocoons.clear()
        pendingMessages.clear()
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
    )

    private data class PendingCocoonMessage(
        val mobName: String,
        val location: WorldVec?,
        val receivedAtMillis: Long,
    )

    private const val COCOON_LIFETIME_MILLIS = 6_400L
    private const val MESSAGE_LINK_WINDOW_MILLIS = 3_000L
    private const val TIMER_GREEN_THRESHOLD_MILLIS = 3_000L
    private const val TIMER_YELLOW_THRESHOLD_MILLIS = 1_000L
    private const val NAME_SCAN_INTERVAL_TICKS = 4
    private val LABEL_OFFSET = WorldVec(0.0, 1.6, 0.0)
    private val LABEL_STYLE = WorldLabelStyle(maxRenderDistance = 80.0, maxScale = 6.0)
    private const val COCOON_HEAD_TEXTURE =
        "eyJ0aW1lc3RhbXAiOjE1ODMxMjMyODkwNTMsInByb2ZpbGVJZCI6IjkxZjA0ZmU5MGYzNjQzYjU4ZjIwZTMzNzVmODZkMzll" +
            "IiwicHJvZmlsZU5hbWUiOiJTdG9ybVN0b3JteSIsInNpZ25hdHVyZVJlcXVpcmVkIjp0cnVlLCJ0ZXh0dXJlcyI6eyJTS0lOIjp7" +
            "InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNGNlYjBlZDhmYzIyNzJiM2QzZDgyMDY3NmQ1MmEz" +
            "OGU3YjJlOGRhOGM2ODdhMjMzZTBkYWJhYTE2YzBlOTZkZiJ9fX0="
}

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

internal fun formatCocoonTime(remainingMillis: Long): String =
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
