package com.skysoft.utils.input

import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.client.input.MouseButtonInfo
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.lwjgl.sdl.SDLMouse
import org.lwjgl.sdl.SDLScancode

class InputCompatibilityTest {
    @Test fun nativeKeyboardEventsRetainSavedBindingIds() {
        val letter = KeyEvent(SDLScancode.SDL_SCANCODE_A, 'a'.code, 0)
        assertEquals(LegacyInputCodes.GLFW_KEY_A, InputUtilities.keyCode(letter))
        assertEquals(SDLScancode.SDL_SCANCODE_A, InputUtilities.scanCode(letter))
        val modifier = KeyEvent(SDLScancode.SDL_SCANCODE_LGUI, 0, 0)
        assertEquals(343 /* original GLFW left Super */, InputUtilities.keyCode(modifier))
        assertEquals(LegacyInputCodes.GLFW_KEY_UNKNOWN, InputUtilities.keyCode(KeyEvent(0, 0, 0)))
        assertEquals(LegacyInputCodes.GLFW_REPEAT, InputUtilities.action(InputConstants.REPEAT))
        assertEquals(LegacyInputCodes.GLFW_PRESS, InputUtilities.action(InputConstants.PRESS))
        assertEquals(LegacyInputCodes.GLFW_RELEASE, InputUtilities.action(InputConstants.RELEASE))
    }

    @Test fun nativeMouseEventsKeepOriginalLogicalButtonOrder() {
        listOf(SDLMouse.SDL_BUTTON_LEFT to LegacyInputCodes.GLFW_MOUSE_BUTTON_LEFT,
            SDLMouse.SDL_BUTTON_RIGHT to LegacyInputCodes.GLFW_MOUSE_BUTTON_RIGHT,
            SDLMouse.SDL_BUTTON_MIDDLE to 2 /* original logical middle button */,
            SDLMouse.SDL_BUTTON_X1 to 3 /* original logical side button */).forEach { (native, legacy) ->
            val info = MouseButtonInfo(native, 0)
            assertEquals(legacy, InputUtilities.mouseButton(info))
            assertEquals(legacy, InputUtilities.mouseButton(MouseButtonEvent(12.0, 19.0, info)))
        }
    }
}
