package com.skysoft.features.event.diana

import com.skysoft.config.DianaRareMobOption
import com.skysoft.config.SkysoftConfigGui
import com.skysoft.events.entity.ClientEntityMetadataEvents
import com.skysoft.events.entity.EntityInteractionEvent
import com.skysoft.events.entity.EntityInteractionEvents
import com.skysoft.events.entity.EntityLifecycleEvents
import com.skysoft.features.pets.ActivePetTracker
import com.skysoft.utils.WorldVec
import com.skysoft.utils.chat.ChatEvents
import com.skysoft.utils.chat.ChatMessage
import com.skysoft.utils.chat.ChatMessageSender
import com.skysoft.utils.chat.ChatMessageVisibility
import com.skysoft.utils.chat.ChatMessageType
import com.skysoft.utils.chat.SkysoftPartyShare
import com.skysoft.utils.render.EntityHighlightRenderer
import com.skysoft.utils.render.SkysoftRenderContext
import com.skysoft.utils.render.WorldRenderDispatcher
import com.skysoft.utils.SkysoftClientEvents
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.decoration.ArmorStand

internal object DianaRareMobSharing {
    private val config get() = SkysoftConfigGui.config().events.diana
    private val settings get() = config.settings
    private val targets = mutableMapOf<String, DianaRareMobTarget>()
    private val pendingLocalSpawns = mutableListOf<PendingRareMobSpawn>()
    private val pendingLocalClears = mutableListOf<PendingLocalRareMobClear>()
    private val pendingRemoteClears = mutableListOf<PendingRemoteRareMobClear>()
    private var nextTargetId = 0L
    private var ticks = 0

    fun register() {
        SkysoftClientEvents.onEndTick("Diana Rare Mob Sharing tick") { onTick() }
        SkysoftClientEvents.onDisconnect("Diana Rare Mob Sharing disconnect reset", ::clear)
        ChatEvents.onVisibleMessage("Diana Rare Mob chat") { message -> onMessage(message) }
        ClientEntityMetadataEvents.register("Diana Rare Mob entity metadata") { event ->
            if (config.enabled && DianaEventState.isOnHub()) {
                DianaRareMobLootshare.handleMetadata(
                    event,
                    targets.values,
                    DianaRareMobRuntime.localPlayerName(),
                    System.currentTimeMillis(),
                )
            }
        }
        EntityLifecycleEvents.onLoad("Diana Rare Mob entity loading") { entity -> onEntityLoad(entity) }
        EntityLifecycleEvents.onUnload("Diana Rare Mob entity unloading") { entity -> onEntityUnload(entity) }
        EntityInteractionEvents.register("Diana Rare Mob entity interaction") { event ->
            onEntityClick(event)
            false
        }
        WorldRenderDispatcher.registerHandler("Diana Rare Mob world rendering", ::onRenderWorld)
    }

    fun hasActiveTarget(): Boolean =
        targets.isNotEmpty()

    val likelyRemoteRareLoot: Boolean
        get() = targets.values.any { target -> target.source == DianaRareMobTargetSource.REMOTE } &&
            targets.values.none { target -> target.source == DianaRareMobTargetSource.LOCAL } &&
            pendingLocalClears.isEmpty()

    val remotePriorityTarget: DianaRareMobPriorityTarget?
        get() = targets.values
            .asSequence()
            .filter { target -> target.source == DianaRareMobTargetSource.REMOTE }
            .minWithOrNull(compareBy<DianaRareMobTarget> { it.createdAtMillis }.thenBy { it.targetId })
            ?.let { target -> DianaRareMobPriorityTarget(target.sharedLocation) }

