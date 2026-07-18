package com.skysoft.features.chat

import com.skysoft.config.ChatTabChannel
import java.util.Collections
import java.util.IdentityHashMap
import net.minecraft.client.multiplayer.chat.GuiMessage

internal class ChatTabFeedbackTracker {
    private val visibleFeedback = identitySet<GuiMessage>()
    private val messagesBeforeAttempt = identitySet<GuiMessage>()
    private var state = FeedbackState.NONE

    fun recordOutgoingAttempt(channel: ChatTabChannel, existingMessages: Collection<GuiMessage>) {
        val retainedFeedback = identitySet<GuiMessage>().apply { addAll(existingMessages) }
        visibleFeedback.removeIf { it !in retainedFeedback }
        state = if (channel == ChatTabChannel.PARTY) FeedbackState.PARTY_LEADING_SEPARATOR else FeedbackState.NONE
        messagesBeforeAttempt.clear()
        messagesBeforeAttempt.addAll(existingMessages)
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
                recordExpectedLine(message, text, PARTY_FEEDBACK_SEPARATOR) {
                    state = FeedbackState.PARTY_ERROR
                }.isVisible
            FeedbackState.PARTY_ERROR ->
                recordExpectedLine(message, text, PARTY_NOT_AVAILABLE_MESSAGE) {
                    state = FeedbackState.PARTY_TRAILING_SEPARATOR
                }.isVisible
            FeedbackState.PARTY_TRAILING_SEPARATOR -> {
                val isSeparator = text == PARTY_FEEDBACK_SEPARATOR
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

    private fun recordExpectedLine(
        message: GuiMessage,
        text: String,
        expected: String,
        onMatch: () -> Unit,
    ): FeedbackLineResult {
        if (text != expected) {
            clearPendingResponse()
            return FeedbackLineResult.REJECTED
        }
        visibleFeedback.add(message)
        onMatch()
        return FeedbackLineResult.ACCEPTED
    }

    private enum class FeedbackLineResult(val isVisible: Boolean) {
        ACCEPTED(true),
        REJECTED(false),
    }

    private enum class FeedbackState {
        NONE,
        PARTY_LEADING_SEPARATOR,
        PARTY_ERROR,
        PARTY_TRAILING_SEPARATOR,
    }

    companion object {
        internal const val PARTY_FEEDBACK_SEPARATOR = "-----------------------------------------------------"
        internal const val PARTY_NOT_AVAILABLE_MESSAGE = "You are not in a party right now."

        private fun <T> identitySet(): MutableSet<T> = Collections.newSetFromMap(IdentityHashMap())
    }
}
