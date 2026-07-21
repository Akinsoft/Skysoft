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
    private val settings get() = config.settings
    private val disabledWarpCommands = mutableSetOf<String>()
    private var warpKeyWasDown = false
    private var lastWarpCommand: DianaWarpPoint? = null
    private var lastWarpAtMillis = 0L
    private var wasOnHub = false

    fun register() {
        ProfileStorageApi.registerConsumer("Diana Burrow Helper") { config.enabled }
        MayorPerkApi.registerConsumer("Diana Burrow Helper") { config.enabled }
        HypixelPartyApi.registerConsumer("Diana Burrow Helper") { config.enabled }
        DianaBurrowStorage.register()
        SkysoftClientEvents.onEndTick(
            "Diana Burrow Helper tick",
            isActive = { isEnabled() || hasRuntimeState() },
        ) { onTick() }
        SkysoftClientEvents.onDisconnect("Diana Burrow Helper disconnect reset", ::clearSession)
        SkyBlockProfileApi.onProfileChange("Diana Burrow Helper profile change", { config.enabled }) { profile ->
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
        ChatEvents.onVisibleMessage("Diana Burrow chat", ::isEnabled) { message ->
            if (message.isSystemLike) {
                handleWarpFailure(message.cleanText)
                DianaBurrowInteractions.onMessage(message)
            }
            ChatMessageVisibility.SHOW
        }
        WorldRenderDispatcher.registerHandler(
            "Diana Burrow world rendering",
            isActive = { config.enabled && DianaEventState.isOnHub() },
            handler = ::onRenderWorld,
        )
        DianaWarpTitleRenderer.register(::activeWarpSuggestion)
        DianaBurrowInteractions.register()
        DianaLobbyCompromisedWatcher.register()
        DianaRareMobSharing.register()
        MythologicalRitualTracker.register()
    }

    private fun isEnabled(): Boolean = config.enabled

    private fun hasRuntimeState(): Boolean =
        wasOnHub || warpKeyWasDown || DianaBurrowTargetTracker.hasTargets()

    private fun onTick() {
        val now = System.currentTimeMillis()
        if (!config.enabled) {
            clearTargets(persistTargets = false)
            DianaBurrowStorage.resetLoadedProfile()
            DianaBurrowChainState.resetLoadedProfile()
            wasOnHub = false
            warpKeyWasDown = false
            return
        }
        if (!DianaEventState.isOnHub()) {
            suspendTargets(now)
            warpKeyWasDown = false
            return
        }

        wasOnHub = true
        DianaBurrowStorage.restoreCurrentProfile(now)
        DianaBurrowChainState.restoreCurrentProfile(now)
        DianaBurrowStorage.refreshCurrentTargets(now)
        DianaHubSurfaceCache.onTick(now)
        DianaBurrowParticleDetector.prune(now)
        DianaArrowGuess.prune(now)
        DianaBurrowTargetTracker.prune(now)
        DianaBurrowInteractions.onTick(now)
        handleWarpKey(now)
    }

    private fun handleParticle(event: ClientParticleEvent) {
        if (!DianaEventState.canUseHelper()) return
        val now = System.currentTimeMillis()
        DianaBurrowParticleDetector.handle(event, now)
        DianaSpadeGuess.handleParticle(event, now)
        DianaArrowGuess.handleParticle(event, now)
    }

    private fun shouldHideArrowParticle(event: ClientParticleEvent): Boolean =
        DianaEventState.canUseHelper() && config.details.hideGuessArrows && DianaParticleClassifier.isArrowParticle(event)

    private fun onItemUse(event: ItemUseEvent) {
        if (DianaEventState.canUseHelper()) {
            DianaSpadeGuess.handleItemUse(event)
        }
    }

    private fun onRenderWorld(context: SkysoftRenderContext) {
        if (!config.enabled || !DianaEventState.isOnHub()) return
        if (!DianaEventState.canUseHelper()) return
        val targets = DianaBurrowTargetTracker.snapshot()
        renderTargets(context, targets)
    }

    internal fun renderTargets(context: SkysoftRenderContext, targets: Collection<DianaBurrowTarget>) {
        val playerLocation = currentPlayerLocation()
        val target = targets.currentTarget(playerLocation) ?: return
        DianaBurrowRenderer.renderWorld(
            context = context,
            targets = targets,
            currentTarget = target,
            drawCrosshairLine = settings.crosshairLine && !DianaRareMobSharing.hasActiveTarget(),
            boldLabels = config.details.boldText,
            labelFormat = config.details.labelFormat,
            boxStyle = config.details.burrowBoxStyle(),
            showClickCounter = settings.clickCounter,
            clickCounterPosition = settings.clickCounterPosition,
            visualAlphaScale = if (DianaRareMobSharing.remotePriorityTarget != null) {
                RARE_MOB_PRIORITY_BURROW_ALPHA
            } else {
                1.0
            },
        )
    }

    private fun handleWarpKey(now: Long) {
        val key = settings.warpKey
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
        if (!settings.warpHint || MinecraftClient.screen() != null) return null
        val playerLocation = currentPlayerLocation() ?: return null
        DianaRareMobSharing.remotePriorityTarget?.let { target ->
            return currentWarpSuggestion(target.sharedLocation, playerLocation)
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
            minSavings = settings.minWarpSavings.toDouble(),
            disabledCommands = disabledWarpCommands,
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
        DianaHubSurfaceCache.saveNow()
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
            DianaHubSurfaceCache.saveNow()
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
