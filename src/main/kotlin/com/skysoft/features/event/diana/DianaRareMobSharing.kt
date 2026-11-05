package com.skysoft.features.event.diana

import com.skysoft.config.DianaRareMobOption
import com.skysoft.config.SkysoftConfigGui
import com.skysoft.data.hypixel.HypixelLocationState
import com.skysoft.events.entity.ClientEntityMetadataEvents
import com.skysoft.events.entity.EntityInteractionEvent
import com.skysoft.events.entity.EntityInteractionEvents
import com.skysoft.events.entity.EntityLifecycleEvents
import com.skysoft.data.skyblock.SkyBlockPlayerDeathParser
import com.skysoft.features.pets.ActivePetTracker
import com.skysoft.utils.PlayerUtilities.localPlayerLocation
import com.skysoft.utils.PlayerUtilities.localPlayerName
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
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.decoration.ArmorStand

internal object DianaRareMobSharing {
    private val config get() = SkysoftConfigGui.config().events.diana
    private val feature get() = config.rareMobSharing
    private val settings get() = feature.settings
    private val lootshareFeature get() = config.lootshare
    private val lootshareSettings get() = lootshareFeature.settings
    private val lootshareDetails get() = lootshareFeature.details
    private var nextTargetId = 0L
    private var ticks = 0

    fun register() {
        SkysoftClientEvents.onEndTick(
            "Diana Rare Mob Sharing tick",
            isActive = { isEnabledOnHub || hasRuntimeState },
        ) { onTick() }
        SkysoftClientEvents.onDisconnect("Diana Rare Mob Sharing disconnect reset", ::clear)
        ChatEvents.onVisibleMessage("Diana Rare Mob chat", { isEnabledOnHub }) { message -> onMessage(message) }
        ClientEntityMetadataEvents.register(
            "Diana Rare Mob entity metadata",
            isActive = { isLootshareEnabledOnHub && targets.isNotEmpty() },
        ) { event ->
            DianaRareMobLootshare.handleMetadata(
                event,
                currentTargets(),
                localPlayerName(),
                System.currentTimeMillis(),
            )
        }
        EntityLifecycleEvents.onLoad("Diana Rare Mob entity loading", { isEnabledOnHub }) { entity -> onEntityLoad(entity) }
        EntityLifecycleEvents.onUnload("Diana Rare Mob entity unloading", { isEnabledOnHub }) { entity -> onEntityUnload(entity) }
        EntityInteractionEvents.register("Diana Rare Mob entity interaction", { isLootshareEnabledOnHub }) { event ->
            onEntityClick(event)
            false
        }
        WorldRenderDispatcher.registerHandler(
            "Diana Rare Mob world rendering",
            isActive = { isEnabledOnHub && targets.isNotEmpty() },
            handler = ::onRenderWorld,
        )
    }

    private val isEnabledOnHub: Boolean
        get() = (feature.enabled || lootshareFeature.enabled) && DianaEventState.isOnHub()

    private val isLootshareEnabledOnHub: Boolean
        get() = lootshareFeature.enabled && DianaEventState.isOnHub()

    private val hasRuntimeState: Boolean
        get() = targets.isNotEmpty() || pendingLocalSpawns.isNotEmpty() || recentLocalDeaths.isNotEmpty()

    val hasActiveTarget: Boolean
        get() = currentTargets().isNotEmpty()

    val activeSpawnerNames: Set<String>
        get() = currentTargets().mapTo(mutableSetOf()) { target -> target.sharedBy.name }

    fun remoteMobSharedBy(playerName: String): DianaRareMobOption? =
        currentTargets()
            .filter { target -> target.isRemote && target.sharedBy.name.equals(playerName, ignoreCase = true) }
            .maxWithOrNull(TARGET_AGE_ORDER)
            ?.mob

    val likelyRemoteRareLoot: Boolean
        get() = currentTargets().any { target -> target.isRemote } &&
            currentTargets().none { target -> target.isLocal } &&
            recentLocalDeaths.onCurrentServer { death -> death.serverName }.isEmpty()

