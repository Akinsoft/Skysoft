package com.skysoft.features.chat

import com.skysoft.config.SkysoftConfigGui
import com.skysoft.utils.input.InputUtilities
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.components.ChatComponent
import org.lwjgl.glfw.GLFW

internal enum class ChatPeekState {
    DISABLED,
    NO_PLAYER,
    KEY_UNBOUND,
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
            isKeyDown = { InputUtilities.isBindingDown(key) },
        )
    }
}

internal inline fun chatPeekState(
    isEnabled: Boolean,
    key: Int,
    hasPlayer: () -> Boolean,
    isKeyDown: () -> Boolean,
): ChatPeekState = when {
    !isEnabled -> ChatPeekState.DISABLED
    !hasPlayer() -> ChatPeekState.NO_PLAYER
    key == GLFW.GLFW_KEY_UNKNOWN -> ChatPeekState.KEY_UNBOUND
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
