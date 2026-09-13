package com.skysoft.features.farming

import com.skysoft.config.SkysoftConfigGui
import com.skysoft.data.SkyBlockIsland
import com.skysoft.data.hypixel.HypixelLocationState
import com.skysoft.data.skyblock.GardenPestState
import com.skysoft.features.misc.MouseLock
import com.skysoft.utils.MinecraftClient
import com.skysoft.utils.SkysoftChat
import com.skysoft.utils.SkysoftClientEvents
import com.skysoft.utils.chat.ChatEvents
import com.skysoft.utils.chat.ChatMessageVisibility
import com.skysoft.utils.input.InputUtilities
import net.minecraft.client.Minecraft
import org.lwjgl.glfw.GLFW

object PestHelper {
    private val config get() = SkysoftConfigGui.config().farming.pests.pestHelper
    private val settings get() = config.settings
    private var warpKeyWasDown = false
    private var returnKeyWasDown = false
    private var pendingWarp: PendingWarp? = null

    fun register() {
        SkysoftClientEvents.onEndTick(
            "Pest Helper keybinds",
            isActive = ::hasKeyWork,
        ) { processKeys() }
        ChatEvents.onVisibleMessage("Pest Helper save confirmation", isActive = { pendingWarp != null }) { message ->
            if (message.isSystemLike && message.cleanText.trim() == "Your spawn location has been set!") {
                completePendingWarp()
            }
            ChatMessageVisibility.SHOW
        }
        SkysoftClientEvents.onDisconnect("Pest Helper disconnect reset", ::reset)
    }

    private fun hasKeyWork(): Boolean =
        config.enabled &&
            (settings.warpKey != GLFW.GLFW_KEY_UNKNOWN || settings.returnKey != GLFW.GLFW_KEY_UNKNOWN) ||
            warpKeyWasDown || returnKeyWasDown || pendingWarp != null

    private fun processKeys() {
        if (!config.enabled) {
            reset()
            return
        }
        updatePendingWarp()

        val warpKeyDown = isKeyDown(settings.warpKey)
        val returnKeyDown = isKeyDown(settings.returnKey)
        val warpPressed = warpKeyDown && !warpKeyWasDown
        val returnPressed = returnKeyDown && !returnKeyWasDown
        warpKeyWasDown = warpKeyDown
        returnKeyWasDown = returnKeyDown
        if ((!warpPressed && !returnPressed) || MinecraftClient.screen() != null) return

        when {
            warpPressed -> warpToPests()
            returnPressed && HypixelLocationState.inSkyBlock -> {
                pendingWarp = null
                val connection = Minecraft.getInstance().connection ?: return
                connection.sendCommand("warp garden")
                if (settings.lockOnReturn) MouseLock.setLocked(true)
            }
        }
    }

    private fun warpToPests() {
        if (pendingWarp != null || !SkyBlockIsland.GARDEN.isInIsland()) return
        val plot = GardenPestState.current.lastSpawn?.plot ?: return
        val connection = Minecraft.getInstance().connection ?: return
        if (settings.savePosition) {
            pendingWarp = PendingWarp(plot, System.nanoTime(), HypixelLocationState.locationVersion)
            connection.sendCommand("setspawn")
        } else {
            teleportToPlot(plot)
        }
    }

    private fun updatePendingWarp() {
        val pending = pendingWarp ?: return
        if (
            !config.enabled || !settings.savePosition || !SkyBlockIsland.GARDEN.isInIsland() ||
            pending.locationVersion != HypixelLocationState.locationVersion
        ) {
            pendingWarp = null
        } else if (System.nanoTime() - pending.requestedAtNanos >= SAVE_TIMEOUT_NANOS) {
            pendingWarp = null
            SkysoftChat.error("Pest warp cancelled: Hypixel did not confirm saving your position.")
        }
    }

    private fun completePendingWarp() {
        updatePendingWarp()
        val pending = pendingWarp ?: return
        pendingWarp = null
        teleportToPlot(pending.plot)
    }

    private fun teleportToPlot(plot: String) {
        val connection = Minecraft.getInstance().connection ?: return
        connection.sendCommand("tptoplot ${plot.commandName()}")
        if (settings.unlockOnWarp) MouseLock.setLocked(false)
    }

    private fun String.commandName(): String = if (this == "The Barn") "barn" else this

    private fun isKeyDown(key: Int): Boolean =
        key != GLFW.GLFW_KEY_UNKNOWN && key != GLFW.GLFW_KEY_ENTER && InputUtilities.isActionBindingDown(key)

    private fun reset() {
        warpKeyWasDown = false
        returnKeyWasDown = false
        pendingWarp = null
    }

    private data class PendingWarp(val plot: String, val requestedAtNanos: Long, val locationVersion: Long)

    private const val SAVE_TIMEOUT_NANOS = 5_000_000_000L
}
