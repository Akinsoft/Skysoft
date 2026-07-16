package com.skysoft.features.chat

import net.minecraft.ChatFormatting
import net.minecraft.client.multiplayer.chat.GuiMessage
import net.minecraft.network.chat.Component

object ChatCompactor {
    private val entries = mutableMapOf<Component, Entry>()
    private var dividerBlock: MutableList<Entry>? = null
    private var messagesSincePrune = 0

    fun prepare(
        content: Component,
        messages: MutableList<GuiMessage>,
        nowMillis: Long = System.currentTimeMillis(),
    ): PreparedChatMessage = prepare(
        content,
        messages,
        nowMillis,
        ChatFeatureSettings.isCompactingEnabled(),
        ChatFeatureSettings.compactDurationMillis(),
    )

    internal fun prepare(
        content: Component,
        messages: MutableList<GuiMessage>,
        nowMillis: Long,
        isEnabled: Boolean,
        durationMillis: Long,
    ): PreparedChatMessage {
        if (!isEnabled) {
            clear()
            return PreparedChatMessage(content)
        }

        if (++messagesSincePrune >= PRUNE_INTERVAL_MESSAGES) {
            entries.values.removeIf { nowMillis - it.lastSeenMillis >= durationMillis }
            messagesSincePrune = 0
        }

        val isDivider = isDivider(content)
        val entry = entries[content]
            ?.takeUnless { isDivider || nowMillis - it.lastSeenMillis >= durationMillis }
            ?: Entry(isDivider).also { if (!isDivider) entries[content] = it }

        trackDividerBlock(entry, nowMillis)
        entry.count++
        entry.lastSeenMillis = nowMillis
        val removedPrevious = entry.count > 1 &&
            removeEntry(entry, messages, mutableSetOf()) == EntryRemovalResult.REMOVED
        val displayContent = if (entry.count > 1) {
            content.copy().append(
                Component.literal(" (${entry.count})").withStyle(ChatFormatting.GRAY),
            )
        } else {
            content
        }
        return PreparedChatMessage(displayContent, entry, removedPrevious)
    }

    fun associate(prepared: PreparedChatMessage, message: GuiMessage) {
        prepared.entry?.lastMessage = message
    }

    fun clear() {
        entries.clear()
        dividerBlock = null
        messagesSincePrune = 0
    }

    internal fun isBlank(content: Component): Boolean =
        ChatFormatting.stripFormatting(content.string).orEmpty().isBlank()

    private fun trackDividerBlock(entry: Entry, nowMillis: Long) {
        dividerBlock
            ?.takeIf { it.isNotEmpty() && nowMillis - it.first().lastSeenMillis >= DIVIDER_TIMEOUT_MILLIS }
            ?.let { dividerBlock = null }

        if (entry.isDivider) {
            val currentBlock = dividerBlock
            if (currentBlock == null) {
                dividerBlock = mutableListOf()
            } else {
                if (currentBlock.size >= MIN_DIVIDER_BLOCK_SIZE) {
                    currentBlock[1].dividers += currentBlock[0]
                    currentBlock.last().dividers += entry
                }
                dividerBlock = null
            }
        }
        dividerBlock?.add(entry)
    }

    private fun removeEntry(
        entry: Entry,
        messages: MutableList<GuiMessage>,
        visited: MutableSet<Entry>,
    ): EntryRemovalResult {
        if (!visited.add(entry)) return EntryRemovalResult.UNCHANGED
        var result = if (entry.lastMessage?.let(messages::remove) == true) {
            EntryRemovalResult.REMOVED
        } else {
            EntryRemovalResult.UNCHANGED
        }
        entry.lastMessage = null
        entry.dividers.forEach { divider ->
            if (removeEntry(divider, messages, visited) == EntryRemovalResult.REMOVED) {
                result = EntryRemovalResult.REMOVED
            }
        }
        entry.dividers.clear()
        return result
    }

    private fun isDivider(content: Component): Boolean {
        val text = ChatFormatting.stripFormatting(content.string).orEmpty()
        return text.length > MIN_DIVIDER_LENGTH && text.all { it == '-' || it == '=' || it == '▬' }
    }

    internal class Entry(
        val isDivider: Boolean,
        var count: Int = 0,
        var lastSeenMillis: Long = 0,
        var lastMessage: GuiMessage? = null,
        val dividers: MutableList<Entry> = mutableListOf(),
    )

    private enum class EntryRemovalResult {
        REMOVED,
        UNCHANGED,
    }

    private const val PRUNE_INTERVAL_MESSAGES = 100
    private const val DIVIDER_TIMEOUT_MILLIS = 5_000L
    private const val MIN_DIVIDER_LENGTH = 5
    private const val MIN_DIVIDER_BLOCK_SIZE = 2
}

class PreparedChatMessage internal constructor(
    val content: Component,
    internal val entry: ChatCompactor.Entry? = null,
    val removedPrevious: Boolean = false,
) {
    internal fun withContent(content: Component): PreparedChatMessage =
        PreparedChatMessage(content, entry, removedPrevious)
}
