package com.skysoft.features.chat

import com.skysoft.config.ChatTabChannel
import com.skysoft.utils.chat.HYPIXEL_CHAT_SEPARATOR
import com.skysoft.utils.identitySet
import net.minecraft.client.multiplayer.chat.GuiMessage

internal class ChatTabFeedbackTracker {
    private val visibleFeedback = identitySet<GuiMessage>()
    private val messagesBeforeAttempt = identitySet<GuiMessage>()
    private var state = FeedbackState.NONE

    fun recordPartyAttempt(existingMessages: Collection<GuiMessage>) {
        messagesBeforeAttempt.clear()
        messagesBeforeAttempt.addAll(existingMessages)
        visibleFeedback.removeIf { it !in messagesBeforeAttempt }
        state = FeedbackState.PARTY_LEADING_SEPARATOR
    }

    fun isVisible(channel: ChatTabChannel, message: GuiMessage, text: String): Boolean {
        if (channel != ChatTabChannel.PARTY) {
            clearPendingResponse()
            return false
        }
        if (message in visibleFeedback) return true
        if (state == FeedbackState.NONE || message in messagesBeforeAttempt) return false

        return when (state) {
            FeedbackState.NONE -> false
            FeedbackState.PARTY_LEADING_SEPARATOR ->
                didRecordExpectedLine(message, text, HYPIXEL_CHAT_SEPARATOR, FeedbackState.PARTY_ERROR)
            FeedbackState.PARTY_ERROR ->
                didRecordExpectedLine(message, text, PARTY_NOT_AVAILABLE_MESSAGE, FeedbackState.PARTY_TRAILING_SEPARATOR)
            FeedbackState.PARTY_TRAILING_SEPARATOR -> {
                val isSeparator = text == HYPIXEL_CHAT_SEPARATOR
                if (isSeparator) visibleFeedback.add(message)
                clearPendingResponse()
                isSeparator
            }
        }
    }

    fun clearPendingResponse() {
        state = FeedbackState.NONE
        messagesBeforeAttempt.clear()
    }

    private fun didRecordExpectedLine(
        message: GuiMessage,
        text: String,
        expected: String,
        nextState: FeedbackState,
    ): Boolean {
        if (text != expected) {
            clearPendingResponse()
            return false
        }
        visibleFeedback.add(message)
        state = nextState
        return true
    }

    private enum class FeedbackState {
        NONE,
        PARTY_LEADING_SEPARATOR,
        PARTY_ERROR,
        PARTY_TRAILING_SEPARATOR,
    }

    private companion object {
        const val PARTY_NOT_AVAILABLE_MESSAGE = "You are not in a party right now."
    }
}
