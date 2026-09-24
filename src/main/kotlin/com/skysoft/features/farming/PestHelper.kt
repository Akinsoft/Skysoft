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
    private var sharedKeyWasDown = false
    private var warpKeyWasDown = false
    private var returnKeyWasDown = false
    private var sharedPositionSaved = false
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
        config.enabled && hasConfiguredKey() ||
            sharedKeyWasDown || warpKeyWasDown || returnKeyWasDown || sharedPositionSaved || pendingWarp != null

    private fun hasConfiguredKey(): Boolean = if (settings.sharedKeybind) {
        settings.sharedKey != GLFW.GLFW_KEY_UNKNOWN
    } else {
        settings.warpKey != GLFW.GLFW_KEY_UNKNOWN || settings.returnKey != GLFW.GLFW_KEY_UNKNOWN
    }

    private fun processKeys() {
        if (!config.enabled) {
            reset()
            return
        }
        updatePendingWarp()
        if (!settings.sharedKeybind) sharedPositionSaved = false

        val sharedKeyDown = isKeyDown(settings.sharedKey)
        val warpKeyDown = isKeyDown(settings.warpKey)
        val returnKeyDown = isKeyDown(settings.returnKey)
        val sharedPressed = sharedKeyDown && !sharedKeyWasDown
        val warpPressed = warpKeyDown && !warpKeyWasDown
        val returnPressed = returnKeyDown && !returnKeyWasDown
        sharedKeyWasDown = sharedKeyDown
        warpKeyWasDown = warpKeyDown
        returnKeyWasDown = returnKeyDown
        if (MinecraftClient.screen() != null) return

        if (settings.sharedKeybind) {
            if (sharedPressed) processSharedKey()
        } else {
            when {
                warpPressed -> warpToPests(settings.savePosition, settings.unlockOnWarp)
                returnPressed -> returnToPosition(settings.lockOnReturn)
            }
        }
    }

    private fun processSharedKey() {
        val totalPests = GardenPestState.current.totalPests ?: return
        if (totalPests <= 0) {
            returnToPosition(lockMouse = settings.unlockAndLock)
            return
        }
        val savePosition = !sharedPositionSaved
        warpToPests(
            savePosition = savePosition,
            unlockMouse = settings.unlockAndLock,
            rememberSharedPosition = savePosition,
        )
    }

    private fun warpToPests(savePosition: Boolean, unlockMouse: Boolean, rememberSharedPosition: Boolean = false) {
        if (pendingWarp != null || !SkyBlockIsland.GARDEN.isInIsland()) return
        val plot = GardenPestState.current.lastSpawn?.plot ?: return
        val connection = Minecraft.getInstance().connection ?: return
        if (savePosition) {
            pendingWarp = PendingWarp(
                plot = plot,
                unlockMouse = unlockMouse,
                rememberSharedPosition = rememberSharedPosition,
                requestedAtNanos = System.nanoTime(),
                locationVersion = HypixelLocationState.locationVersion,
            )
            connection.sendCommand("setspawn")
        } else {
            teleportToPlot(plot, unlockMouse)
        }
    }

    private fun returnToPosition(lockMouse: Boolean) {
        if (!HypixelLocationState.inSkyBlock) return
        pendingWarp = null
        val connection = Minecraft.getInstance().connection ?: return
        connection.sendCommand("warp garden")
        sharedPositionSaved = false
        if (lockMouse) MouseLock.setLocked(true)
    }

    private fun updatePendingWarp() {
        val pending = pendingWarp ?: return
        if (
            !config.enabled || (!settings.savePosition && !settings.sharedKeybind) ||
            !SkyBlockIsland.GARDEN.isInIsland() ||
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
        teleportToPlot(pending.plot, pending.unlockMouse)
        if (pending.rememberSharedPosition) sharedPositionSaved = true
    }

    private fun teleportToPlot(plot: String, unlockMouse: Boolean) {
        val connection = Minecraft.getInstance().connection ?: return
        connection.sendCommand("tptoplot ${plot.commandName()}")
        if (unlockMouse) MouseLock.setLocked(false)
    }

    private fun String.commandName(): String = if (this == "The Barn") "barn" else this

    private fun isKeyDown(key: Int): Boolean =
        key != GLFW.GLFW_KEY_UNKNOWN && key != GLFW.GLFW_KEY_ENTER && InputUtilities.isActionBindingDown(key)

    private fun reset() {
        sharedKeyWasDown = false
        warpKeyWasDown = false
        returnKeyWasDown = false
        sharedPositionSaved = false
        pendingWarp = null
    }

    private data class PendingWarp(
        val plot: String,
        val unlockMouse: Boolean,
        val rememberSharedPosition: Boolean,
        val requestedAtNanos: Long,
        val locationVersion: Long,
    )

    private const val SAVE_TIMEOUT_NANOS = 5_000_000_000L
}
