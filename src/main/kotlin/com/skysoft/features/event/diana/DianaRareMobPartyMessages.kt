package com.skysoft.features.event.diana

import com.skysoft.config.DianaRareMobOption
import com.skysoft.utils.chat.ChatMessage
import com.skysoft.utils.chat.ChatMessageSender
import com.skysoft.utils.chat.ChatMessageVisibility
import com.skysoft.utils.chat.ChatSenderParser
import com.skysoft.utils.chat.SkysoftPartyShare
import com.skysoft.utils.chat.isLocalPlayer
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component

internal object DianaRareMobPartyMessages {
    data class Context(
        val localPlayerName: String?,
        val receivedRareMobs: Collection<DianaRareMobOption>,
        val showRareMobSharing: Boolean,
        val showPartyMessages: Boolean,
        val now: Long,
    )

    fun handleCocoon(
        message: ChatMessage,
        cocoon: DianaRareMobCocoon,
        context: Context,
        targets: Collection<DianaRareMobTarget>,
    ): ChatMessageVisibility = handleFromSender(message, cocoon.marker, context) { sender ->
        if (sender.isLocalPlayer(context.localPlayerName) || cocoon.mob !in context.receivedRareMobs) {
            return@handleFromSender ChatMessageVisibility.SHOW
        }
        refreshRemoteCocoonTargets(targets, cocoon.mob, sender, context.now)
        if (context.showRareMobSharing) {
            if (!context.showPartyMessages) {
                SkysoftPartyShare.showCocoonReplacement(sender, cocoon.mob.partyLabel())
            }
            DianaRareMobTitleRenderer.showCocoon(cocoon.mob, sender)
        }
        partyMessageVisibility(context.showPartyMessages)
    }

    fun handleClear(
        message: ChatMessage,
        clear: DianaRareMobClear,
        context: Context,
        targets: Collection<DianaRareMobTarget>,
        clearTarget: (DianaRareMobTarget) -> Unit,
    ): ChatMessageVisibility = handleFromSender(message, clear.marker, context) { sender ->
        if (!sender.isLocalPlayer(context.localPlayerName)) {
            targets
                .filter { target ->
                    (clear.mob == null || target.mob == clear.mob) &&
                        target.sharedBy.name.equals(sender.name, ignoreCase = true)
                }
                .forEach(clearTarget)
        }
        partyMessageVisibility(context.showPartyMessages)
    }

    fun handleShare(
        message: ChatMessage,
        share: DianaRareMobShare,
        context: Context,
        rememberShare: (DianaRareMobShare, ChatMessageSender) -> Unit,
    ): ChatMessageVisibility = handleFromSender(message, share.marker, context) { sender ->
        if (share.mob !in context.receivedRareMobs || sender.isLocalPlayer(context.localPlayerName)) {
            return@handleFromSender ChatMessageVisibility.SHOW
        }
        rememberShare(share, sender)
        if (context.showRareMobSharing) {
            if (!context.showPartyMessages) {
                SkysoftPartyShare.showFoundReplacement(sender, share.mob.partyLabel(), share.location)
            }
            DianaRareMobTitleRenderer.show(share.mob, sender)
        }
        partyMessageVisibility(context.showPartyMessages)
    }

    private inline fun handleFromSender(
        message: ChatMessage,
        marker: String,
        context: Context,
        handle: (ChatMessageSender) -> ChatMessageVisibility,
    ): ChatMessageVisibility {
        val sender = ChatSenderParser.senderBefore(message, marker) ?: return ChatMessageVisibility.SHOW
        if (isOwnPartyEcho(message, sender, context.localPlayerName, context.now)) {
            return partyMessageVisibility(context.showPartyMessages)
        }
        return handle(sender)
    }

    private fun DianaRareMobOption.partyLabel(): Component =
        Component.literal(label).withStyle(ChatFormatting.LIGHT_PURPLE)
}

internal fun isOwnPartyEcho(
    message: ChatMessage,
    sender: ChatMessageSender,
    localPlayerName: String?,
    now: Long,
): Boolean = sender.isLocalPlayer(localPlayerName) && SkysoftPartyShare.consumeRecentSentMessage(message.body, now)

internal fun partyMessageVisibility(showPartyMessages: Boolean): ChatMessageVisibility =
    if (showPartyMessages) ChatMessageVisibility.SHOW else ChatMessageVisibility.HIDE
