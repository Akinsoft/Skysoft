package com.skysoft.utils.chat

import com.skysoft.utils.ActiveListenerRegistry
import com.skysoft.utils.SkysoftErrorBoundary
import com.skysoft.utils.SkysoftMessage
import com.skysoft.utils.SkysoftMessageSource
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents
import net.minecraft.network.chat.Component

object ChatEvents {
    private val visibleListeners = ActiveListenerRegistry<(ChatMessage) -> ChatMessageVisibility>()
    private val actionBarListeners = ActiveListenerRegistry<(SkysoftMessage) -> ChatMessageVisibility>()
    private val visibleGameModifiers = ActiveListenerRegistry<(ChatMessage) -> Component>()
    private val actionBarModifiers = ActiveListenerRegistry<(SkysoftMessage) -> Component>()
    private var registered = false

    fun onVisibleMessage(
        boundary: String,
        isActive: () -> Boolean,
        listener: (ChatMessage) -> ChatMessageVisibility,
    ) {
        register()
        visibleListeners.register(boundary, isActive, listener)
    }

    fun onPartyMessage(
        boundary: String,
        isActive: () -> Boolean,
        listener: (ChatMessage) -> ChatMessageVisibility,
    ) {
        onVisibleMessage(boundary, isActive) { message ->
            if (message.type == ChatMessageType.PARTY) listener(message) else ChatMessageVisibility.SHOW
        }
    }

    fun onActionBar(
        boundary: String,
        isActive: () -> Boolean,
        listener: (SkysoftMessage) -> ChatMessageVisibility,
    ) {
        register()
        actionBarListeners.register(boundary, isActive, listener)
    }

    fun onVisibleGameMessageModify(
        boundary: String,
        isActive: () -> Boolean,
        modifier: (ChatMessage) -> Component,
    ) {
        register()
        visibleGameModifiers.register(boundary, isActive, modifier)
    }

    fun onActionBarModify(
        boundary: String,
        isActive: () -> Boolean,
        modifier: (SkysoftMessage) -> Component,
    ) {
        register()
        actionBarModifiers.register(boundary, isActive, modifier)
    }

    private fun register() {
        if (registered) return
        registered = true

        ClientReceiveMessageEvents.ALLOW_CHAT.register { message, _, _, _, _ ->
            SkysoftErrorBoundary.value("Chat message classification", true) {
                dispatchIncoming(SkysoftMessage(message, SkysoftMessageSource.CHAT)).allowsMessage
            }
        }
        ClientReceiveMessageEvents.ALLOW_GAME.register { message, overlay ->
            SkysoftErrorBoundary.value("Game message classification", true) {
                dispatchIncoming(SkysoftMessage(message, SkysoftMessageSource.GAME, overlay)).allowsMessage
            }
        }
        ClientReceiveMessageEvents.MODIFY_GAME.register { message, overlay ->
            SkysoftErrorBoundary.value("Game message modification", message) {
                val incoming = SkysoftMessage(message, SkysoftMessageSource.GAME, overlay)
                if (overlay) modifyActionBar(incoming) else modifyVisibleGameMessage(incoming)
            }
        }
    }

    private fun dispatchIncoming(message: SkysoftMessage): ChatMessageVisibility = when {
        message.source == SkysoftMessageSource.GAME && message.overlay && actionBarListeners.hasActiveListeners ->
            dispatchActionBar(message)
        !message.overlay && visibleListeners.hasActiveListeners -> dispatchVisible(ChatMessageClassifier.classify(message))
        else -> ChatMessageVisibility.SHOW
    }

    private fun modifyVisibleGameMessage(message: SkysoftMessage): Component =
        modifyGameMessage(message, visibleGameModifiers, ChatMessageClassifier::classify)

    private fun modifyActionBar(message: SkysoftMessage): Component =
        modifyGameMessage(message, actionBarModifiers) { it }

    private fun <T> modifyGameMessage(
        message: SkysoftMessage,
        modifiers: ActiveListenerRegistry<(T) -> Component>,
        prepare: (SkysoftMessage) -> T,
    ): Component = modifiers.foldActive(message.component) { component, modifier ->
        modifier(prepare(SkysoftMessage(component, message.source, message.overlay)))
    }

    private fun dispatchVisible(message: ChatMessage): ChatMessageVisibility =
        dispatch(message, visibleListeners)

    private fun dispatchActionBar(message: SkysoftMessage): ChatMessageVisibility =
        dispatch(message, actionBarListeners)

    private fun <T> dispatch(
        message: T,
        listeners: ActiveListenerRegistry<(T) -> ChatMessageVisibility>,
    ): ChatMessageVisibility =
        listeners.foldActive(ChatMessageVisibility.SHOW) { result, listener ->
            listener(message).combine(result)
        }

    private fun ChatMessageVisibility.combine(previous: ChatMessageVisibility): ChatMessageVisibility =
        if (this == ChatMessageVisibility.HIDE) this else previous
}

enum class ChatMessageVisibility {
    SHOW,
    HIDE,
    ;

    val allowsMessage: Boolean
        get() = this == SHOW
}
