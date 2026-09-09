package com.skysoft.utils.chat

import com.skysoft.utils.SkysoftMessage
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component

enum class ChatMessageType {
    SYSTEM,
    PARTY,
    GUILD,
    COOP,
    PRIVATE_MESSAGE,
    ALL,
    UNKNOWN,
}

enum class PrivateMessageDirection {
    FROM,
    TO,
}

data class ChatMessage(
    val raw: SkysoftMessage,
    val type: ChatMessageType,
    val sender: ChatMessageSender? = null,
    val body: String = raw.cleanText,
    val privateMessageDirection: PrivateMessageDirection? = null,
) {
    val component get() = raw.component
    val source get() = raw.source
    val overlay get() = raw.overlay
    val plainText get() = raw.plainText
    val cleanText get() = raw.cleanText
    val formattedText get() = raw.formattedText
    val cleanFormattedText get() = raw.cleanFormattedText
    val isSystemLike get() = type == ChatMessageType.SYSTEM || type == ChatMessageType.UNKNOWN
}

data class ChatMessageSender(
    val name: String,
    val color: Int?,
) {
    fun nameComponent(): Component {
        val component = Component.literal(name)
        return if (color != null) {
            component.withColor(color)
        } else {
            component.withStyle(ChatFormatting.WHITE)
        }
    }
}
