package com.skysoft.utils.input

import com.mojang.blaze3d.platform.InputConstants
import com.skysoft.utils.MinecraftClient
import com.skysoft.utils.gui.Point
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import org.lwjgl.glfw.GLFW

object InputUtilities {
    private val bindingPressScreens = mutableMapOf<Int, Screen?>()

    @JvmStatic
    fun recordBindingInput(window: Long, binding: Int, action: Int) {
        val minecraft = Minecraft.getInstance()
        if (window != minecraft.window.handle()) return
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
            in GLFW.GLFW_MOUSE_BUTTON_1..GLFW.GLFW_MOUSE_BUTTON_LAST ->
                GLFW.glfwGetMouseButton(window, binding) == GLFW.GLFW_PRESS
            in GLFW.GLFW_KEY_SPACE..GLFW.GLFW_KEY_LAST -> GLFW.glfwGetKey(window, binding) == GLFW.GLFW_PRESS
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

    fun bindingName(binding: Int): String =
        if (binding == GLFW.GLFW_KEY_UNKNOWN) {
            "None"
        } else {
            InputConstants.Type.KEYSYM.getOrCreate(binding).displayName.string
        }

    fun clipboardAscii(): String = Minecraft.getInstance().keyboardHandler.clipboard.filter { it.code in 32..126 }
}
