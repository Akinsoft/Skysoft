package com.skysoft.features.screenshot

import com.skysoft.config.SkysoftConfigGui
import com.skysoft.utils.MinecraftClient
import com.skysoft.utils.SkysoftChat
import com.skysoft.utils.SkysoftClientEvents
import com.skysoft.utils.SoundUtilities
import com.skysoft.utils.input.InputUtilities
import java.util.function.Consumer
import net.minecraft.client.Minecraft
import net.minecraft.client.Screenshot
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.contents.TranslatableContents
import org.lwjgl.glfw.GLFW

object ScreenshotManager {
    private var managerKeyWasDown = false

    fun register() {
        SkysoftClientEvents.onEndTick("Screenshot Manager keybind", ::hasManagerKeyWork) {
            processManagerKey()
        }
    }

    fun open() {
        val currentScreen = MinecraftClient.screen()
        if (currentScreen is ScreenshotManagerScreen) return
        MinecraftClient.setScreen(ScreenshotManagerScreen(currentScreen))
    }

    internal fun decorateCaptureCallback(callback: Consumer<Component>): Consumer<Component> {
        if (!config().enabled) return callback
        SoundUtilities.playScreenshotShutterSound()
        return Consumer { message -> callback.accept(replacementCaptureMessage(message)) }
    }

    private fun hasManagerKeyWork(): Boolean {
        val config = config()
        return config.enabled && (config.settings.managerKey != GLFW.GLFW_KEY_UNKNOWN || managerKeyWasDown)
    }

    private fun processManagerKey() {
        val key = config().settings.managerKey
        val isKeyDown = key != GLFW.GLFW_KEY_UNKNOWN &&
            key != GLFW.GLFW_KEY_ENTER &&
            InputUtilities.isKeyDown(key)
        if (!isKeyDown) {
            managerKeyWasDown = false
            return
        }
        if (managerKeyWasDown) return
        managerKeyWasDown = true
        if (key == GLFW.GLFW_KEY_F4 && InputUtilities.isKeyDown(GLFW.GLFW_KEY_F3)) return
        if (MinecraftClient.screen() != null) return
        open()
    }

    private fun replacementCaptureMessage(message: Component): Component {
        val translation = message.contents as? TranslatableContents
        if (translation?.key != SCREENSHOT_SUCCESS_KEY) return message
        return SkysoftChat.prefixed(Component.literal("Screenshot captured."))
    }

    private fun config() = SkysoftConfigGui.config().gui.screenshotManager

    internal fun screenshotsDirectory() = Minecraft.getInstance().gameDirectory.toPath().resolve(Screenshot.SCREENSHOT_DIR)

    private const val SCREENSHOT_SUCCESS_KEY = "screenshot.success"
}