    private fun onTick() {
        val now = System.currentTimeMillis()
        if (!config.enabled || !DianaEventState.isOnHub()) {
            clear()
            return
        }
        val signals = if (
            ++ticks % LINK_INTERVAL_TICKS == 0 &&
            (pendingLocalSpawns.isNotEmpty() || targets.isNotEmpty())
        ) {
            DianaRareMobEntityMatcher.visibleSignals()
        } else {
            null
        }
        if (signals != null) {
            trySharePending(signals, now)
            linkTargets(signals, now)
            pruneStaleRemoteTargets(now, DianaRareMobRuntime.playerLocation())
        }
        DianaRareMobLootshare.scan(targets.values, DianaRareMobRuntime.localPlayerName(), now)
        pruneTargets(now)
        flushPendingLocalRareMobClears(pendingLocalClears, now)
        flushPendingRemoteRareMobClears(targets, pendingRemoteClears, now) { target, reason ->
            clearTarget(target, reason, broadcast = false, deferRemoteDeathClear = false)
        }
        DianaRareMobGlow.apply(targets.values, DianaRareMobRuntime.localPlayerName(), config.details.lootshareColors())
    }

    private fun onMessage(message: ChatMessage): ChatMessageVisibility {
        val now = System.currentTimeMillis()
        if (message.type == ChatMessageType.PARTY && DianaLootshareReadyMessage.isMessage(message.body)) {
            return DianaLootshareReadyMessage.handlePartyMessage(
                message = message,
                localPlayerName = DianaRareMobRuntime.localPlayerName(),
                now = now,
                showMarker = config.enabled && DianaEventState.isOnHub() && targets.isNotEmpty(),
            )
        }
        if (!config.enabled || !DianaEventState.isOnHub()) return ChatMessageVisibility.SHOW
        if (message.isSystemLike) {
            val localCocoon = DianaRareMobShareParser.parseLocalCocoon(message.cleanText)
            if (localCocoon != null) {
                handleLocalCocoon(localCocoon, now)
                return ChatMessageVisibility.SHOW
            }
            recordLocalSpawn(
                message = message.cleanText,
                rareMobSharing = settings.rareMobSharing,
                sharedRareMobs = settings.sharedRareMobs.get(),
                pendingLocalSpawns = pendingLocalSpawns,
                now = now,
            )
            if (message.cleanText.isLocalBurrowProgressMessage()) {
                targets.values
                    .filter { target -> target.source == DianaRareMobTargetSource.LOCAL }
                    .toList()
                    .forEach { target -> clearTarget(target, "burrow progressed") }
            }
            if (message.cleanText.isOwnDianaDeathMessage()) {
                targets.values
                    .filter { target -> target.source == DianaRareMobTargetSource.LOCAL }
                    .toList()
                    .forEach { target -> clearTarget(target, "player died") }
            }
            DianaRareMobShareParser.parsePlayerDeath(message.cleanText)?.let { death ->
                clearRemoteTargetForPlayerDeath(targets.values, death) { target ->
                    clearTarget(target, SHARED_PLAYER_DIED_REASON, broadcast = false)
                }
            }
        }
        return if (message.type == ChatMessageType.PARTY) {
            handlePartyMessage(message, now)
        } else {
            ChatMessageVisibility.SHOW
        }
    }

    private fun handlePartyMessage(message: ChatMessage, now: Long): ChatMessageVisibility {
        val context = DianaRareMobPartyMessages.Context(
            localPlayerName = DianaRareMobRuntime.localPlayerName(),
            receivedRareMobs = settings.receivedRareMobs.get(),
            now = now,
        )
        val cocoon = DianaRareMobShareParser.parseCocoon(message.body)
        val clear = if (cocoon == null) DianaRareMobShareParser.parseClear(message.body) else null
        val share = if (cocoon == null && clear == null) DianaRareMobShareParser.parse(message.body) else null
        return when {
            cocoon != null -> DianaRareMobPartyMessages.handleCocoon(
                message,
                cocoon,
                context,
                targets.values,
                pendingRemoteClears,
            )
            clear != null -> DianaRareMobPartyMessages.handleClear(message, clear, context, targets.values) { target ->
                clearTarget(target, "shared clear", broadcast = false)
            }
            share != null -> DianaRareMobPartyMessages.handleShare(message, share, context) { parsedShare, sender ->
                rememberShare(parsedShare, sender, DianaRareMobTargetSource.REMOTE, null, now)
            }
            else -> ChatMessageVisibility.SHOW
        }
    }