    val remotePriorityLocation: WorldVec?
        get() = currentTargets()
            .filter { target -> target.isRemote }
            .minWithOrNull(TARGET_AGE_ORDER)
            ?.sharedLocation

    private fun onTick() {
        val now = System.currentTimeMillis()
        pruneTargets(now)
        pendingLocalSpawns.removeIf { pending -> now >= pending.expiresAtMillis }
        recentLocalDeaths.removeIf { death -> now >= death.expiresAtMillis }
        if (!feature.enabled && !lootshareFeature.enabled) {
            clear()
            return
        }
        if (!feature.enabled) {
            pendingLocalSpawns.clear()
            recentLocalDeaths.clear()
            DianaRareMobTitleRenderer.clear()
            targets.values
                .filter { target -> target.isLocal }
                .forEach { target -> clearTarget(target, "sharing disabled", broadcast = false) }
        }
        if (!DianaEventState.isOnHub()) {
            DianaRareMobTitleRenderer.clear()
            DianaLootshareReadyMarkers.clear()
            return
        }
        val signals = if (
            ++ticks % LINK_INTERVAL_TICKS == 0 &&
            (pendingLocalSpawns.onCurrentServer { it.serverName }.isNotEmpty() || currentTargets().isNotEmpty())
        ) {
            DianaRareMobEntityMatcher.visibleSignals()
        } else {
            null
        }
        if (signals != null) {
            if (feature.enabled) trySharePending(signals, now)
            linkTargets(signals, now)
        }
        val activeTargets = currentTargets()
        if (signals != null) pruneStaleRemoteTargets(activeTargets, now, localPlayerLocation())
        if (lootshareFeature.enabled) {
            DianaRareMobLootshare.scan(activeTargets, localPlayerName(), now)
        } else {
            DianaLootshareReadyMarkers.clear()
        }
        DianaRareMobGlow.apply(
            activeTargets,
            localPlayerName(),
            lootshareFeature.enabled,
            lootshareDetails.lootshareColors(),
        )
        if (activeTargets.isEmpty()) DianaLootshareReadyMarkers.clear()
    }

    private fun onMessage(message: ChatMessage): ChatMessageVisibility {
        val now = System.currentTimeMillis()
        if (
            lootshareFeature.enabled &&
            message.type == ChatMessageType.PARTY &&
            DianaLootshareReadyMessage.isMessage(message.body)
        ) {
            return DianaLootshareReadyMessage.handlePartyMessage(
                message = message,
                localPlayerName = localPlayerName(),
                now = now,
                showMarker = lootshareSettings.partyCheckmarks && currentTargets().isNotEmpty(),
                showMessage = config.showPartyMessages,
            )
        }
        if (message.isSystemLike) {
            val localCocoon = DianaRareMobShareParser.parseLocalCocoon(message.cleanText)
            if (localCocoon != null && feature.enabled) {
                handleLocalCocoon(localCocoon, now)
                return ChatMessageVisibility.SHOW
            }
            val serverName = HypixelLocationState.currentServerName
            if (serverName != null && feature.enabled && settings.shareMobs) {
                recordLocalSpawn(message.cleanText, serverName, settings.sharedRareMobs.get(), now)
            }
            if (message.cleanText.isLocalBurrowProgressMessage()) clearLocalTargets("burrow progressed")
            if (SkyBlockPlayerDeathParser.isLocalDeath(message.cleanText)) clearLocalTargets("player died")
            DianaRareMobShareParser.parsePlayerDeath(message.cleanText)
                ?.let(::remoteTargetKilledBy)
                ?.let { target -> clearTarget(target, "shared player died", broadcast = false) }
        }
        return if (message.type == ChatMessageType.PARTY) {
            handlePartyMessage(message, now)
        } else {
            ChatMessageVisibility.SHOW
        }
    }

