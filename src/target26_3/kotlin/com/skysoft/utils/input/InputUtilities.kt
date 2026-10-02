package com.skysoft.utils.input

import com.mojang.blaze3d.platform.InputConstants
import com.skysoft.utils.MinecraftClient
import com.skysoft.utils.gui.Point
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.client.input.MouseButtonInfo
import java.nio.FloatBuffer
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import com.skysoft.utils.input.LegacyInputCodes as GLFW
import io.github.notenoughupdates.moulconfig.platform.ModernInputBridge
import org.lwjgl.sdl.SDLMouse

object InputUtilities {
    @JvmStatic fun keyCode(event: KeyEvent): Int = ModernInputBridge.toLegacyKey(event.key())
    @JvmStatic fun scanCode(event: KeyEvent): Int = event.key()
    @JvmStatic fun mouseButton(event: MouseButtonEvent): Int = ModernInputBridge.toLegacyMouseButton(event.button())
    @JvmStatic fun mouseButton(event: MouseButtonInfo): Int = ModernInputBridge.toLegacyMouseButton(event.button())
    @JvmStatic fun action(nativeAction: Int): Int = if (nativeAction == InputConstants.REPEAT) GLFW.GLFW_REPEAT else nativeAction

    private val bindingPressScreens = mutableMapOf<Int, Screen?>()
    var isRepeatedBindingInput = false
        private set

    @JvmStatic
    fun recordBindingInput(window: Long, binding: Int, action: Int) {
        val minecraft = Minecraft.getInstance()
        if (window != minecraft.window.handle()) return
        isRepeatedBindingInput = action == GLFW.GLFW_REPEAT
        when (action) {
            GLFW.GLFW_PRESS -> bindingPressScreens[binding] = MinecraftClient.screen(minecraft)
            GLFW.GLFW_RELEASE -> bindingPressScreens.remove(binding)
        }
    }

    fun isActionBindingDown(binding: Int): Boolean {
        val minecraft = Minecraft.getInstance()
        if (!minecraft.isWindowActive || bindingPressScreens[binding] !== MinecraftClient.screen(minecraft)) {
            bindingPressScreens.remove(binding)
        }
        return bindingPressScreens.containsKey(binding) && isBindingDown(binding)
    }

    fun isBindingDown(binding: Int): Boolean {
        val window = Minecraft.getInstance().window.handle()
        return when (binding) {
            in GLFW.GLFW_MOUSE_BUTTON_1..GLFW.GLFW_MOUSE_BUTTON_LAST -> {
                val buttons = SDLMouse.SDL_GetMouseState(null as FloatBuffer?, null as FloatBuffer?)
                buttons and (1 shl (ModernInputBridge.toNativeMouseButton(binding) - 1)) != 0
            }
            in GLFW.GLFW_KEY_SPACE..GLFW.GLFW_KEY_LAST -> ModernInputBridge.toNativeKey(binding).let { nativeKey ->
                nativeKey != 0 && InputConstants.isKeyDown(nativeKey)
            }
            else -> false
        }
    }

    fun isShiftDown(): Boolean =
        isBindingDown(GLFW.GLFW_KEY_LEFT_SHIFT) || isBindingDown(GLFW.GLFW_KEY_RIGHT_SHIFT)

    fun scaledMousePosition(minecraft: Minecraft): Point {
        val window = minecraft.window
        return Point(
            minecraft.mouseHandler.getScaledXPos(window).toInt(),
            minecraft.mouseHandler.getScaledYPos(window).toInt(),
        )
    }

    fun bindingName(binding: Int): String = when (binding) {
        GLFW.GLFW_KEY_UNKNOWN -> "None"
        in GLFW.GLFW_MOUSE_BUTTON_1..GLFW.GLFW_MOUSE_BUTTON_LAST ->
            InputConstants.Type.MOUSE.getOrCreate(ModernInputBridge.toNativeMouseButton(binding)).displayName.string
        else -> InputConstants.Type.KEYBOARD.getOrCreate(ModernInputBridge.toNativeKey(binding)).displayName.string
    }

    fun clipboardAscii(): String = Minecraft.getInstance().keyboardHandler.clipboard.filter { it.code in 32..126 }
}
