package com.skysoft.data.skyblock

import com.skysoft.utils.ActiveListenerRegistry
import com.skysoft.utils.chat.ChatEvents
import com.skysoft.utils.chat.ChatMessageVisibility

internal class ParsedChatListeners<T : Any>(
    private val boundary: String,
    private val parse: (String) -> T?,
) {
    private val listeners = ActiveListenerRegistry<(T) -> Unit>()
    private var registered = false

    fun register(boundary: String, isActive: () -> Boolean, listener: (T) -> Unit) {
        if (!registered) registerChat()
        listeners.register(boundary, isActive, listener)
    }

    private fun registerChat() {
        registered = true
        ChatEvents.onVisibleMessage(boundary, { listeners.hasActiveListeners }) { message ->
            parse(message.cleanText)?.let { parsed ->
                listeners.forEachActive { listener -> listener(parsed) }
            }
            ChatMessageVisibility.SHOW
        }
    }
}
