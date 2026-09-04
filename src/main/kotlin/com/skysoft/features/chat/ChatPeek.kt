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

internal enum class ChatPeekState {
    DISABLED,
    NO_PLAYER,
    KEY_UNBOUND,
    SCREEN_OPEN,
    KEY_RELEASED,
    ACTIVE,
}

internal object ChatPeek {
    private val config
        get() = SkysoftConfigGui.config().chat.chatPeek

    fun displayMode(displayMode: ChatComponent.DisplayMode): ChatComponent.DisplayMode {
        val state = currentState()
        return chatPeekDisplayMode(displayMode, state == ChatPeekState.ACTIVE)
    }

    fun expandedHeight(): Int? {
        if (currentState() != ChatPeekState.ACTIVE) return null
        val minecraft = Minecraft.getInstance()
        return ChatComponent.getHeight(minecraft.options.chatHeightFocused().get())
    }

    internal fun currentState(): ChatPeekState {
        val settings = config
        val key = settings.settings.key
        return chatPeekState(
            isEnabled = settings.enabled,
            key = key,
            hasPlayer = { Minecraft.getInstance().player != null },
            isKeyDown = { InputUtilities.isActionBindingDown(key) },
            isPeekBlocked = { isChatPeekBlocked() },
        )
    }
}

internal fun isChatPeekBlocked(screen: Screen? = MinecraftClient.screen()): Boolean = when (screen) {
    is AbstractContainerScreen<*>, is AbstractSignEditScreen -> true
    else -> false
}

internal inline fun chatPeekState(
    isEnabled: Boolean,
    key: Int,
    hasPlayer: () -> Boolean,
    isKeyDown: () -> Boolean,
    isPeekBlocked: () -> Boolean,
): ChatPeekState = when {
    !isEnabled -> ChatPeekState.DISABLED
    !hasPlayer() -> ChatPeekState.NO_PLAYER
    key == GLFW.GLFW_KEY_UNKNOWN -> ChatPeekState.KEY_UNBOUND
    isPeekBlocked() -> ChatPeekState.SCREEN_OPEN
    !isKeyDown() -> ChatPeekState.KEY_RELEASED
    else -> ChatPeekState.ACTIVE
}

internal fun chatPeekDisplayMode(
    displayMode: ChatComponent.DisplayMode,
    isActive: Boolean,
): ChatComponent.DisplayMode = if (isActive && displayMode == ChatComponent.DisplayMode.BACKGROUND) {
    ChatComponent.DisplayMode.FOREGROUND
} else {
    displayMode
}
