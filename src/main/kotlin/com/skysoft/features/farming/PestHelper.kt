package com.skysoft.features.farming

import com.skysoft.config.SkysoftConfigGui
import com.skysoft.data.SkyBlockIsland
import com.skysoft.data.hypixel.HypixelLocationState
import com.skysoft.data.skyblock.GardenPestState
import com.skysoft.features.misc.MouseLock
import com.skysoft.utils.MinecraftClient
import com.skysoft.utils.SkysoftClientEvents
import com.skysoft.utils.input.InputUtilities
import net.minecraft.client.Minecraft
import org.lwjgl.glfw.GLFW

object PestHelper {
    private val config get() = SkysoftConfigGui.config().farming.pestHelper
    private val settings get() = config.settings
    private var warpKeyWasDown = false
    private var returnKeyWasDown = false

    fun register() {
        SkysoftClientEvents.onEndTick(
            "Pest Helper keybinds",
            isActive = ::hasKeyWork,
        ) { processKeys() }
        SkysoftClientEvents.onDisconnect("Pest Helper keybind reset", ::resetKeys)
    }

    private fun hasKeyWork(): Boolean =
        config.enabled &&
            (settings.warpKey != GLFW.GLFW_KEY_UNKNOWN || settings.returnKey != GLFW.GLFW_KEY_UNKNOWN) ||
            warpKeyWasDown || returnKeyWasDown

    private fun processKeys() {
        if (!config.enabled) {
            resetKeys()
            return
        }

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
                val connection = Minecraft.getInstance().connection ?: return
                connection.sendCommand("warp garden")
                if (settings.lockOnReturn) MouseLock.setLocked(true)
            }
        }
    }

    private fun warpToPests() {
        if (!SkyBlockIsland.GARDEN.isInIsland()) return
        val plot = GardenPestState.current.lastSpawn?.plot ?: return
        val connection = Minecraft.getInstance().connection ?: return
        if (settings.savePosition) connection.sendCommand("setspawn")
        connection.sendCommand("tptoplot ${plot.commandName()}")
        if (settings.unlockOnWarp) MouseLock.setLocked(false)
    }

    private fun String.commandName(): String = if (this == "The Barn") "barn" else this

    private fun isKeyDown(key: Int): Boolean =
        key != GLFW.GLFW_KEY_UNKNOWN && key != GLFW.GLFW_KEY_ENTER && InputUtilities.isActionBindingDown(key)

    private fun resetKeys() {
        warpKeyWasDown = false
        returnKeyWasDown = false
    }
}
