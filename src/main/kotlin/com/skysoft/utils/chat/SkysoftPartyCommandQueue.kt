package com.skysoft.utils.chat

internal class SkysoftPartyCommandQueue(
    private val partyCommand: (String) -> String,
    private val canSendParty: (Boolean) -> Boolean,
    private val rememberSentMessage: (String, Long) -> Unit,
) {
    private val queuedPartyMessages = ArrayDeque<QueuedPartyMessage>()
    private val pendingSentMessages = mutableListOf<PendingSentPartyMessage>()
    private var nextPartyCommandAtMillis = 0L

    fun enqueue(
        message: String,
        allowRecentPartyChatEvidence: Boolean,
        command: String? = null,
        requireParty: Boolean = true,
    ) {
        val trimmedMessage = message.trim()
        if (queuedPartyMessages.size >= MAX_QUEUED_PARTY_MESSAGES) {
            return
        }
        queuedPartyMessages += QueuedPartyMessage(
            trimmedMessage,
            allowRecentPartyChatEvidence,
            command,
            requireParty,
        )
    }

    fun nextPartyCommand(now: Long = System.currentTimeMillis()): String? {
        if (now < nextPartyCommandAtMillis) return null
        val queued = queuedPartyMessages.firstOrNull() ?: return null
        if (queued.requireParty && !canSendParty(queued.allowRecentPartyChatEvidence)) {
            queuedPartyMessages.removeFirst()
            return null
        }
        queuedPartyMessages.removeFirst()
        rememberSentMessage(queued.message, now)
        rememberPendingSentMessage(queued, now)
        nextPartyCommandAtMillis = now + PARTY_COMMAND_SPACING_MILLIS
        return queued.command ?: partyCommand(queued.message)
    }

    fun recordLocalPartyChat(now: Long = System.currentTimeMillis()) {
        val nextAllowedAtMillis = now + LOCAL_PARTY_CHAT_SPACING_MILLIS
        if (nextAllowedAtMillis <= nextPartyCommandAtMillis) return
        nextPartyCommandAtMillis = nextAllowedAtMillis
    }

    fun recordCommandCooldownFailure(
        cleanText: String,
        now: Long = System.currentTimeMillis(),
    ) {
        if (!isCommandCooldownFailure(cleanText)) return
        prunePendingSentMessages(now)
        val pending = pendingSentMessages.removeLastOrNull()?.queued ?: return
        if (pending.cooldownRetries >= MAX_COMMAND_COOLDOWN_RETRIES) {
            return
        }
        queuedPartyMessages.addFirst(
            pending.copy(cooldownRetries = pending.cooldownRetries + 1),
        )
        while (queuedPartyMessages.size > MAX_QUEUED_PARTY_MESSAGES) {
            queuedPartyMessages.removeLast()
        }
        nextPartyCommandAtMillis = maxOf(nextPartyCommandAtMillis, now + PARTY_COMMAND_COOLDOWN_RETRY_MILLIS)
    }

    fun recordPartyEcho(message: String, now: Long = System.currentTimeMillis()) {
        prunePendingSentMessages(now)
        val normalized = message.trim()
        val index = pendingSentMessages.indexOfFirst { sent -> sent.queued.message == normalized }
        if (index < 0) return
        pendingSentMessages.removeAt(index)
    }

    fun clear() {
        queuedPartyMessages.clear()
        pendingSentMessages.clear()
        nextPartyCommandAtMillis = 0L
    }

    private fun rememberPendingSentMessage(queued: QueuedPartyMessage, now: Long) {
        prunePendingSentMessages(now)
        pendingSentMessages += PendingSentPartyMessage(
            queued = queued,
            expiresAtMillis = now + PENDING_SENT_MESSAGE_MILLIS,
        )
    }

    private fun prunePendingSentMessages(now: Long) {
        pendingSentMessages.removeIf { sent -> now > sent.expiresAtMillis }
    }

    private fun isCommandCooldownFailure(cleanText: String): Boolean {
        val commandCooldownFailure = cleanText.startsWith("Command Failed:", ignoreCase = true) &&
            cleanText.contains("command is on cooldown", ignoreCase = true)
        val chatCooldownFailure = cleanText.contains("you can only chat every", ignoreCase = true)
        return commandCooldownFailure || chatCooldownFailure
    }

    private data class QueuedPartyMessage(
        val message: String,
        val allowRecentPartyChatEvidence: Boolean,
        val command: String?,
        val requireParty: Boolean,
        val cooldownRetries: Int = 0,
    )

    private data class PendingSentPartyMessage(
        val queued: QueuedPartyMessage,
        val expiresAtMillis: Long,
    )

    private companion object {
        const val PARTY_COMMAND_SPACING_MILLIS = 1_000L
        const val LOCAL_PARTY_CHAT_SPACING_MILLIS = 500L
        const val PARTY_COMMAND_COOLDOWN_RETRY_MILLIS = 1_250L
        const val PENDING_SENT_MESSAGE_MILLIS = 3_000L
        const val MAX_COMMAND_COOLDOWN_RETRIES = 2
        const val MAX_QUEUED_PARTY_MESSAGES = 5
    }
}
