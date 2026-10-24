package com.skysoft.utils.input

import com.mojang.blaze3d.platform.InputConstants
import com.skysoft.utils.MinecraftClient
import com.skysoft.utils.MinecraftInput
import com.skysoft.utils.gui.Point
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.KeyEvent

object InputUtilities {
    private val HISTORY_SHORTCUT_KEYS = setOf(MinecraftInput.UNDO_SHORTCUT_KEY, MinecraftInput.REDO_SHORTCUT_KEY)
    private val bindingPressScreens = mutableMapOf<Int, Screen?>()
    var isRepeatedBindingInput = false
        private set

    @JvmStatic
    fun recordKeyInput(window: Long, key: Int, action: Int) {
        recordBindingInput(window, key, action)
    }

    @JvmStatic
    fun recordMouseInput(window: Long, button: Int, action: Int) {
        recordBindingInput(window, MinecraftInput.mouseBinding(button), action)
    }

    private fun recordBindingInput(window: Long, binding: Int, action: Int) {
        val minecraft = Minecraft.getInstance()
        if (window != minecraft.window.handle()) return
        isRepeatedBindingInput = action == InputConstants.REPEAT
        when (action) {
            InputConstants.PRESS -> bindingPressScreens[binding] = MinecraftClient.screen(minecraft)
            InputConstants.RELEASE -> bindingPressScreens.remove(binding)
        }
    }

    fun isActionBindingDown(binding: Int): Boolean {
        val minecraft = Minecraft.getInstance()
        if (!minecraft.isWindowActive || bindingPressScreens[binding] !== MinecraftClient.screen(minecraft)) {
            bindingPressScreens.remove(binding)
        }
        return bindingPressScreens.containsKey(binding) && isBindingDown(binding)
    }

    fun isBindingDown(binding: Int): Boolean = MinecraftInput.isBindingDown(binding)

    fun isMouseButtonDown(button: Int): Boolean = isBindingDown(MinecraftInput.mouseBinding(button))

    fun isHistoryShortcut(event: KeyEvent): Boolean =
        event.hasControlDownWithQuirk() && MinecraftInput.shortcutKey(event) in HISTORY_SHORTCUT_KEYS

    fun isRedoShortcut(event: KeyEvent): Boolean =
        MinecraftInput.shortcutKey(event) == MinecraftInput.REDO_SHORTCUT_KEY || Minecraft.getInstance().hasShiftDown()

    fun isShiftDown(): Boolean =
        isBindingDown(InputConstants.KEY_LSHIFT) || isBindingDown(InputConstants.KEY_RSHIFT)

    fun scaledMousePosition(minecraft: Minecraft): Point {
        val window = minecraft.window
        return Point(
            minecraft.mouseHandler.getScaledXPos(window).toInt(),
            minecraft.mouseHandler.getScaledYPos(window).toInt(),
        )
    }

    fun bindingName(binding: Int): String =
        if (binding == MinecraftInput.NO_BINDING) "None" else MinecraftInput.bindingKey(binding).displayName.string

    fun clipboardAscii(): String = Minecraft.getInstance().keyboardHandler.clipboard.filter { it.code in 32..126 }
}
