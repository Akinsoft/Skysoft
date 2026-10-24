package com.skysoft.utils

import com.mojang.blaze3d.platform.InputConstants
import com.skysoft.config.KeybindNumbering
import java.nio.FloatBuffer
import net.minecraft.client.Minecraft
import net.minecraft.client.input.KeyEvent
import org.lwjgl.sdl.SDLMouse
import org.lwjgl.sdl.SDLScancode
import org.lwjgl.system.MemoryStack

object MinecraftInput {
    const val NO_BINDING = SDLScancode.SDL_SCANCODE_UNKNOWN
    const val UNDO_SHORTCUT_KEY = InputConstants.KEYCODE_Z
    const val REDO_SHORTCUT_KEY = InputConstants.KEYCODE_Y
    const val KEYPAD_SUBTRACT_KEY = SDLScancode.SDL_SCANCODE_KP_MINUS
    val keybindNumbering = KeybindNumbering.SDL

    val onTextInputFocusChange: (Any, Boolean) -> Unit = { owner, focused ->
        Minecraft.getInstance().textInputManager().onTextInputFocusChange(owner, focused)
    }

    fun shortcutKey(event: KeyEvent): Int = event.shortcutKey()

    fun scanCode(event: KeyEvent): Int = event.keycode()

    fun mouseBinding(button: Int): Int = button + KeybindNumbering.SDL_MOUSE_BINDING_OFFSET

    fun isMouseBinding(binding: Int): Boolean =
        mouseButton(binding) in InputConstants.MOUSE_BUTTON_LEFT..InputConstants.MOUSE_BUTTON_8

    fun isKeyBinding(binding: Int): Boolean = binding in NO_BINDING + 1 until SDLScancode.SDL_SCANCODE_COUNT

    fun isBindingDown(binding: Int): Boolean = when {
        isMouseBinding(binding) -> {
            val buttonMask = 1 shl (mouseButton(binding) - 1)
            SDLMouse.SDL_GetMouseState(null as FloatBuffer?, null as FloatBuffer?) and buttonMask != 0
        }
        isKeyBinding(binding) -> InputConstants.isKeyDown(binding)
        else -> false
    }

    fun bindingKey(binding: Int): InputConstants.Key =
        if (isMouseBinding(binding)) {
            InputConstants.Type.MOUSE.getOrCreate(mouseButton(binding))
        } else {
            InputConstants.Type.KEYBOARD.getOrCreate(binding)
        }

    fun cursorPosition(): Pair<Double, Double> = MemoryStack.stackPush().use { stack ->
        val x = stack.mallocFloat(1)
        val y = stack.mallocFloat(1)
        SDLMouse.SDL_GetMouseState(x, y)
        x[0].toDouble() to y[0].toDouble()
    }

    @JvmStatic
    fun setCursorPosition(x: Double, y: Double) {
        SDLMouse.SDL_WarpMouseInWindow(Minecraft.getInstance().window.handle(), x.toFloat(), y.toFloat())
    }

    private fun mouseButton(binding: Int): Int = binding - KeybindNumbering.SDL_MOUSE_BINDING_OFFSET
}