    private fun handleLocalCocoon(cocoon: DianaRareMobCocoon, now: Long) {
        if (!settings.rareMobSharing || cocoon.mob !in settings.sharedRareMobs.get()) return
        val localPlayerName = DianaRareMobRuntime.localPlayerName() ?: return
        val location = localCocoonLocation(cocoon.mob, targets.values, pendingLocalClears)
            ?: DianaRareMobRuntime.playerLocation()?.down()?.roundToBlock()
        pendingLocalSpawns.removeIf { pending -> pending.mob == cocoon.mob }
        pendingLocalClears.removeIf { pending -> pending.mob == cocoon.mob }
        targets.values
            .filter { target -> target.source == DianaRareMobTargetSource.LOCAL && target.mob == cocoon.mob }
            .toList()
            .forEach { target -> clearTarget(target, "cocooned", broadcast = false) }

        SkysoftPartyShare.sendParty(DianaRareMobShareParser.formatCocoon(cocoon.mob))
        if (location == null) return
        val sender = ChatMessageSender(localPlayerName, null)
        val share = DianaRareMobShare(cocoon.mob, location)
        val target = rememberShare(share, sender, DianaRareMobTargetSource.LOCAL, null, now)
        target.markPendingCocoonHatch(now + COCOON_HATCH_ATTACH_MILLIS)
        SkysoftPartyShare.sendParty(DianaRareMobShareParser.format(share))
    }

    private fun onEntityLoad(entity: Entity) {
        if (!config.enabled || !DianaEventState.isOnHub()) return
        if (pendingLocalSpawns.isEmpty() && targets.isEmpty()) return
        val now = System.currentTimeMillis()
        if (entity is ArmorStand &&
            DianaRareMobLootshare.tryHandleDamageSplash(
                entity,
                targets.values,
                DianaRareMobRuntime.localPlayerName(),
                now,
            )
        ) return
        val signals = DianaRareMobEntityMatcher.visibleSignals()
        trySharePending(signals, now)
        linkTargets(signals, now)
    }

    private fun onEntityUnload(entity: Entity) {
        if (!config.enabled || !DianaEventState.isOnHub()) return
        val now = System.currentTimeMillis()
        targets.values
            .filter { target -> target.entity?.id == entity.id || target.nameplate?.id == entity.id }
            .toList()
            .forEach { target ->
                val trackedMobUnloaded = target.entity?.id == entity.id
                val clearReason = if (target.currentHealth == 0L) {
                    HEALTH_REACHED_ZERO_REASON
                } else {
                    null
                }
                if (clearReason != null) {
                    clearTarget(target, clearReason)
                    return@forEach
                }
                if (trackedMobUnloaded &&
                    target.entity?.isAlive == false &&
                    target.clearReasonForTrackedEntityDeath() != null
                ) {
                    target.markTrackedEntityDeath(now)
                }
                target.detachEntity(entity.id)
            }
    }

    private fun onEntityClick(event: EntityInteractionEvent) {
        if (event.action != EntityInteractionEvent.ActionType.ATTACK) return
        val target = targets.values.firstOrNull { rareMob -> rareMob.entity?.id == event.clickedEntity.id } ?: return
        val activePet = ActivePetTracker.currentPet
        val canDamage = DianaMythologicalPetRequirement.canDamageRareMob(activePet)
        target.recordLocalAttack(
            event.clickedEntity,
            DianaRareMobRuntime.playerLocation(),
            System.currentTimeMillis(),
            canDamage,
        )
    }

    private fun trySharePending(signals: List<DianaRareMobSignal>, now: Long) {
        val playerLocation = DianaRareMobRuntime.playerLocation() ?: return
        val iterator = pendingLocalSpawns.iterator()
        while (iterator.hasNext()) {
            val pending = iterator.next()
            val signal = closestPendingRareMobSignal(signals, pending.mob, playerLocation)
            if (signal != null) {
                if (sharePending(pending, signal, now) == LocalRareMobShareResult.SHARED) iterator.remove()
            } else if (now >= pending.expiresAtMillis) {
                iterator.remove()
            }
        }
    }

