package com.skysoft.utils

import com.mojang.blaze3d.platform.InputConstants
import com.skysoft.config.KeybindNumbering
import net.minecraft.client.Minecraft
import net.minecraft.client.input.KeyEvent
import org.lwjgl.glfw.GLFW

object MinecraftInput {
    const val NO_BINDING = GLFW.GLFW_KEY_UNKNOWN
    const val UNDO_SHORTCUT_KEY = InputConstants.KEY_Z
    const val REDO_SHORTCUT_KEY = InputConstants.KEY_Y
    const val KEYPAD_SUBTRACT_KEY = GLFW.GLFW_KEY_KP_SUBTRACT
    val keybindNumbering = KeybindNumbering.GLFW

    val onTextInputFocusChange: (Any, Boolean) -> Unit = { _, _ -> }

    fun shortcutKey(event: KeyEvent): Int = event.key()

    fun scanCode(event: KeyEvent): Int = event.scancode()

    fun mouseBinding(button: Int): Int = button

    fun isMouseBinding(binding: Int): Boolean = binding in GLFW.GLFW_MOUSE_BUTTON_1..GLFW.GLFW_MOUSE_BUTTON_LAST

    fun isKeyBinding(binding: Int): Boolean = binding in GLFW.GLFW_KEY_SPACE..GLFW.GLFW_KEY_LAST

    fun isBindingDown(binding: Int): Boolean {
        val window = Minecraft.getInstance().window
        return when {
            isMouseBinding(binding) -> GLFW.glfwGetMouseButton(window.handle(), binding) == GLFW.GLFW_PRESS
            isKeyBinding(binding) -> InputConstants.isKeyDown(window, binding)
            else -> false
        }
    }

    fun bindingKey(binding: Int): InputConstants.Key =
        if (isMouseBinding(binding)) {
            InputConstants.Type.MOUSE.getOrCreate(binding)
        } else {
            InputConstants.Type.KEYSYM.getOrCreate(binding)
        }

    fun cursorPosition(): Pair<Double, Double> {
        val x = doubleArrayOf(0.0)
        val y = doubleArrayOf(0.0)
        GLFW.glfwGetCursorPos(Minecraft.getInstance().window.handle(), x, y)
        return x[0] to y[0]
    }

    @JvmStatic
    fun setCursorPosition(x: Double, y: Double) {
        GLFW.glfwSetCursorPos(Minecraft.getInstance().window.handle(), x, y)
    }
}
