package com.skysoft.features.event.diana

import com.skysoft.config.SkysoftConfigGui
import com.skysoft.data.hypixel.SkyBlockProfileApi
import com.skysoft.data.hypixel.HypixelPartyApi
import com.skysoft.data.ProfileStorageApi
import com.skysoft.data.skyblock.MayorPerkApi
import com.skysoft.events.input.ItemUseEvent
import com.skysoft.events.input.ItemUseEvents
import com.skysoft.events.particle.ClientParticleEvent
import com.skysoft.events.particle.ClientParticleEvents
import com.skysoft.utils.WorldVec
import com.skysoft.utils.MinecraftClient
import com.skysoft.utils.chat.ChatEvents
import com.skysoft.utils.chat.ChatMessageVisibility
import com.skysoft.utils.input.InputUtilities
import com.skysoft.utils.render.SkysoftRenderContext
import com.skysoft.utils.render.WorldRenderDispatcher
import com.skysoft.utils.toWorldVec
import com.skysoft.utils.SkysoftClientEvents
import net.minecraft.client.Minecraft
import org.lwjgl.glfw.GLFW

object DianaBurrowHelper {
    private val config get() = SkysoftConfigGui.config().events.diana
    private val burrowHelper get() = config.burrowHelper
    private val settings get() = burrowHelper.settings
    private val details get() = burrowHelper.details
    private val quickWarps get() = config.quickWarps
    private val quickWarpSettings get() = quickWarps.settings
    private val disabledWarpCommands = mutableSetOf<String>()
    private var warpKeyWasDown = false
    private var lastWarpCommand: DianaWarpPoint? = null
    private var lastWarpAtMillis = 0L
    private var wasOnHub = false

    fun register() {
        ProfileStorageApi.registerConsumer("Diana Burrow Helper") { burrowHelper.enabled }
        MayorPerkApi.registerConsumer("Diana helpers") { config.isAnyFeatureEnabled() }
        HypixelPartyApi.registerConsumer("Diana helpers") { config.isAnyFeatureEnabled() }
        SkysoftClientEvents.onEndTick(
            "Diana Burrow Helper tick",
            isActive = { isEnabled() || quickWarps.enabled || hasRuntimeState() },
        ) { onTick() }
        SkysoftClientEvents.onDisconnect("Diana Burrow Helper disconnect reset", ::clearSession)
        SkysoftClientEvents.onClientStopping("Diana Hub Surface Cache save") {
            DianaHubSurfaceCache.flush()
        }
        SkyBlockProfileApi.onProfileChange("Diana Burrow Helper profile change", { burrowHelper.enabled }) { profile ->
            DianaBurrowStorage.saveCurrentTargets()
            clearTargets(persistTargets = false)
            DianaBurrowStorage.resetLoadedProfile()
            DianaBurrowChainState.resetLoadedProfile()
            if (profile != null) {
                DianaBurrowStorage.restoreCurrentProfile()
                DianaBurrowChainState.restoreCurrentProfile()
            }
            wasOnHub = false
        }
        ClientParticleEvents.register("Diana Burrow particles", DianaEventState::canUseHelper) { event ->
            handleParticle(event)
            shouldHideArrowParticle(event)
        }
        ItemUseEvents.register("Diana Burrow item use", DianaEventState::canUseHelper) { event ->
            onItemUse(event)
            false
        }
        ChatEvents.onVisibleMessage("Diana Burrow chat", { isEnabled() || quickWarps.enabled }) { message ->
            if (message.isSystemLike) {
                if (quickWarps.enabled) handleWarpFailure(message.cleanText)
                if (isEnabled()) DianaBurrowInteractions.onMessage(message)
            }
            ChatMessageVisibility.SHOW
        }
        WorldRenderDispatcher.registerHandler(
            "Diana Burrow world rendering",
            isActive = { burrowHelper.enabled && DianaEventState.isOnHub() },
            handler = ::onRenderWorld,
        )
        DianaWarpTitleRenderer.register(::activeWarpSuggestion)
    }