    private fun sharePending(
        pending: PendingRareMobSpawn,
        signal: DianaRareMobSignal,
        now: Long,
    ): LocalRareMobShareResult {
        val localPlayerName = DianaRareMobRuntime.localPlayerName() ?: return LocalRareMobShareResult.PLAYER_UNAVAILABLE
        val sender = ChatMessageSender(localPlayerName, null)
        val share = DianaRareMobShare(pending.mob, signal.location.roundToBlock())
        rememberShare(share, sender, DianaRareMobTargetSource.LOCAL, signal, now)
        SkysoftPartyShare.sendParty(DianaRareMobShareParser.format(share))
        return LocalRareMobShareResult.SHARED
    }

    private fun rememberShare(
        share: DianaRareMobShare,
        sender: ChatMessageSender,
        source: DianaRareMobTargetSource,
        signal: DianaRareMobSignal?,
        now: Long,
    ): DianaRareMobTarget {
        val key = DianaRareMobRuntime.shareKey(sender.name, share)
        val target = targets.getOrPut(key) {
            DianaRareMobTarget(
                ++nextTargetId,
                key,
                share.mob,
                sender,
                source,
                now,
                now + TARGET_LIFETIME_MILLIS,
                share.location,
            )
        }
        target.sharedBy = sender
        target.extendExpiry(now + TARGET_LIFETIME_MILLIS)
        if (signal != null) updateTargetFromSignal(target, signal, now)
        return target
    }

    private fun linkTargets(signals: List<DianaRareMobSignal>, now: Long) {
        targets.values.toList().forEach { target ->
            val signal = signals
                .asSequence()
                .filter { it.mob == target.mob }
                .filter { it.location.distance(target.lineLocation()) <= REMOTE_LINK_DISTANCE }
                .minByOrNull { it.location.distanceSq(target.lineLocation()) }
            if (signal != null) updateTargetFromSignal(target, signal, now)
        }
    }

    private fun updateTargetFromSignal(target: DianaRareMobTarget, signal: DianaRareMobSignal, now: Long) {
        if (signal.health?.current != null && signal.health.current <= 0L) {
            if (target.isAwaitingCocoonHatch(now)) return
            clearTarget(target, "health reached zero")
            return
        }
        val previousEntity = target.entity
        target.updateFromSignal(signal, now)
        if (previousEntity?.id != target.entity?.id) {
            previousEntity?.let(EntityHighlightRenderer::removeEntityColor)
            target.glowColor = null
        }
    }

    private fun pruneTargets(now: Long) {
        targets.values.toList().forEach { target ->
            when {
                now >= target.expiresAtMillis -> clearTarget(target, "expired")
                target.isAwaitingCocoonHatch(now) -> Unit
                target.currentHealth == 0L -> clearTarget(target, HEALTH_REACHED_ZERO_REASON)
                target.hasConfirmedTrackedEntityDeath(now, TRACKED_ENTITY_DEATH_CONFIRM_MILLIS) -> {
                    val clearReason = target.clearReasonForTrackedEntityDeath()
                    if (clearReason != null) {
                        clearTarget(target, clearReason)
                    }
                }
                target.entity?.isAlive == false -> {
                    val entityId = target.entity?.id
                    if (target.clearReasonForTrackedEntityDeath() != null) {
                        target.markTrackedEntityDeath(now)
                    }
                    entityId?.let { target.detachEntity(it) }
                }
            }
        }
    }

    private fun pruneStaleRemoteTargets(now: Long, playerLocation: WorldVec?) {
        if (playerLocation == null) return
        targets.values
            .mapNotNull { target -> staleRemoteClearReason(target, playerLocation, now)?.let { target to it } }
            .toList()
            .forEach { (target, reason) -> clearTarget(target, reason, broadcast = false) }
    }