    private fun handlePartyMessage(message: ChatMessage, now: Long): ChatMessageVisibility {
        val context = DianaRareMobPartyMessages.Context(
            localPlayerName = localPlayerName(),
            receivedRareMobs = settings.receivedRareMobs.get(),
            showRareMobSharing = feature.enabled,
            showPartyMessages = config.showPartyMessages,
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
                currentTargets(),
            )
            clear != null -> DianaRareMobPartyMessages.handleClear(message, clear, context, currentTargets()) { target ->
                clearTarget(target, "shared clear", broadcast = false)
            }
            share != null -> DianaRareMobPartyMessages.handleShare(message, share, context) { parsedShare, sender ->
                HypixelLocationState.currentServerName?.let { serverName ->
                    rememberShare(parsedShare, sender, DianaRareMobTargetSource.REMOTE, null, serverName, now)
                }
            }
            else -> ChatMessageVisibility.SHOW
        }
    }

    private fun handleLocalCocoon(cocoon: DianaRareMobCocoon, now: Long) {
        if (!settings.shareMobs || cocoon.mob !in settings.sharedRareMobs.get()) return
        val localPlayerName = localPlayerName() ?: return
        val serverName = HypixelLocationState.currentServerName ?: return
        val location = localCocoonLocation(cocoon.mob) ?: localPlayerLocation()?.down()?.roundToBlock()
        pendingLocalSpawns.removeIf { pending -> pending.serverName == serverName && pending.mob == cocoon.mob }
        recentLocalDeaths.removeIf { death -> death.serverName == serverName && death.mob == cocoon.mob }
        val localTargets = currentTargets().filter { target -> target.isLocal && target.mob == cocoon.mob }
        val cocoonedTarget = localTargets.maxWithOrNull(TARGET_AGE_ORDER)
        localTargets.forEach { target ->
            clearTarget(target, if (target === cocoonedTarget) MOB_DIED_REASON else "cocooned")
        }

        SkysoftPartyShare.sendParty(DianaRareMobShareParser.formatCocoon(cocoon.mob))
        if (settings.ownMobAlerts) DianaRareMobTitleRenderer.showOwnCocoon(cocoon.mob)
        if (location == null) return
        val sender = ChatMessageSender(localPlayerName, null)
        val share = DianaRareMobShare(cocoon.mob, location)
        val target = rememberShare(share, sender, DianaRareMobTargetSource.LOCAL, null, serverName, now)
        target.prepareForCocoonHatch(now + COCOON_HATCH_ATTACH_MILLIS)
        SkysoftPartyShare.sendParty(DianaRareMobShareParser.format(share))
    }

    private fun onEntityLoad(entity: Entity) {
        if (pendingLocalSpawns.isEmpty() && targets.isEmpty()) return
        val now = System.currentTimeMillis()
        if (
            lootshareFeature.enabled &&
            entity is ArmorStand &&
            DianaRareMobLootshare.tryHandleDamageSplash(
                entity,
                currentTargets(),
                localPlayerName(),
                now,
            )
        ) return
        val signals = DianaRareMobEntityMatcher.visibleSignals()
        if (feature.enabled) trySharePending(signals, now)
        linkTargets(signals, now)
    }

    private fun onEntityUnload(entity: Entity) {
        currentTargets()
            .filter { target -> target.entityUuid == entity.uuid || target.nameplateUuid == entity.uuid }
            .forEach { target ->
                val trackedMobUnloaded = target.entityUuid == entity.uuid
                val clearReason = when {
                    target.currentHealth == 0L -> HEALTH_REACHED_ZERO_REASON
                    target.deathConfirmed -> MOB_DIED_REASON
                    trackedMobUnloaded && entity is LivingEntity && entity.isDeadOrDying -> MOB_DIED_REASON
                    else -> null
                }
                if (clearReason != null) {
                    clearTarget(target, clearReason)
                } else if (trackedMobUnloaded && entity is LivingEntity) {
                    EntityHighlightRenderer.removeEntityColor(entity, DianaRareMobGlow)
                    target.glowColor = null
                }
            }
    }

    private fun onEntityClick(event: EntityInteractionEvent) {
        if (event.action != EntityInteractionEvent.ActionType.ATTACK) return
        val target = currentTargets().firstOrNull { rareMob -> rareMob.entityUuid == event.clickedEntity.uuid } ?: return
        val activePet = ActivePetTracker.currentPet
        val canDamage = DianaMythologicalPetRequirement.canDamageRareMob(activePet)
        target.recordLocalAttack(
            event.clickedEntity,
            localPlayerLocation(),
            System.currentTimeMillis(),
            canDamage,
        )
    }

    private fun trySharePending(signals: List<DianaRareMobSignal>, now: Long) {
        val playerLocation = localPlayerLocation() ?: return
        val sender = ChatMessageSender(localPlayerName() ?: return, null)
        val serverName = HypixelLocationState.currentServerName ?: return
        pendingLocalSpawns.removeIf { pending ->
            if (pending.serverName != serverName) return@removeIf false
            val signal = closestPendingRareMobSignal(signals, pending.mob, playerLocation)
                ?: return@removeIf now >= pending.expiresAtMillis
            sharePending(pending, signal, sender, now)
            true
        }
    }

    private fun sharePending(
        pending: PendingRareMobSpawn,
        signal: DianaRareMobSignal,
        sender: ChatMessageSender,
        now: Long,
    ) {
        val share = DianaRareMobShare(pending.mob, signal.location.roundToBlock())
        rememberShare(share, sender, DianaRareMobTargetSource.LOCAL, signal, pending.serverName, now)
        if (settings.ownMobAlerts) DianaRareMobTitleRenderer.showOwn(pending.mob)
        SkysoftPartyShare.sendParty(DianaRareMobShareParser.format(share))
    }

    private fun rememberShare(
        share: DianaRareMobShare,
        sender: ChatMessageSender,
        source: DianaRareMobTargetSource,
        signal: DianaRareMobSignal?,
        serverName: String,
        now: Long,
    ): DianaRareMobTarget {
        val key = "$serverName:${sender.name.lowercase()}:${share.mob.name}:${share.location.blockKey()}"
        val target = targets.getOrPut(key) {
            DianaRareMobTarget(
                ++nextTargetId,
                key,
                serverName,
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
        currentTargets().forEach { target ->
            val awaitingCocoonHatch = target.isAwaitingCocoonHatch(now)
            val matchingSignals = signals
                .asSequence()
                .filter { it.mob == target.mob }
                .filter { !awaitingCocoonHatch || it.health?.current?.let { health -> health > 0L } != false }
            val entityUuid = target.entityUuid
            val signal = if (entityUuid != null) {
                matchingSignals.firstOrNull { it.trackedMob.entityUuid == entityUuid }
            } else {
                matchingSignals
                    .filter { it.location.distance(target.lineLocation()) <= REMOTE_LINK_DISTANCE }
                    .minByOrNull { it.location.distanceSq(target.lineLocation()) }
            }
            if (signal != null) updateTargetFromSignal(target, signal, now)
        }
    }

    private fun updateTargetFromSignal(target: DianaRareMobTarget, signal: DianaRareMobSignal, now: Long) {
        val health = signal.health?.current
        if (health != null && health <= 0L) {
            if (target.isAwaitingCocoonHatch(now)) return
            clearTarget(target, HEALTH_REACHED_ZERO_REASON)
            return
        }
        target.updateFromSignal(signal)
    }

    private fun pruneTargets(now: Long) {
        targets.values.toList().forEach { target ->
            when {
                now >= target.expiresAtMillis -> clearTarget(target, "expired")
                target.isAwaitingCocoonHatch(now) -> Unit
                target.currentHealth == 0L -> clearTarget(target, HEALTH_REACHED_ZERO_REASON)
                target.deathConfirmed -> clearTarget(target, MOB_DIED_REASON)
            }
        }
    }

    private fun pruneStaleRemoteTargets(
        activeTargets: Collection<DianaRareMobTarget>,
        now: Long,
        playerLocation: WorldVec?,
    ) {
        if (playerLocation == null) return
        activeTargets
            .mapNotNull { target -> staleRemoteClearReason(target, playerLocation, now)?.let { target to it } }
            .forEach { (target, reason) -> clearTarget(target, reason, broadcast = false) }
    }

    private fun onRenderWorld(context: SkysoftRenderContext) {
        DianaRareMobRenderer.renderWorld(
            context = context,
            targets = currentTargets(),
            currentTarget = if (feature.enabled) currentTarget() else null,
            showRareMobSharing = feature.enabled,
            showLootshare = lootshareFeature.enabled,
            drawCrosshairLine = config.burrowHelper.settings.crosshairLine,
            drawLootshareRadius = lootshareDetails.lootshareRadius,
            localPlayerName = localPlayerName(),
            lootshareColors = lootshareDetails.lootshareColors(),
        )
        if (lootshareFeature.enabled && lootshareSettings.partyCheckmarks) {
            DianaLootshareReadyMarkers.renderWorld(
                context,
                localPlayerName(),
                activeSpawnerNames,
                System.currentTimeMillis(),
            )
        }
    }

    private fun clearTarget(
        target: DianaRareMobTarget,
        reason: String,
        broadcast: Boolean = target.serverName == HypixelLocationState.currentServerName &&
            target.isLocal &&
            reason in BROADCAST_CLEAR_REASONS,
    ) {
        targets.remove(target.key)
        target.removeGlow()
        if (broadcast && target.isLocal && reason in LOCAL_DEATH_REASONS) {
            recentLocalDeaths += RecentLocalRareMobDeath(
                target.serverName,
                target.mob,
                target.lineLocation().roundToBlock(),
                System.currentTimeMillis() + LOCAL_DEATH_LOCATION_MILLIS,
            )
        }
        if (broadcast) SkysoftPartyShare.sendParty(DianaRareMobShareParser.formatClear(target.mob))
    }

    private fun clearLocalTargets(reason: String) {
        currentTargets().filter { target -> target.isLocal }.forEach { target -> clearTarget(target, reason) }
    }

    private fun clear() {
        targets.values.forEach { target -> target.removeGlow() }
        targets.clear()
        pendingLocalSpawns.clear()
        recentLocalDeaths.clear()
        nextTargetId = 0L
        ticks = 0
        DianaRareMobTitleRenderer.clear()
        DianaLootshareReadyMarkers.clear()
    }

    private const val REMOTE_LINK_DISTANCE = 40.0
    private const val LINK_INTERVAL_TICKS = 2
    private val BROADCAST_CLEAR_REASONS = setOf(
        HEALTH_REACHED_ZERO_REASON,
        MOB_DIED_REASON,
        "player died",
        "burrow progressed",
    )
}

private val targets = mutableMapOf<String, DianaRareMobTarget>()
private val pendingLocalSpawns = mutableListOf<PendingRareMobSpawn>()
private val recentLocalDeaths = mutableListOf<RecentLocalRareMobDeath>()
private val TARGET_AGE_ORDER = compareBy<DianaRareMobTarget>({ it.createdAtMillis }, { it.targetId })

private inline fun <T> Collection<T>.onCurrentServer(serverName: (T) -> String): List<T> {
    val currentServerName = HypixelLocationState.currentServerName ?: return emptyList()
    return filter { entry -> serverName(entry) == currentServerName }
}

private fun currentTargets(): List<DianaRareMobTarget> =
    targets.values.onCurrentServer { target -> target.serverName }

private fun currentTarget(): DianaRareMobTarget? {
    val playerLocation = localPlayerLocation() ?: return null
    return currentTargets().minByOrNull { target -> target.lineLocation().distanceSq(playerLocation) }
}

private fun recordLocalSpawn(
    message: String,
    serverName: String,
    sharedRareMobs: Collection<DianaRareMobOption>,
    now: Long,
) {
    val mob = DianaDugMobParser.parse(message)?.let(DianaRareMobOption::fromLabel) ?: return
    if (mob in sharedRareMobs) pendingLocalSpawns += PendingRareMobSpawn(serverName, mob, now + LOCAL_SPAWN_LINK_MILLIS)
}

private fun localCocoonLocation(mob: DianaRareMobOption): WorldVec? =
    currentTargets()
        .filter { target -> target.isLocal && target.mob == mob }
        .maxWithOrNull(TARGET_AGE_ORDER)
        ?.lineLocation()
        ?.roundToBlock()
        ?: recentLocalDeaths
            .onCurrentServer { death -> death.serverName }
            .filter { death -> death.mob == mob }
            .maxByOrNull { death -> death.expiresAtMillis }
            ?.location

internal fun refreshRemoteCocoonTargets(
    targets: Collection<DianaRareMobTarget>,
    mob: DianaRareMobOption,
    sender: ChatMessageSender,
    now: Long,
) {
    targets
        .filter { target ->
            target.isRemote && target.mob == mob && target.sharedBy.name.equals(sender.name, ignoreCase = true)
        }
        .forEach { target ->
            target.removeGlow()
            target.extendExpiry(now + TARGET_LIFETIME_MILLIS)
            target.prepareForCocoonHatch(now + COCOON_HATCH_ATTACH_MILLIS)
        }
}

private fun remoteTargetKilledBy(death: DianaRareMobPlayerDeath): DianaRareMobTarget? =
    currentTargets()
        .filter { target ->
            target.isRemote && target.mob == death.mob && target.sharedBy.name.equals(death.player, ignoreCase = true)
        }
        .maxWithOrNull(TARGET_AGE_ORDER)

private fun staleRemoteClearReason(target: DianaRareMobTarget, playerLocation: WorldVec, now: Long): String? {
    if (!target.isRemote) return null
    if (
        target.isAwaitingCocoonHatch(now) ||
        target.hasVisibleSignal() ||
        target.lineLocation().distance(playerLocation) > REMOTE_MISSING_CLEAR_DISTANCE
    ) {
        target.nearbyWithoutSignalSinceMillis = null
        return null
    }
    val nearbySince = target.nearbyWithoutSignalSinceMillis ?: now
    target.nearbyWithoutSignalSinceMillis = nearbySince
    val graceMillis = target.remoteMissingGraceMillis()
    if (now - nearbySince < graceMillis) return null
    return if (target.lastSeenAtMillis == null) "not found after arrival" else "lost after arrival"
}

private fun DianaRareMobTarget.remoteMissingGraceMillis(): Long =
    if (lastSeenAtMillis == null) {
        REMOTE_MISSING_GRACE_MILLIS
    } else if (mob == DianaRareMobOption.KING_MINOS) {
        REMOTE_KING_MINOS_LOST_GRACE_MILLIS
    } else {
        REMOTE_LOST_GRACE_MILLIS
    }

private fun DianaRareMobTarget.removeGlow() {
    entity?.let { glowingEntity -> EntityHighlightRenderer.removeEntityColor(glowingEntity, DianaRareMobGlow) }
}

private fun String.isLocalBurrowProgressMessage(): Boolean =
    startsWith(BURROW_DUG_MESSAGE) || startsWith(BURROW_CHAIN_FINISHED_MESSAGE)

private const val HEALTH_REACHED_ZERO_REASON = "health reached zero"
private const val MOB_DIED_REASON = "mob died"
private const val TARGET_LIFETIME_MILLIS = 75_000L
private const val LOCAL_SPAWN_LINK_MILLIS = 30_000L
private const val LOCAL_DEATH_LOCATION_MILLIS = 2_000L
private const val COCOON_HATCH_ATTACH_MILLIS = 12_000L
private const val REMOTE_MISSING_CLEAR_DISTANCE = 50.0
private const val REMOTE_MISSING_GRACE_MILLIS = 10_000L
private const val REMOTE_LOST_GRACE_MILLIS = 3_000L
private const val REMOTE_KING_MINOS_LOST_GRACE_MILLIS = 30_000L
private val LOCAL_DEATH_REASONS = setOf(HEALTH_REACHED_ZERO_REASON, MOB_DIED_REASON)

private data class PendingRareMobSpawn(
    val serverName: String,
    val mob: DianaRareMobOption,
    val expiresAtMillis: Long,
)

private data class RecentLocalRareMobDeath(
    val serverName: String,
    val mob: DianaRareMobOption,
    val location: WorldVec,
    val expiresAtMillis: Long,
)