    fun didClearBurrows(): Boolean {
        if (SkyBlockProfileApi.currentProfileId == null) return false
        DianaBurrowStorage.restoreCurrentProfile()
        DianaBurrowChainState.restoreCurrentProfile()
        clearTargets(persistTargets = true)
        DianaBurrowChainState.clear(persist = true)
        DianaBurrowStorage.saveCurrentTargets()
        return true
    }

    private fun isEnabled(): Boolean = burrowHelper.enabled

    private fun hasRuntimeState(): Boolean =
        wasOnHub || warpKeyWasDown || DianaBurrowTargetTracker.hasTargets()

    private fun onTick() {
        val now = System.currentTimeMillis()
        val onHub = DianaEventState.isOnHub()
        if (burrowHelper.enabled) {
            if (onHub) {
                wasOnHub = true
                DianaBurrowStorage.restoreCurrentProfile(now)
                DianaBurrowChainState.restoreCurrentProfile(now)
                DianaHubSurfaceCache.onTick()
                DianaBurrowParticleDetector.prune(now)
                DianaArrowGuess.prune(now)
                DianaBurrowTargetTracker.prune(now)
                DianaBurrowInteractions.onTick(now)
            } else {
                suspendTargets(now)
            }
        } else if (wasOnHub || DianaBurrowTargetTracker.hasTargets()) {
            clearTargets(persistTargets = false)
            DianaBurrowStorage.resetLoadedProfile()
            DianaBurrowChainState.resetLoadedProfile()
            wasOnHub = false
        }

        if (quickWarps.enabled && onHub) {
            handleWarpKey(now)
        } else {
            warpKeyWasDown = false
        }
    }

    private fun handleParticle(event: ClientParticleEvent) {
        if (!DianaEventState.canUseHelper()) return
        val now = System.currentTimeMillis()
        DianaBurrowParticleDetector.handle(event, now)
        DianaSpadeGuess.handleParticle(event, now)
        DianaArrowGuess.handleParticle(event, now)
    }

    private fun shouldHideArrowParticle(event: ClientParticleEvent): Boolean =
        DianaEventState.canUseHelper() && details.hideGuessArrows && DianaParticleClassifier.isArrowParticle(event)

    private fun onItemUse(event: ItemUseEvent) {
        if (DianaEventState.canUseHelper()) {
            DianaSpadeGuess.handleItemUse(event)
        }
    }

    private fun onRenderWorld(context: SkysoftRenderContext) {
        if (!burrowHelper.enabled || !DianaEventState.isOnHub()) return
        if (!DianaEventState.canUseHelper()) return
        val targets = DianaBurrowTargetTracker.snapshot()
        renderTargets(context, targets)
    }

    internal fun renderTargets(context: SkysoftRenderContext, targets: Collection<DianaBurrowTarget>) {
        val playerLocation = currentPlayerLocation() ?: return
        val target = targets.currentTarget(playerLocation) ?: return
        val labelColors = details.burrowLabelColors()
        DianaBurrowRenderer.renderWorld(
            context = context,
            targets = targets,
            currentTarget = target,
            playerLocation = playerLocation,
            drawCrosshairLine = settings.crosshairLine &&
                (!config.rareMobSharing.enabled || !DianaRareMobSharing.hasActiveTarget),
            boldLabels = details.boldText,
            labelFormat = details.labelFormat,
            labelColors = labelColors,
            beamColors = if (details.beaconBeam) details.burrowBeamColors() else null,
            boxStyle = details.burrowBoxStyle(labelColors),
            distanceStyle = if (details.showDistance) details.burrowDistanceStyle() else null,
            showClickCounter = settings.clickCounter,
            clickCounterPosition = settings.clickCounterPosition,
            visualAlphaScale = if (
                config.rareMobSharing.enabled && DianaRareMobSharing.remotePriorityTarget != null
            ) {
                RARE_MOB_PRIORITY_BURROW_ALPHA
            } else {
                1.0
            },
        )
    }