    private fun onRenderWorld(context: SkysoftRenderContext) {
        if (!config.enabled || !DianaEventState.isOnHub()) return
        if (targets.isEmpty()) {
            DianaLootshareReadyMarkers.clear()
        }
        val currentTarget = currentTarget()
        DianaRareMobRenderer.renderWorld(
            context = context,
            targets = targets.values,
            currentTarget = currentTarget,
            drawCrosshairLine = settings.crosshairLine,
            drawLootshareRadius = settings.lootshareRadius,
            localPlayerName = DianaRareMobRuntime.localPlayerName(),
            lootshareColors = config.details.lootshareColors(),
        )
        DianaLootshareReadyMarkers.renderWorld(
            context,
            DianaRareMobRuntime.localPlayerName(),
            System.currentTimeMillis(),
        )
    }

    private fun clearTarget(
        target: DianaRareMobTarget,
        reason: String,
        broadcast: Boolean = target.source == DianaRareMobTargetSource.LOCAL && reason in BROADCAST_CLEAR_REASONS,
        deferRemoteDeathClear: Boolean = true,
    ) {
        if (deferRemoteDeathClear && shouldDeferRemoteRareMobClear(target, reason, broadcast)) {
            rememberPendingRemoteRareMobClear(pendingRemoteClears, target, reason, System.currentTimeMillis())
            return
        }
        targets.remove(target.key)
        pendingRemoteClears.removeIf { pending -> pending.key == target.key }
        target.entity?.let(EntityHighlightRenderer::removeEntityColor)
        if (shouldDeferLocalRareMobClear(target, reason, broadcast)) {
            pendingLocalClears += PendingLocalRareMobClear(
                target.mob,
                target.lineLocation().roundToBlock(),
                System.currentTimeMillis() + LOCAL_COCOON_CLEAR_GRACE_MILLIS,
            )
        } else if (broadcast) {
            SkysoftPartyShare.sendParty(DianaRareMobShareParser.formatClear(target.mob))
        }
    }

    private fun clear() {
        targets.values.forEach { target -> target.entity?.let(EntityHighlightRenderer::removeEntityColor) }
        targets.clear()
        pendingLocalSpawns.clear()
        pendingLocalClears.clear()
        pendingRemoteClears.clear()
        nextTargetId = 0L
        ticks = 0
        DianaRareMobTitleRenderer.clear()
        DianaLootshareReadyMarkers.clear()
    }

    private fun currentTarget(): DianaRareMobTarget? {
        val playerLocation = DianaRareMobRuntime.playerLocation() ?: return null
        return targets.values.minByOrNull { target -> target.lineLocation().distanceSq(playerLocation) }
    }

    private const val REMOTE_LINK_DISTANCE = 40.0
    private const val LINK_INTERVAL_TICKS = 2
    private const val SHARED_PLAYER_DIED_REASON = "shared player died"
    private val BROADCAST_CLEAR_REASONS = setOf(
        HEALTH_REACHED_ZERO_REASON,
        "mob died",
        "player died",
        "burrow progressed",
    )
}

private fun recordLocalSpawn(
    message: String,
    rareMobSharing: Boolean,
    sharedRareMobs: Collection<DianaRareMobOption>,
    pendingLocalSpawns: MutableList<PendingRareMobSpawn>,
    now: Long,
) {
    val label = DianaDugMobParser.parse(message) ?: return
    if (!rareMobSharing) return
    val mob = DianaRareMobOption.fromLabel(label) ?: return
    if (mob !in sharedRareMobs) return
    pendingLocalSpawns += PendingRareMobSpawn(mob, now + LOCAL_SPAWN_LINK_MILLIS)
}

private fun localCocoonLocation(
    mob: DianaRareMobOption,
    targets: Collection<DianaRareMobTarget>,
    pendingClears: Collection<PendingLocalRareMobClear>,
): WorldVec? =
    targets
        .asSequence()
        .filter { target -> target.source == DianaRareMobTargetSource.LOCAL && target.mob == mob }
        .maxWithOrNull(compareBy<DianaRareMobTarget> { it.createdAtMillis }.thenBy { it.targetId })
        ?.lineLocation()
        ?.roundToBlock()
        ?: pendingClears
            .filter { pending -> pending.mob == mob }
            .maxByOrNull { pending -> pending.expiresAtMillis }
            ?.location

