package com.skysoft.utils.chat

import com.skysoft.utils.SkysoftErrorBoundary
import com.skysoft.utils.SkysoftMessage
import com.skysoft.utils.SkysoftMessageSource
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents
import net.minecraft.network.chat.Component

object ChatEvents {
    private var visibleListeners: List<Listener<ChatMessage, ChatMessageVisibility>> = emptyList()
    private var actionBarListeners: List<Listener<SkysoftMessage, ChatMessageVisibility>> = emptyList()
    private var visibleGameModifiers: List<Listener<ChatMessage, Component>> = emptyList()
    private var actionBarModifiers: List<Listener<SkysoftMessage, Component>> = emptyList()
    private var registered = false

    fun onVisibleMessage(
        boundary: String,
        isActive: () -> Boolean,
        listener: (ChatMessage) -> ChatMessageVisibility,
    ) {
        register()
        visibleListeners += Listener(boundary, isActive, listener)
    }

    fun onMessageType(
        boundary: String,
        messageType: ChatMessageType,
        isActive: () -> Boolean,
        listener: (ChatMessage) -> ChatMessageVisibility,
    ) {
        onVisibleMessage(boundary, isActive) { message ->
            if (message.type == messageType) listener(message) else ChatMessageVisibility.SHOW
        }
    }

    fun onPartyMessage(
        boundary: String,
        isActive: () -> Boolean,
        listener: (ChatMessage) -> ChatMessageVisibility,
    ) {
        onMessageType(boundary, ChatMessageType.PARTY, isActive, listener)
    }

    fun onActionBar(
        boundary: String,
        isActive: () -> Boolean,
        listener: (SkysoftMessage) -> ChatMessageVisibility,
    ) {
        register()
        actionBarListeners += Listener(boundary, isActive, listener)
    }

    fun onVisibleGameMessageModify(
        boundary: String,
        isActive: () -> Boolean,
        modifier: (ChatMessage) -> Component,
    ) {
        register()
        visibleGameModifiers += Listener(boundary, isActive, modifier)
    }

    fun onActionBarModify(
        boundary: String,
        isActive: () -> Boolean,
        modifier: (SkysoftMessage) -> Component,
    ) {
        register()
        actionBarModifiers += Listener(boundary, isActive, modifier)
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
        message.source == SkysoftMessageSource.GAME && message.overlay && actionBarListeners.hasActiveListeners() ->
            dispatchActionBar(message)
        !message.overlay && visibleListeners.hasActiveListeners() -> dispatchVisible(ChatMessageClassifier.classify(message))
        else -> ChatMessageVisibility.SHOW
    }

    private fun modifyVisibleGameMessage(message: SkysoftMessage): Component =
        modifyGameMessage(message, visibleGameModifiers, ChatMessageClassifier::classify)

    private fun modifyActionBar(message: SkysoftMessage): Component =
        modifyGameMessage(message, actionBarModifiers) { it }

    private fun <T> modifyGameMessage(
        message: SkysoftMessage,
        modifiers: List<Listener<T, Component>>,
        prepare: (SkysoftMessage) -> T,
    ): Component = modifiers.fold(message.component) { component, modifier ->
        if (modifier.isActive()) {
            SkysoftErrorBoundary.value(modifier.boundary, component) {
                modifier.callback(prepare(SkysoftMessage(component, message.source, message.overlay)))
            }
        } else {
            component
        }
    }

    private fun dispatchVisible(message: ChatMessage): ChatMessageVisibility =
        dispatch(message, visibleListeners)

    private fun dispatchActionBar(message: SkysoftMessage): ChatMessageVisibility =
        dispatch(message, actionBarListeners)

    private fun <T> dispatch(
        message: T,
        listeners: List<Listener<T, ChatMessageVisibility>>,
    ): ChatMessageVisibility =
        listeners.fold(ChatMessageVisibility.SHOW) { result, listener ->
            if (listener.isActive()) {
                SkysoftErrorBoundary.value(listener.boundary, result) {
                    listener.callback(message).combine(result)
                }
            } else {
                result
            }
        }

    private fun <T, R> List<Listener<T, R>>.hasActiveListeners(): Boolean = any { it.isActive() }