    private fun handleWarpKey(now: Long) {
        val key = quickWarpSettings.warpKey
        val keyDown = key != GLFW.GLFW_KEY_UNKNOWN && key != GLFW.GLFW_KEY_ENTER && InputUtilities.isBindingDown(key)
        if (!keyDown) {
            warpKeyWasDown = false
            return
        }
        if (warpKeyWasDown) return
        warpKeyWasDown = true
        val suggestion = activeWarpSuggestion() ?: return
        sendWarp(suggestion, now)
    }

    private fun activeWarpSuggestion(): DianaWarpSuggestion? {
        if (!quickWarps.enabled || MinecraftClient.screen() != null) return null
        val playerLocation = currentPlayerLocation() ?: return null
        if (config.rareMobSharing.enabled) {
            DianaRareMobSharing.remotePriorityTarget?.let { target ->
                return currentWarpSuggestion(target.sharedLocation, playerLocation)
            }
        }
        if (!DianaEventState.canUseHelper()) return null
        val target = DianaBurrowTargetTracker.currentTarget(playerLocation) ?: return null
        return currentWarpSuggestion(target.location.blockCenter(), playerLocation)
    }

    private fun sendWarp(suggestion: DianaWarpSuggestion, now: Long) {
        Minecraft.getInstance().connection?.sendCommand("warp ${suggestion.point.command}") ?: return
        lastWarpCommand = suggestion.point
        lastWarpAtMillis = now
    }

    private fun currentWarpSuggestion(targetLocation: WorldVec, playerLocation: WorldVec): DianaWarpSuggestion? =
        DianaWarpSelector.bestWarp(
            target = targetLocation,
            playerLocation = playerLocation,
            minSavings = quickWarpSettings.minWarpSavings.toDouble(),
            disabledCommands = disabledWarpCommands,
            warps = quickWarpSettings.warps.get(),
        )

    private fun handleWarpFailure(message: String) {
        if (!message.contains("haven't unlocked this fast travel destination", ignoreCase = true)) return
        val failedWarp = lastWarpCommand ?: return
        if (System.currentTimeMillis() - lastWarpAtMillis > WARP_FAILURE_WINDOW_MILLIS) return
        disabledWarpCommands += failedWarp.command
        lastWarpCommand = null
    }

    private fun clearSession() {
        DianaBurrowStorage.saveCurrentTargets()
        DianaHubSurfaceCache.saveInBackground()
        clearTargets(persistTargets = false)
        DianaBurrowStorage.resetLoadedProfile()
        DianaBurrowChainState.resetLoadedProfile()
        disabledWarpCommands.clear()
        lastWarpCommand = null
        lastWarpAtMillis = 0L
        warpKeyWasDown = false
        wasOnHub = false
    }

    private fun suspendTargets(now: Long) {
        if (wasOnHub || DianaBurrowTargetTracker.snapshot().isNotEmpty()) {
            DianaBurrowStorage.saveCurrentTargets(now)
            DianaHubSurfaceCache.saveInBackground()
            clearTargets(persistTargets = false)
            DianaBurrowStorage.resetLoadedProfile()
            DianaBurrowChainState.resetLoadedProfile()
        } else {
            clearTransientTracking()
        }
        wasOnHub = false
    }

    private fun clearTargets(persistTargets: Boolean = false) {
        DianaBurrowTargetTracker.clear(persist = persistTargets)
        clearTransientTracking()
    }

    private fun clearTransientTracking() {
        DianaBurrowParticleDetector.clear()
        DianaSpadeGuess.clear()
        DianaArrowGuess.clear()
        DianaBurrowInteractions.clear()
    }

    private fun currentPlayerLocation(): WorldVec? =
        Minecraft.getInstance().player?.position()?.toWorldVec()

    private const val WARP_FAILURE_WINDOW_MILLIS = 5_000L
    private const val RARE_MOB_PRIORITY_BURROW_ALPHA = 0.5
}

private fun Collection<DianaBurrowTarget>.currentTarget(playerLocation: WorldVec?): DianaBurrowTarget? {
    if (playerLocation == null) return firstOrNull()
    return minByOrNull { target -> target.location.blockCenter().distanceSq(playerLocation) }
}
