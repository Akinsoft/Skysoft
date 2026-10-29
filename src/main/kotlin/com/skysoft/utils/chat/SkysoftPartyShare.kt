package com.skysoft.utils.chat

import com.skysoft.data.hypixel.HypixelPartyApi
import com.skysoft.utils.PlayerUtilities.localPlayerName
import com.skysoft.utils.WorldVec
import com.skysoft.utils.SkysoftChat
import com.skysoft.utils.SkysoftClientEvents
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component

object SkysoftPartyShare {
    private val recentSentMessages = mutableListOf<RecentSentPartyMessage>()
    private val commandQueue = SkysoftPartyCommandQueue(::canSendParty, ::rememberSentMessage)
    private var partyChatObservedUntilMillis = 0L
    private var wasActive = false

    fun register() {
        SkysoftClientEvents.onEndTick(
            "Party Share queue",
            isActive = { HypixelPartyApi.hasActiveConsumers || wasActive },
        ) { updateQueue() }
        SkysoftClientEvents.onDisconnect("Party Share disconnect reset", ::clearRecentSentMessages)
        ChatEvents.onVisibleMessage(
            "Share queue tracking",
            isActive = HypixelPartyApi::hasActiveConsumers,
        ) { message ->
            val now = System.currentTimeMillis()
            if (message.isSystemLike) commandQueue.recordCommandCooldownFailure(message.cleanText, now)
            if (message.type == ChatMessageType.PARTY || message.type == ChatMessageType.GUILD) {
                if (message.sender?.isLocalPlayer(localPlayerName()) == true) {
                    commandQueue.recordLocalPartyChat(now)
                    commandQueue.recordPartyEcho(message.body, now)
                }
            }
            ChatMessageVisibility.SHOW
        }
    }

    fun sendParty(message: String, allowRecentPartyChatEvidence: Boolean = false) {
        if (!canSendParty(allowRecentPartyChatEvidence)) return
        commandQueue.enqueue(message, allowRecentPartyChatEvidence)
    }

    fun sendGuild(message: String) {
        commandQueue.enqueue(
            message = message,
            allowRecentPartyChatEvidence = false,
            command = "gc ${message.trim()}",
            requireParty = false,
        )
    }

    fun markPartyChatObserved() {
        partyChatObservedUntilMillis = System.currentTimeMillis() + PARTY_CHAT_EVIDENCE_MILLIS
    }

    private fun canSendParty(allowRecentPartyChatEvidence: Boolean): Boolean =
        HypixelPartyApi.isLoaded && HypixelPartyApi.isInParty ||
            allowRecentPartyChatEvidence && System.currentTimeMillis() <= partyChatObservedUntilMillis

    private fun rememberSentMessage(message: String, now: Long) {
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

    private fun clearRecentSentMessages() {
        recentSentMessages.clear()
        commandQueue.clear()
        partyChatObservedUntilMillis = 0L
        wasActive = false
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

    private fun updateQueue() {
        if (!HypixelPartyApi.hasActiveConsumers) {
            clearRecentSentMessages()
            return
        }
        wasActive = true
        val connection = Minecraft.getInstance().connection ?: return
        val command = commandQueue.nextPartyCommand() ?: return
        connection.sendCommand(command)
    }

    private data class RecentSentPartyMessage(
        val message: String,
        val expiresAtMillis: Long,
    )

    private const val SENT_MESSAGE_ECHO_WINDOW_MILLIS = 5_000L
    private const val PARTY_CHAT_EVIDENCE_MILLIS = 10_000L
}
