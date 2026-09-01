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
    private val targets get() = DianaRareMobSharingState.targets
    private val pendingLocalSpawns get() = DianaRareMobSharingState.pendingLocalSpawns
    private val recentLocalDeaths get() = DianaRareMobSharingState.recentLocalDeaths
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
                DianaRareMobRuntime.localPlayerName(),
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
            .asSequence()
            .filter { target -> target.source == DianaRareMobTargetSource.REMOTE }
            .filter { target -> target.sharedBy.name.equals(playerName, ignoreCase = true) }
            .maxWithOrNull(compareBy<DianaRareMobTarget> { it.createdAtMillis }.thenBy { it.targetId })
            ?.mob

    val likelyRemoteRareLoot: Boolean
        get() = currentTargets().any { target -> target.source == DianaRareMobTargetSource.REMOTE } &&
            currentTargets().none { target -> target.source == DianaRareMobTargetSource.LOCAL } &&
            currentRecentLocalDeaths().isEmpty()

    val remotePriorityTarget: DianaRareMobPriorityTarget?
        get() = currentTargets()
            .asSequence()
            .filter { target -> target.source == DianaRareMobTargetSource.REMOTE }
            .minWithOrNull(compareBy<DianaRareMobTarget> { it.createdAtMillis }.thenBy { it.targetId })
            ?.let { target -> DianaRareMobPriorityTarget(target.sharedLocation) }

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
                .filter { target -> target.source == DianaRareMobTargetSource.LOCAL }
                .toList()
                .forEach { target -> clearTarget(target, "sharing disabled", broadcast = false) }
        }
        if (!DianaEventState.isOnHub()) {
            DianaRareMobTitleRenderer.clear()
            DianaLootshareReadyMarkers.clear()
            return
        }
        val signals = if (
            ++ticks % LINK_INTERVAL_TICKS == 0 &&
            (currentPendingLocalSpawns().isNotEmpty() || currentTargets().isNotEmpty())
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
        if (signals != null) pruneStaleRemoteTargets(activeTargets, now, DianaRareMobRuntime.playerLocation())
        if (lootshareFeature.enabled) {
            DianaRareMobLootshare.scan(activeTargets, DianaRareMobRuntime.localPlayerName(), now)
        } else {
            DianaLootshareReadyMarkers.clear()
        }
        DianaRareMobGlow.apply(
            activeTargets,
            DianaRareMobRuntime.localPlayerName(),
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
                localPlayerName = DianaRareMobRuntime.localPlayerName(),
                now = now,
                showMarker = lootshareSettings.partyCheckmarks && currentTargets().isNotEmpty(),
                showMessage = config.showPartyMessages,
            )
        }
        if (!isEnabledOnHub) return ChatMessageVisibility.SHOW
        if (message.isSystemLike) {
            val localCocoon = DianaRareMobShareParser.parseLocalCocoon(message.cleanText)
            if (localCocoon != null && feature.enabled) {
                handleLocalCocoon(localCocoon, now)
                return ChatMessageVisibility.SHOW
            }
            HypixelLocationState.currentServerName?.let { serverName ->
                recordLocalSpawn(
                    message = message.cleanText,
                    serverName = serverName,
                    rareMobSharing = feature.enabled && settings.shareMobs,
                    sharedRareMobs = settings.sharedRareMobs.get(),
                    pendingLocalSpawns = pendingLocalSpawns,
                    now = now,
                )
            }
            if (message.cleanText.isLocalBurrowProgressMessage()) {
                currentTargets()
                    .filter { target -> target.source == DianaRareMobTargetSource.LOCAL }
                    .toList()
                    .forEach { target -> clearTarget(target, "burrow progressed") }
            }
            if (SkyBlockPlayerDeathParser.isLocalDeath(message.cleanText)) {
                currentTargets()
                    .filter { target -> target.source == DianaRareMobTargetSource.LOCAL }
                    .toList()
                    .forEach { target -> clearTarget(target, "player died") }
            }
            DianaRareMobShareParser.parsePlayerDeath(message.cleanText)?.let { death ->
                clearRemoteTargetForPlayerDeath(currentTargets(), death) { target ->
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
        val localPlayerName = DianaRareMobRuntime.localPlayerName() ?: return
        val serverName = HypixelLocationState.currentServerName ?: return
        val currentTargets = currentTargets()
        val location = localCocoonLocation(cocoon.mob, currentTargets, currentRecentLocalDeaths())
            ?: DianaRareMobRuntime.playerLocation()?.down()?.roundToBlock()
        pendingLocalSpawns.removeIf { pending -> pending.serverName == serverName && pending.mob == cocoon.mob }
        recentLocalDeaths.removeIf { death -> death.serverName == serverName && death.mob == cocoon.mob }
        val localTargets = currentTargets
            .filter { target -> target.source == DianaRareMobTargetSource.LOCAL && target.mob == cocoon.mob }
        val cocoonedTarget = localTargets.maxWithOrNull(
            compareBy<DianaRareMobTarget> { it.createdAtMillis }.thenBy { it.targetId },
        )
        localTargets.forEach { target ->
            clearTarget(target, if (target === cocoonedTarget) "mob died" else "cocooned")
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
        if (!isEnabledOnHub) return
        if (pendingLocalSpawns.isEmpty() && targets.isEmpty()) return
        val now = System.currentTimeMillis()
        if (
            lootshareFeature.enabled &&
            entity is ArmorStand &&
            DianaRareMobLootshare.tryHandleDamageSplash(
                entity,
                currentTargets(),
                DianaRareMobRuntime.localPlayerName(),
                now,
            )
        ) return
        val signals = DianaRareMobEntityMatcher.visibleSignals()
        if (feature.enabled) trySharePending(signals, now)
        linkTargets(signals, now)
    }

    private fun onEntityUnload(entity: Entity) {
        if (!isEnabledOnHub) return
        currentTargets()
            .filter { target -> target.entityUuid == entity.uuid || target.nameplateUuid == entity.uuid }
            .toList()
            .forEach { target ->
                val trackedMobUnloaded = target.entityUuid == entity.uuid
                val clearReason = when {
                    target.currentHealth == 0L -> HEALTH_REACHED_ZERO_REASON
                    target.deathConfirmed -> "mob died"
                    trackedMobUnloaded && entity is LivingEntity && entity.isDeadOrDying -> "mob died"
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
            DianaRareMobRuntime.playerLocation(),
            System.currentTimeMillis(),
            canDamage,
        )
    }

    private fun trySharePending(signals: List<DianaRareMobSignal>, now: Long) {
        val playerLocation = DianaRareMobRuntime.playerLocation() ?: return
        val serverName = HypixelLocationState.currentServerName ?: return
        val iterator = pendingLocalSpawns.iterator()
        while (iterator.hasNext()) {
            val pending = iterator.next()
            if (pending.serverName != serverName) continue
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
        rememberShare(share, sender, DianaRareMobTargetSource.LOCAL, signal, pending.serverName, now)
        if (settings.ownMobAlerts) DianaRareMobTitleRenderer.showOwn(pending.mob)
        SkysoftPartyShare.sendParty(DianaRareMobShareParser.format(share))
        return LocalRareMobShareResult.SHARED
    }

    private fun rememberShare(
        share: DianaRareMobShare,
        sender: ChatMessageSender,
        source: DianaRareMobTargetSource,
        signal: DianaRareMobSignal?,
        serverName: String,
        now: Long,
    ): DianaRareMobTarget {
        val key = DianaRareMobRuntime.shareKey(serverName, sender.name, share)
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
            clearTarget(target, "health reached zero")
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
                target.deathConfirmed -> clearTarget(target, "mob died")
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
            .toList()
            .forEach { (target, reason) -> clearTarget(target, reason, broadcast = false) }
    }

    private fun onRenderWorld(context: SkysoftRenderContext) {
        if (!isEnabledOnHub) return
        DianaRareMobRenderer.renderWorld(
            context = context,
            targets = currentTargets(),
            currentTarget = if (feature.enabled) currentTarget() else null,
            showRareMobSharing = feature.enabled,
            showLootshare = lootshareFeature.enabled,
            drawCrosshairLine = config.burrowHelper.settings.crosshairLine,
            drawLootshareRadius = lootshareDetails.lootshareRadius,
            localPlayerName = DianaRareMobRuntime.localPlayerName(),
            lootshareColors = lootshareDetails.lootshareColors(),
        )
        if (lootshareFeature.enabled && lootshareSettings.partyCheckmarks) {
            DianaLootshareReadyMarkers.renderWorld(
                context,
                DianaRareMobRuntime.localPlayerName(),
                activeSpawnerNames,
                System.currentTimeMillis(),
            )
        }
    }

    private fun clearTarget(
        target: DianaRareMobTarget,
        reason: String,
        broadcast: Boolean = target.serverName == HypixelLocationState.currentServerName &&
            target.source == DianaRareMobTargetSource.LOCAL &&
            reason in BROADCAST_CLEAR_REASONS,
    ) {
        targets.remove(target.key)
        target.entity?.let { entity -> EntityHighlightRenderer.removeEntityColor(entity, DianaRareMobGlow) }
        if (shouldRememberLocalRareMobDeath(target, reason, broadcast)) {
            recentLocalDeaths += RecentLocalRareMobDeath(
                target.serverName,
                target.mob,
                target.lineLocation().roundToBlock(),
                System.currentTimeMillis() + LOCAL_DEATH_LOCATION_MILLIS,
            )
        }
        if (broadcast) SkysoftPartyShare.sendParty(DianaRareMobShareParser.formatClear(target.mob))
    }

    private fun clear() {
        targets.values.forEach { target ->
            target.entity?.let { entity -> EntityHighlightRenderer.removeEntityColor(entity, DianaRareMobGlow) }
        }
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
    private const val SHARED_PLAYER_DIED_REASON = "shared player died"
    private val BROADCAST_CLEAR_REASONS = setOf(
        HEALTH_REACHED_ZERO_REASON,
        "mob died",
        "player died",
        "burrow progressed",
    )
}

private object DianaRareMobSharingState {
    val targets = mutableMapOf<String, DianaRareMobTarget>()
    val pendingLocalSpawns = mutableListOf<PendingRareMobSpawn>()
    val recentLocalDeaths = mutableListOf<RecentLocalRareMobDeath>()
}

private fun currentTargets(): List<DianaRareMobTarget> {
    val serverName = HypixelLocationState.currentServerName ?: return emptyList()
    return DianaRareMobSharingState.targets.values.filter { target -> target.serverName == serverName }
}

private fun currentPendingLocalSpawns(): List<PendingRareMobSpawn> {
    val serverName = HypixelLocationState.currentServerName ?: return emptyList()
    return DianaRareMobSharingState.pendingLocalSpawns.filter { pending -> pending.serverName == serverName }
}

private fun currentRecentLocalDeaths(): List<RecentLocalRareMobDeath> {
    val serverName = HypixelLocationState.currentServerName ?: return emptyList()
    return DianaRareMobSharingState.recentLocalDeaths.filter { death -> death.serverName == serverName }
}

private fun currentTarget(): DianaRareMobTarget? {
    val playerLocation = DianaRareMobRuntime.playerLocation() ?: return null
    return currentTargets().minByOrNull { target -> target.lineLocation().distanceSq(playerLocation) }
}

private fun recordLocalSpawn(
    message: String,
    serverName: String,
    rareMobSharing: Boolean,
    sharedRareMobs: Collection<DianaRareMobOption>,
    pendingLocalSpawns: MutableList<PendingRareMobSpawn>,
    now: Long,
) {
    val label = DianaDugMobParser.parse(message) ?: return
    if (!rareMobSharing) return
    val mob = DianaRareMobOption.fromLabel(label) ?: return
    if (mob !in sharedRareMobs) return
    pendingLocalSpawns += PendingRareMobSpawn(serverName, mob, now + LOCAL_SPAWN_LINK_MILLIS)
}

private fun localCocoonLocation(
    mob: DianaRareMobOption,
    targets: Collection<DianaRareMobTarget>,
    recentDeaths: Collection<RecentLocalRareMobDeath>,
): WorldVec? =
    targets
        .asSequence()
        .filter { target -> target.source == DianaRareMobTargetSource.LOCAL && target.mob == mob }
        .maxWithOrNull(compareBy<DianaRareMobTarget> { it.createdAtMillis }.thenBy { it.targetId })
        ?.lineLocation()
        ?.roundToBlock()
        ?: recentDeaths
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
        .filter { target -> target.source == DianaRareMobTargetSource.REMOTE }
        .filter { target -> target.mob == mob && target.sharedBy.name.equals(sender.name, ignoreCase = true) }
        .forEach { target ->
            target.entity?.let { entity -> EntityHighlightRenderer.removeEntityColor(entity, DianaRareMobGlow) }
            target.extendExpiry(now + TARGET_LIFETIME_MILLIS)
            target.prepareForCocoonHatch(now + COCOON_HATCH_ATTACH_MILLIS)
        }
}

internal fun shouldRememberLocalRareMobDeath(
    target: DianaRareMobTarget,
    reason: String,
    broadcast: Boolean,
): Boolean = broadcast && target.source == DianaRareMobTargetSource.LOCAL && reason in LOCAL_DEATH_REASONS

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
private const val LOCAL_DEATH_LOCATION_MILLIS = 2_000L
private const val COCOON_HATCH_ATTACH_MILLIS = 12_000L
private const val REMOTE_MISSING_CLEAR_DISTANCE = 50.0
private const val REMOTE_MISSING_GRACE_MILLIS = 10_000L
private const val REMOTE_LOST_GRACE_MILLIS = 3_000L
private const val REMOTE_KING_MINOS_LOST_GRACE_MILLIS = 30_000L
private val LOCAL_DEATH_REASONS = setOf(HEALTH_REACHED_ZERO_REASON, "mob died")

private data class PendingRareMobSpawn(
    val serverName: String,
    val mob: DianaRareMobOption,
    val expiresAtMillis: Long,
)

private enum class LocalRareMobShareResult {
    SHARED,
    PLAYER_UNAVAILABLE,
}

private data class RecentLocalRareMobDeath(
    val serverName: String,
    val mob: DianaRareMobOption,
    val location: WorldVec,
    val expiresAtMillis: Long,
)

internal data class DianaRareMobPriorityTarget(
    val sharedLocation: WorldVec,
)
