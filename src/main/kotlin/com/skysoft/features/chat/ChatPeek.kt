package com.skysoft.features.chat

import com.skysoft.config.SkysoftConfigGui
import com.skysoft.utils.MinecraftClient
import com.skysoft.utils.input.InputUtilities
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.components.ChatComponent
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen
import org.lwjgl.glfw.GLFW

internal object ChatPeek {
    private val config
        get() = SkysoftConfigGui.config().chat.chatPeek

    fun displayMode(displayMode: ChatComponent.DisplayMode): ChatComponent.DisplayMode {
        return if (isActive() && displayMode == ChatComponent.DisplayMode.BACKGROUND) {
            ChatComponent.DisplayMode.FOREGROUND
        } else {
            displayMode
        }
    }

    fun expandedHeight(): Int? {
        if (!isActive()) return null
        val minecraft = Minecraft.getInstance()
        return ChatComponent.getHeight(minecraft.options.chatHeightFocused().get())
    }

    private fun isActive(): Boolean {
        val settings = config
        val key = settings.settings.key
        return settings.enabled && Minecraft.getInstance().player != null &&
            key != GLFW.GLFW_KEY_UNKNOWN && !isChatPeekBlocked() && InputUtilities.isActionBindingDown(key)
    }
}

internal fun isChatPeekBlocked(screen: Screen? = MinecraftClient.screen()): Boolean = when (screen) {
    is AbstractContainerScreen<*>, is AbstractSignEditScreen -> true
    else -> false
}
