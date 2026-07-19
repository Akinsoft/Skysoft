package com.skysoft.features.chat

import com.skysoft.config.ChatTabChannel
import java.util.Collections
import java.util.IdentityHashMap
import net.minecraft.client.multiplayer.chat.GuiMessage

internal class ChatTabCommandResponseTracker {
    private val routedMessages = ChatTabChannel.entries.associateWith { identitySet<GuiMessage>() }
    private val messagesBeforeCommand = identitySet<GuiMessage>()
    private var pendingResponse: PendingResponse? = null

    fun record(command: String, origin: ChatTabChannel, existingMessages: Collection<GuiMessage>) {
        val response = commandResponse(command)
        clearPendingResponse()
        if (response == null) return
        routedMessages.values.forEach { messages -> messages.removeIf { it !in existingMessages } }
        messagesBeforeCommand.clear()
        messagesBeforeCommand.addAll(existingMessages)
        pendingResponse = PendingResponse(
            response = response,
            origin = origin,
            phase = ResponsePhase.LEADING,
            expiresAtMillis = System.currentTimeMillis() + RESPONSE_TIMEOUT_MILLIS,
        )
    }

    fun observe(message: GuiMessage, text: String) {
        if (routedMessages.values.any { message in it } || message in messagesBeforeCommand) return
        val pending = pendingResponse ?: return
        if (System.currentTimeMillis() > pending.expiresAtMillis) {
            clearPendingResponse()
            return
        }

        when (pending.phase) {
            ResponsePhase.LEADING -> observeLeadingLine(pending, message, text)
            ResponsePhase.HEADER -> observeHeaderLine(pending, message, text)
            ResponsePhase.CONTENT -> observeContentLine(pending, message, text)
        }
    }

    fun isVisible(channel: ChatTabChannel, message: GuiMessage): Boolean = message in routedMessages.getValue(channel)

    fun clearPendingResponse() {
        pendingResponse = null
        messagesBeforeCommand.clear()
    }

    private fun observeLeadingLine(pending: PendingResponse, message: GuiMessage, text: String) {
        when {
            text == RESPONSE_SEPARATOR -> {
                route(pending, message)
                pending.phase = ResponsePhase.HEADER
            }
            pending.response.header.matches(text) -> {
                route(pending, message)
                pending.phase = ResponsePhase.CONTENT
            }
            pending.response.completeResponse.matches(text) -> {
                route(pending, message)
                clearPendingResponse()
            }
        }
    }

    private fun observeHeaderLine(pending: PendingResponse, message: GuiMessage, text: String) {
        when {
            text == RESPONSE_SEPARATOR -> route(pending, message)
            pending.response.header.matches(text) -> {
                route(pending, message)
                pending.phase = ResponsePhase.CONTENT
            }
            pending.response.completeResponse.matches(text) -> {
                route(pending, message)
                clearPendingResponse()
            }
            else -> clearPendingResponse()
        }
    }

    private fun observeContentLine(pending: PendingResponse, message: GuiMessage, text: String) {
        route(pending, message)
        pending.lineCount++
        if (text == RESPONSE_SEPARATOR || pending.lineCount >= MAX_RESPONSE_LINES) {
            clearPendingResponse()
        } else {
            pending.expiresAtMillis = System.currentTimeMillis() + RESPONSE_TIMEOUT_MILLIS
        }
    }

    private fun route(pending: PendingResponse, message: GuiMessage) {
        routedMessages.getValue(pending.response.channel).add(message)
        routedMessages.getValue(pending.origin).add(message)
    }

    private data class PendingResponse(
        val response: CommandResponse,
        val origin: ChatTabChannel,
        var phase: ResponsePhase,
        var expiresAtMillis: Long,
        var lineCount: Int = 0,
    )

    private data class CommandResponse(
        val channel: ChatTabChannel,
        val header: Regex,
        val completeResponse: Regex,
    )

    private enum class ResponsePhase {
        LEADING,
        HEADER,
        CONTENT,
    }

    companion object {
        private const val RESPONSE_SEPARATOR = "-----------------------------------------------------"
        private const val RESPONSE_TIMEOUT_MILLIS = 3_000L
        private const val MAX_RESPONSE_LINES = 100
        private val whitespacePattern = Regex("\\s+")
        private val friendListResponse = CommandResponse(
            ChatTabChannel.DM,
            Regex("^Friends(?: List)?(?: .*)?$"),
            Regex("^(?:You have no friends(?: online)?\\.|You don't have any friends.*)$"),
        )
        private val partyListResponse = CommandResponse(
            ChatTabChannel.PARTY,
            Regex("^Party (?:List|Leader|Moderators|Members)(?::| .*)?.*$"),
            Regex("^You are not (?:currently )?in a party(?: right now)?\\.$"),
        )
        private val guildListResponse = CommandResponse(
            ChatTabChannel.GUILD,
            Regex("^Guild (?:List|Name|Master|Members|Online Members|Information)(?::| .*)?.*$"),
            Regex("^You are not (?:currently )?in a guild\\.$"),
        )

        private fun commandResponse(command: String): CommandResponse? {
            val words = command.trim().lowercase().split(whitespacePattern)
            if (words.size < 2 || words[1] != "list") return null
            return when (words[0]) {
                "f", "friend", "friends" -> friendListResponse
                "p", "party" -> partyListResponse
                "g", "guild" -> guildListResponse
                else -> null
            }
        }

        private fun <T> identitySet(): MutableSet<T> = Collections.newSetFromMap(IdentityHashMap())
    }
}