    private fun ChatMessageVisibility.combine(previous: ChatMessageVisibility): ChatMessageVisibility =
        if (this == ChatMessageVisibility.HIDE) this else previous

    private data class Listener<T, R>(
        val boundary: String,
        val isActive: () -> Boolean,
        val callback: (T) -> R,
    )
}

enum class ChatMessageVisibility {
    SHOW,
    HIDE,
    ;

    val allowsMessage: Boolean
        get() = this == SHOW
}

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

internal object ChatMessageClassifier {
    fun classify(raw: SkysoftMessage): ChatMessage {
        val clean = raw.cleanText.trim()
        return messageTypePattern(ChatMessageType.PARTY, partyPattern, clean, raw)
            ?: messageTypePattern(ChatMessageType.GUILD, guildPattern, clean, raw)
            ?: messageTypePattern(ChatMessageType.COOP, coopPattern, clean, raw)
            ?: privateMessage(clean, raw)
            ?: allChatMessage(clean, raw)
            ?: ChatMessage(raw = raw, type = ChatMessageType.SYSTEM, body = clean)
    }

    private fun messageTypePattern(
        messageType: ChatMessageType,
        pattern: Regex,
        clean: String,
        raw: SkysoftMessage,
    ): ChatMessage? {
        val match = pattern.matchEntire(clean) ?: return null
        return ChatMessage(
            raw = raw,
            type = messageType,
            sender = senderFromPrefix(match.groups["sender"]?.value.orEmpty()),
            body = match.groups["body"]?.value.orEmpty().trim(),
        )
    }

    private fun senderFromPrefix(prefix: String): ChatMessageSender? {
        val withoutBracketGroups = bracketGroupPattern.replace(prefix, " ")
        val name = withoutBracketGroups
            .split(whitespacePattern)
            .lastOrNull { playerNamePattern.matchEntire(it) != null }
            ?: return null
        return ChatMessageSender(name, null)
    }

    private fun privateMessage(clean: String, raw: SkysoftMessage): ChatMessage? {
        val match = privatePattern.matchEntire(clean)
            ?.takeUnless { clean.startsWith("From stash:", ignoreCase = true) }
            ?: return null
        val direction = when (match.groups["direction"]?.value?.lowercase()) {
            "from" -> PrivateMessageDirection.FROM
            "to" -> PrivateMessageDirection.TO
            else -> null
        }
        return ChatMessage(
            raw = raw,
            type = ChatMessageType.PRIVATE_MESSAGE,
            sender = senderFromPrefix(match.groups["sender"]?.value.orEmpty()),
            body = match.groups["body"]?.value.orEmpty().trim(),
            privateMessageDirection = direction,
        )
    }

    private fun allChatMessage(clean: String, raw: SkysoftMessage): ChatMessage? {
        val match = allPattern.matchEntire(clean) ?: return null
        val sender = senderFromPrefix(match.groups["sender"]?.value.orEmpty()) ?: return null
        return ChatMessage(
            raw = raw,
            type = ChatMessageType.ALL,
            sender = sender,
            body = match.groups["body"]?.value.orEmpty().trim(),
        )
    }

    private val partyPattern = Regex("""^Party > (?<sender>.+?): (?<body>.*)$""")
    private val guildPattern = Regex("""^Guild > (?<sender>.+?): (?<body>.*)$""")
    private val coopPattern = Regex("""^Co-op > (?<sender>.+?): (?<body>.*)$""")
    private val privatePattern = Regex("""^(?<direction>From|To) (?<sender>.+?): (?<body>.*)$""")
    private val allPattern = Regex(
        """^(?<sender>(?:(?:\[[^]]+] )+(?:(?:[^\[\]A-Za-z0-9_\s:]+ )(?:\[[^]]+] )*)*)?""" +
            """[A-Za-z0-9_]{1,16}(?: \[[^]]+])?(?: [^A-Za-z0-9_\s:]+)*): (?<body>.*)$""",
    )
    private val bracketGroupPattern = Regex("""\[[^]]+]""")
    private val whitespacePattern = Regex("""\s+""")
    private val playerNamePattern = Regex("""[A-Za-z0-9_]{1,16}""")
}
