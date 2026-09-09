package com.skysoft.features.event.diana

import com.skysoft.config.SkysoftConfigGui
import com.skysoft.utils.MinecraftClient
import com.skysoft.utils.WorldVec
import com.skysoft.utils.input.InputUtilities
import com.skysoft.utils.toWorldVec
import net.minecraft.client.Minecraft
import org.lwjgl.glfw.GLFW

internal object DianaQuickWarps {
    private val config get() = SkysoftConfigGui.config().events.diana
    private val quickWarps get() = config.quickWarps
    private val quickWarpSettings get() = quickWarps.settings
    private val disabledWarpCommands = mutableSetOf<String>()
    private var warpKeyWasDown = false
    private var lastWarpCommand: DianaWarpPoint? = null
    private var lastWarpAtMillis = 0L

    val enabled: Boolean get() = quickWarps.enabled
    val hasRuntimeState: Boolean get() = warpKeyWasDown

    fun register() {
        DianaWarpTitleRenderer.register(::activeWarpSuggestion)
    }

    fun onTick(now: Long, onHub: Boolean) {
        if (enabled && onHub) {
            handleWarpKey(now)
        } else {
            warpKeyWasDown = false
        }
    }

    fun clear() {
        disabledWarpCommands.clear()
        lastWarpCommand = null
        lastWarpAtMillis = 0L
        warpKeyWasDown = false
    }

    fun handleWarpFailure(message: String) {
        if (!message.contains("haven't unlocked this fast travel destination", ignoreCase = true)) return
        val failedWarp = lastWarpCommand ?: return
        if (System.currentTimeMillis() - lastWarpAtMillis > WARP_FAILURE_WINDOW_MILLIS) return
        disabledWarpCommands += failedWarp.command
        lastWarpCommand = null
    }

    private fun handleWarpKey(now: Long) {
        val key = quickWarpSettings.warpKey
        val keyDown = key != GLFW.GLFW_KEY_UNKNOWN && key != GLFW.GLFW_KEY_ENTER && InputUtilities.isActionBindingDown(key)
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

    private fun currentPlayerLocation(): WorldVec? =
        Minecraft.getInstance().player?.position()?.toWorldVec()

    private const val WARP_FAILURE_WINDOW_MILLIS = 5_000L
}
