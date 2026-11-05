package com.skysoft.features.event.diana

import com.skysoft.config.SkysoftConfigGui
import com.skysoft.data.hypixel.SkyBlockProfileApi
import com.skysoft.data.hypixel.HypixelPartyApi
import com.skysoft.data.ProfileStorageApi
import com.skysoft.data.skyblock.MayorPerkApi
import com.skysoft.events.input.ItemUseEvents
import com.skysoft.events.particle.ClientParticleEvent
import com.skysoft.events.particle.ClientParticleEvents
import com.skysoft.utils.PlayerUtilities.localPlayerLocation
import com.skysoft.utils.chat.ChatEvents
import com.skysoft.utils.chat.ChatMessageVisibility
import com.skysoft.utils.render.SkysoftRenderContext
import com.skysoft.utils.render.WorldRenderDispatcher
import com.skysoft.utils.SkysoftClientEvents

object DianaBurrowHelper {
    private val config get() = SkysoftConfigGui.config().events.diana
    private val burrowHelper get() = config.burrowHelper
    private val settings get() = burrowHelper.settings
    private val details get() = burrowHelper.details
    private var wasOnHub = false

    fun register() {
        ProfileStorageApi.registerConsumer("Diana Burrow Helper", ::isEnabled)
        MayorPerkApi.registerConsumer("Diana helpers") { config.isAnyFeatureEnabled() }
        HypixelPartyApi.registerConsumer("Diana helpers") { config.isAnyFeatureEnabled() }
        SkysoftClientEvents.onEndTick(
            "Diana Burrow Helper tick",
            isActive = { isEnabled() || DianaQuickWarps.enabled || hasRuntimeState() },
        ) { onTick() }
        SkysoftClientEvents.onDisconnect("Diana Burrow Helper disconnect reset", ::clearSession)
        SkysoftClientEvents.onClientStopping("Diana Hub Surface Cache save") {
            DianaHubSurfaceCache.flush()
        }
        SkyBlockProfileApi.onProfileChange("Diana Burrow Helper profile change", ::isEnabled) { profile ->
            DianaBurrowStorage.saveCurrentTargets()
            unloadTargets()
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
            DianaSpadeGuess.handleItemUse(event)
            false
        }
        ChatEvents.onVisibleMessage("Diana Burrow chat", { isEnabled() || DianaQuickWarps.enabled }) { message ->
            if (message.isSystemLike) {
                if (DianaQuickWarps.enabled) DianaQuickWarps.handleWarpFailure(message.cleanText)
                if (isEnabled()) DianaBurrowInteractions.onMessage(message)
            }
            ChatMessageVisibility.SHOW
        }
        WorldRenderDispatcher.registerHandler(
            "Diana Burrow world rendering",
            isActive = { isEnabled() && DianaEventState.isOnHub() },
            handler = ::onRenderWorld,
        )
        DianaQuickWarps.register()
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
        wasOnHub || DianaQuickWarps.hasRuntimeState || DianaBurrowTargetTracker.hasTargets()

    private fun onTick() {
        val now = System.currentTimeMillis()
        val onHub = DianaEventState.isOnHub()
        if (isEnabled()) {
            if (onHub) {
                wasOnHub = true
                DianaBurrowStorage.restoreCurrentProfile(now)
                DianaBurrowChainState.restoreCurrentProfile(now)
                DianaHubSurfaceCache.onTick()
                DianaBurrowParticleDetector.prune(now)
                DianaArrowGuess.prune(now)
                DianaRemovedBurrows.prune(now)
                DianaBurrowInteractions.onTick(now)
            } else {
                suspendTargets(now)
            }
        } else if (wasOnHub || DianaBurrowTargetTracker.hasTargets()) {
            unloadTargets()
            wasOnHub = false
        }

        DianaQuickWarps.onTick(now, onHub)
    }

    private fun handleParticle(event: ClientParticleEvent) {
        val now = System.currentTimeMillis()
        DianaBurrowParticleDetector.handle(event, now)
        DianaSpadeGuess.handleParticle(event, now)
        DianaArrowGuess.handleParticle(event, now)
    }

    private fun shouldHideArrowParticle(event: ClientParticleEvent): Boolean =
        details.hideGuessArrows && DianaParticleClassifier.isArrowParticle(event)

    private fun onRenderWorld(context: SkysoftRenderContext) {
        if (!DianaEventState.canUseHelper()) return
        renderTargets(context, DianaBurrowTargetTracker.snapshot())
    }

    internal fun renderTargets(context: SkysoftRenderContext, targets: Collection<DianaBurrowTarget>) {
        val playerLocation = localPlayerLocation() ?: return
        val target = targets.nearestTo(playerLocation) ?: return
        val labelColors = details.burrowLabelColors()
        DianaBurrowRenderer.renderWorld(
            context = context,
            targets = targets,
            currentTarget = target,
            playerLocation = playerLocation,
            style = DianaBurrowRenderStyle(
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
                    config.rareMobSharing.enabled && DianaRareMobSharing.remotePriorityLocation != null
                ) {
                    RARE_MOB_PRIORITY_BURROW_ALPHA
                } else {
                    1.0
                },
            ),
        )
    }

    private fun clearSession() {
        saveAndUnloadTargets(System.currentTimeMillis())
        DianaQuickWarps.clear()
        wasOnHub = false
    }

    private fun suspendTargets(now: Long) {
        if (wasOnHub || DianaBurrowTargetTracker.hasTargets()) {
            saveAndUnloadTargets(now)
        } else {
            clearTransientTracking()
        }
        wasOnHub = false
    }

    private fun saveAndUnloadTargets(now: Long) {
        DianaBurrowStorage.saveCurrentTargets(now)
        DianaHubSurfaceCache.saveInBackground()
        unloadTargets()
    }

    private fun unloadTargets() {
        clearTargets(persistTargets = false)
        DianaBurrowStorage.resetLoadedProfile()
        DianaBurrowChainState.resetLoadedProfile()
    }

    private fun clearTargets(persistTargets: Boolean) {
        DianaBurrowTargetTracker.clear(persist = persistTargets)
        clearTransientTracking()
    }

    private fun clearTransientTracking() {
        DianaBurrowParticleDetector.clear()
        DianaSpadeGuess.clear()
        DianaArrowGuess.clear()
        DianaBurrowInteractions.clear()
    }

    private const val RARE_MOB_PRIORITY_BURROW_ALPHA = 0.5
}
