package com.skysoft.features.event.diana

import com.skysoft.config.DianaRareMobOption
import com.skysoft.utils.chat.ChatMessage
import com.skysoft.utils.chat.ChatMessageSender
import com.skysoft.utils.chat.ChatMessageVisibility
import com.skysoft.utils.chat.SkysoftPartyShare
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
    ): ChatMessageVisibility {
        val sender = DianaRareMobRuntime.senderFor(message, cocoon.marker)
            ?: return ChatMessageVisibility.SHOW
        if (DianaRareMobPartyEcho.shouldHideRecentlySent(message, sender, context.localPlayerName, context.now)) {
            return context.messageVisibility
        }
        return when {
            sender.isLocalPlayer(context.localPlayerName) -> ChatMessageVisibility.SHOW
            cocoon.mob !in context.receivedRareMobs -> ChatMessageVisibility.SHOW
            else -> {
                refreshRemoteCocoonTargets(targets, cocoon.mob, sender, context.now)
                if (context.showRareMobSharing) {
                    if (!context.showPartyMessages) {
                        SkysoftPartyShare.showCocoonReplacement(
                            sender = sender,
                            label = Component.literal(cocoon.mob.label).withStyle(ChatFormatting.LIGHT_PURPLE),
                        )
                    }
                    DianaRareMobTitleRenderer.showCocoon(cocoon.mob, sender)
                }
                context.messageVisibility
            }
        }
    }

    fun handleClear(
        message: ChatMessage,
        clear: DianaRareMobClear,
        context: Context,
        targets: Collection<DianaRareMobTarget>,
        clearTarget: (DianaRareMobTarget) -> Unit,
    ): ChatMessageVisibility {
        val sender = DianaRareMobRuntime.senderFor(message, clear.marker)
            ?: return ChatMessageVisibility.SHOW
        if (DianaRareMobPartyEcho.shouldHideRecentlySent(message, sender, context.localPlayerName, context.now)) {
            return context.messageVisibility
        }
        val clearedTargets = targets
            .filter { target ->
                (clear.mob == null || target.mob == clear.mob) &&
                    target.sharedBy.name.equals(sender.name, ignoreCase = true)
            }
            .toList()
        if (!sender.isLocalPlayer(context.localPlayerName)) {
            clearedTargets.forEach { target ->
                clearTarget(target)
            }
        }
        return context.messageVisibility
    }

    fun handleShare(
        message: ChatMessage,
        share: DianaRareMobShare,
        context: Context,
        rememberShare: (DianaRareMobShare, ChatMessageSender) -> Unit,
    ): ChatMessageVisibility {
        val sender = DianaRareMobRuntime.senderFor(message, share)
            ?: return ChatMessageVisibility.SHOW
        if (DianaRareMobPartyEcho.shouldHideRecentlySent(message, sender, context.localPlayerName, context.now)) {
            return context.messageVisibility
        }
        return when {
            share.mob !in context.receivedRareMobs -> ChatMessageVisibility.SHOW
            sender.isLocalPlayer(context.localPlayerName) -> ChatMessageVisibility.SHOW
            else -> {
                rememberShare(share, sender)
                if (context.showRareMobSharing) {
                    if (!context.showPartyMessages) {
                        SkysoftPartyShare.showFoundReplacement(
                            sender = sender,
                            label = Component.literal(share.mob.label).withStyle(ChatFormatting.LIGHT_PURPLE),
                            location = share.location,
                        )
                    }
                    DianaRareMobTitleRenderer.show(share.mob, sender)
                }
                context.messageVisibility
            }
        }
    }

    private val Context.messageVisibility: ChatMessageVisibility
        get() = if (showPartyMessages) ChatMessageVisibility.SHOW else ChatMessageVisibility.HIDE
}