private fun flushPendingLocalRareMobClears(pendingClears: MutableList<PendingLocalRareMobClear>, now: Long) {
    val iterator = pendingClears.iterator()
    while (iterator.hasNext()) {
        val pending = iterator.next()
        if (now < pending.expiresAtMillis) continue
        SkysoftPartyShare.sendParty(DianaRareMobShareParser.formatClear(pending.mob))
        iterator.remove()
    }
}

internal fun flushPendingRemoteRareMobClears(
    targets: Map<String, DianaRareMobTarget>,
    pendingClears: MutableList<PendingRemoteRareMobClear>,
    now: Long,
    clearTarget: (DianaRareMobTarget, String) -> Unit,
) {
    val iterator = pendingClears.iterator()
    while (iterator.hasNext()) {
        val pending = iterator.next()
        if (now < pending.expiresAtMillis) continue
        val target = targets[pending.key]
        iterator.remove()
        if (target != null) {
            if (target.isAwaitingCocoonHatch(now)) continue
            clearTarget(target, pending.reason)
        }
    }
}

internal fun refreshRemoteCocoonTargets(
    targets: Collection<DianaRareMobTarget>,
    pendingClears: MutableList<PendingRemoteRareMobClear>,
    mob: DianaRareMobOption,
    sender: ChatMessageSender,
    now: Long,
) {
    val refreshedKeys = targets
        .filter { target -> target.source == DianaRareMobTargetSource.REMOTE }
        .filter { target -> target.mob == mob && target.sharedBy.name.equals(sender.name, ignoreCase = true) }
        .onEach { target ->
            target.nearbyWithoutSignalSinceMillis = null
            target.extendExpiry(now + TARGET_LIFETIME_MILLIS)
            target.markPendingCocoonHatch(now + COCOON_HATCH_ATTACH_MILLIS)
        }
        .mapTo(mutableSetOf()) { target -> target.key }
    pendingClears.removeIf { pending ->
        pending.key in refreshedKeys ||
            (pending.mob == mob && pending.sharedBy.equals(sender.name, ignoreCase = true))
    }
}

internal fun shouldDeferLocalRareMobClear(target: DianaRareMobTarget, reason: String, broadcast: Boolean): Boolean =
    broadcast && target.source == DianaRareMobTargetSource.LOCAL && reason in LOCAL_DEATH_CLEAR_REASONS

internal fun shouldDeferRemoteRareMobClear(target: DianaRareMobTarget, reason: String, broadcast: Boolean): Boolean =
    !broadcast && target.source == DianaRareMobTargetSource.REMOTE && reason in REMOTE_DEATH_CLEAR_REASONS

internal fun rememberPendingRemoteRareMobClear(
    pendingClears: MutableList<PendingRemoteRareMobClear>,
    target: DianaRareMobTarget,
    reason: String,
    now: Long,
) {
    val expiresAtMillis = now + REMOTE_COCOON_CLEAR_GRACE_MILLIS
    if (pendingClears.any { pending -> pending.key == target.key }) return
    pendingClears += PendingRemoteRareMobClear(
        key = target.key,
        mob = target.mob,
        sharedBy = target.sharedBy.name,
        reason = reason,
        expiresAtMillis = expiresAtMillis,
    )
}

internal fun DianaRareMobTarget.clearReasonForTrackedEntityDeath(): String? =
    when (mob) {
        DianaRareMobOption.KING_MINOS -> null
        else -> "mob died"
    }

internal fun clearRemoteTargetForPlayerDeath(
    targets: Collection<DianaRareMobTarget>,
    death: DianaRareMobPlayerDeath,
    clearTarget: (DianaRareMobTarget) -> Unit,
): RemoteTargetClearResult {
    val target = targets
        .asSequence()
        .filter { target -> target.source == DianaRareMobTargetSource.REMOTE }
        .filter { target -> target.sharedBy.name.equals(death.player, ignoreCase = true) }
        .filter { target -> target.mob == death.mob }
        .maxWithOrNull(compareBy<DianaRareMobTarget> { it.createdAtMillis }.thenBy { it.targetId })
        ?: return RemoteTargetClearResult.NOT_FOUND
    clearTarget(target)
    return RemoteTargetClearResult.CLEARED
}

