package com.skysoft.utils.input

import com.mojang.blaze3d.platform.InputConstants
import com.skysoft.utils.gui.Point
import net.minecraft.client.Minecraft
import org.lwjgl.glfw.GLFW

object InputUtilities {
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
