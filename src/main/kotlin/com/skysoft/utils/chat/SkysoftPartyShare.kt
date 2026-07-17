package com.skysoft.utils.chat

import com.skysoft.data.hypixel.HypixelPartyApi
import com.skysoft.utils.WorldVec
import com.skysoft.utils.SkysoftChat
import com.skysoft.utils.SkysoftClientEvents
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component

object SkysoftPartyShare {
    private val recentSentMessages = mutableListOf<RecentSentPartyMessage>()
    private val commandQueue = SkysoftPartyCommandQueue(::partyCommand, ::partySendBlockedReason, ::rememberSentMessage)
    private var partyChatObservedUntilMillis = 0L

    fun register() {
        SkysoftClientEvents.onEndTick("Party Share queue") { sendNextQueuedPartyMessage() }
        SkysoftClientEvents.onDisconnect("Party Share disconnect reset", ::clearRecentSentMessages)
        ChatEvents.onVisibleMessage("Party Share sent-message tracking") { message ->
            recordCommandCooldownFailure(message.cleanText)
            ChatMessageVisibility.SHOW
        }
        ChatEvents.onPartyMessage("Party Share received-message tracking") { message ->
            message.sender?.takeIf { it.isLocalPlayerName(localPlayerName()) }?.let {
                commandQueue.recordLocalPartyChat()
                recordPartyEchoDelivered(message.body)
            }
            ChatMessageVisibility.SHOW
        }
    }

    fun partyCommand(message: String): String = "pc $message"

    fun sendParty(message: String, allowRecentPartyChatEvidence: Boolean = false) {
        if (partySendBlockedReason(allowRecentPartyChatEvidence) != null) return
        commandQueue.enqueue(message, allowRecentPartyChatEvidence)
    }

    fun markPartyChatObserved(now: Long = System.currentTimeMillis()) {
        partyChatObservedUntilMillis = now + PARTY_CHAT_EVIDENCE_MILLIS
    }

    internal fun partySendBlockedReason(allowRecentPartyChatEvidence: Boolean = false): String? = when {
        HypixelPartyApi.isLoaded && HypixelPartyApi.isInParty -> null
        allowRecentPartyChatEvidence && hasRecentPartyChatEvidence() -> null
        !HypixelPartyApi.isLoaded -> "party state is not loaded"
        else -> "player is not in a party"
    }

    internal fun nextPartyCommand(now: Long = System.currentTimeMillis()): String? =
        commandQueue.nextPartyCommand(now)

    internal fun recordCommandCooldownFailure(
        cleanText: String,
        now: Long = System.currentTimeMillis(),
    ): CommandCooldownRecoveryResult =
        commandQueue.recordCommandCooldownFailure(cleanText, now)

    internal fun recordPartyEchoDelivered(
        message: String,
        now: Long = System.currentTimeMillis(),
    ): PartyEchoDeliveryResult =
        commandQueue.recordPartyEcho(message, now)

    internal fun rememberSentMessage(message: String, now: Long = System.currentTimeMillis()) {
        pruneSentMessages(now)
        recentSentMessages += RecentSentPartyMessage(message.trim(), now + SENT_MESSAGE_ECHO_WINDOW_MILLIS)
    }

    internal fun consumeRecentSentMessage(message: String, now: Long = System.currentTimeMillis()): Boolean {
        pruneSentMessages(now)
        val normalized = message.trim()
        val index = recentSentMessages.indexOfFirst { sent -> sent.message == normalized }
        if (index < 0) return false
        recentSentMessages.removeAt(index)
        return true
    }

    internal fun clearRecentSentMessages() {
        recentSentMessages.clear()
        commandQueue.clear()
        partyChatObservedUntilMillis = 0L
    }

    fun showFoundReplacement(
        sender: ChatMessageSender,
        label: Component,
        location: WorldVec,
        detail: Component? = null,
    ) {
        val message = Component.empty()
            .append(sender.nameComponent())
            .append(Component.literal(" found a ").withStyle(ChatFormatting.GRAY))
            .append(label)
        if (detail != null) {
            message.append(Component.literal(" ").withStyle(ChatFormatting.GRAY))
                .append(detail)
        }
        message.append(
            Component.literal(" at ${location.x.toInt()} ${location.y.toInt()} ${location.z.toInt()}")
                .withStyle(ChatFormatting.GRAY),
        )
        SkysoftChat.chat(message)
    }

    fun showCocoonReplacement(sender: ChatMessageSender, label: Component) {
        SkysoftChat.chat(
            Component.empty()
                .append(sender.nameComponent())
                .append(Component.literal(" cocooned a ").withStyle(ChatFormatting.GRAY))
                .append(label),
        )
    }

    private fun pruneSentMessages(now: Long) {
        recentSentMessages.removeIf { sent -> now > sent.expiresAtMillis }
    }

    private fun sendNextQueuedPartyMessage() {
        val command = nextPartyCommand() ?: return
        Minecraft.getInstance().connection?.sendCommand(command)
    }

    private fun hasRecentPartyChatEvidence(now: Long = System.currentTimeMillis()): Boolean =
        now <= partyChatObservedUntilMillis

    private fun localPlayerName(): String? =
        runCatching { Minecraft.getInstance().player?.gameProfile?.name }.getOrNull()

    private fun ChatMessageSender.isLocalPlayerName(localPlayerName: String?): Boolean =
        localPlayerName != null && name.equals(localPlayerName, ignoreCase = true)

    private data class RecentSentPartyMessage(
        val message: String,
        val expiresAtMillis: Long,
    )

    private const val SENT_MESSAGE_ECHO_WINDOW_MILLIS = 5_000L
    private const val PARTY_CHAT_EVIDENCE_MILLIS = 10_000L
}