internal enum class RemoteTargetClearResult {
    CLEARED,
    NOT_FOUND,
}

private fun staleRemoteClearReason(target: DianaRareMobTarget, playerLocation: WorldVec, now: Long): String? {
    if (target.source != DianaRareMobTargetSource.REMOTE) return null
    if (target.isAwaitingCocoonHatch(now)) {
        target.nearbyWithoutSignalSinceMillis = null
        return null
    }
    if (target.entity != null || target.nameplate != null) {
        target.nearbyWithoutSignalSinceMillis = null
        return null
    }
    if (target.lineLocation().distance(playerLocation) > REMOTE_MISSING_CLEAR_DISTANCE) {
        target.nearbyWithoutSignalSinceMillis = null
        return null
    }
    val nearbySince = target.nearbyWithoutSignalSinceMillis ?: now
    target.nearbyWithoutSignalSinceMillis = nearbySince
    val graceMillis = target.remoteMissingGraceMillis()
    if (now - nearbySince < graceMillis) return null
    return if (target.lastSeenAtMillis == null) "not found after arrival" else "lost after arrival"
}

private fun DianaRareMobTarget.detachEntity(entityId: Int) {
    if (entity?.id == entityId) {
        entity?.let(EntityHighlightRenderer::removeEntityColor)
        glowColor = null
    }
    clearEntity(entityId)
}

private fun DianaRareMobTarget.remoteMissingGraceMillis(): Long =
    if (lastSeenAtMillis == null) {
        REMOTE_MISSING_GRACE_MILLIS
    } else if (mob == DianaRareMobOption.KING_MINOS) {
        REMOTE_KING_MINOS_LOST_GRACE_MILLIS
    } else {
        REMOTE_LOST_GRACE_MILLIS
    }

private fun String.isLocalBurrowProgressMessage(): Boolean =
    startsWith("You dug out a Griffin Burrow!") ||
        startsWith("You finished the Griffin burrow chain!")

internal fun ChatMessageSender.isLocalPlayer(localPlayerName: String?): Boolean =
    localPlayerName != null && name.equals(localPlayerName, ignoreCase = true)

private const val HEALTH_REACHED_ZERO_REASON = "health reached zero"
private const val TARGET_LIFETIME_MILLIS = 75_000L
private const val LOCAL_SPAWN_LINK_MILLIS = 30_000L
private const val LOCAL_COCOON_CLEAR_GRACE_MILLIS = 2_000L
private const val REMOTE_COCOON_CLEAR_GRACE_MILLIS = 2_000L
private const val COCOON_HATCH_ATTACH_MILLIS = 12_000L
private const val REMOTE_MISSING_CLEAR_DISTANCE = 50.0
private const val TRACKED_ENTITY_DEATH_CONFIRM_MILLIS = 2_000L
private const val REMOTE_MISSING_GRACE_MILLIS = 10_000L
private const val REMOTE_LOST_GRACE_MILLIS = 3_000L
private const val REMOTE_KING_MINOS_LOST_GRACE_MILLIS = 30_000L
private val LOCAL_DEATH_CLEAR_REASONS = setOf(HEALTH_REACHED_ZERO_REASON, "mob died")
private val REMOTE_DEATH_CLEAR_REASONS = setOf(HEALTH_REACHED_ZERO_REASON, "mob died")

private data class PendingRareMobSpawn(
    val mob: DianaRareMobOption,
    val expiresAtMillis: Long,
)

private enum class LocalRareMobShareResult {
    SHARED,
    PLAYER_UNAVAILABLE,
}

private data class PendingLocalRareMobClear(
    val mob: DianaRareMobOption,
    val location: WorldVec,
    val expiresAtMillis: Long,
)

internal data class PendingRemoteRareMobClear(
    val key: String,
    val mob: DianaRareMobOption,
    val sharedBy: String,
    val reason: String,
    var expiresAtMillis: Long,
)

internal data class DianaRareMobPriorityTarget(
    val sharedLocation: WorldVec,
)
