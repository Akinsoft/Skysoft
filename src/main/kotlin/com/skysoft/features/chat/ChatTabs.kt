package com.skysoft.features.chat

import com.skysoft.config.ChatTabChannel
import com.skysoft.config.ChatTabPosition
import com.skysoft.config.SkysoftConfigGui
import com.skysoft.utils.MinecraftClient
import com.skysoft.utils.SkysoftMessage
import com.skysoft.utils.SkysoftMessageSource
import com.skysoft.utils.chat.ChatMessageClassifier
import com.skysoft.utils.chat.ChatMessageType
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.chat.GuiMessage
import net.minecraft.client.multiplayer.chat.GuiMessageSource

object ChatTabs {
    private var selectedChannel = ChatTabChannel.ALL
    private var appliedState: FilterState? = null

    fun register() {
        ClientTickEvents.END_CLIENT_TICK.register(::updateFilter)
    }

    fun isEnabled(): Boolean = SkysoftConfigGui.config().chat.tabs.enabled

    fun position(): ChatTabPosition = SkysoftConfigGui.config().chat.tabs.position

    fun channels(): List<ChatTabChannel> = SkysoftConfigGui.config().chat.tabs.channels.get().distinct()

    fun activeChannel(): ChatTabChannel {
        val channels = channels()
        selectedChannel = selectedChannel.takeIf(channels::contains) ?: channels.firstOrNull() ?: ChatTabChannel.ALL
        return selectedChannel
    }

    fun select(channel: ChatTabChannel) {
        selectedChannel = channel
        updateFilter(Minecraft.getInstance(), isForced = true)
    }

    internal fun isVisible(channel: ChatTabChannel, message: GuiMessage): Boolean {
        if (channel == ChatTabChannel.ALL) return true
        val content = ChatTimestamps.originalContent(message.content())
        if (message.source() == GuiMessageSource.SYSTEM_CLIENT &&
            content.string.startsWith(SKYSOFT_PREFIX)
        ) {
            return true
        }
        val type = ChatMessageClassifier.classify(
            SkysoftMessage(content, SkysoftMessageSource.GAME),
        ).type
        return when (channel) {
            ChatTabChannel.ALL -> true
            ChatTabChannel.GUILD -> type == ChatMessageType.GUILD
            ChatTabChannel.DM -> type == ChatMessageType.PRIVATE_MESSAGE
            ChatTabChannel.PARTY -> type == ChatMessageType.PARTY
        }
    }

    internal fun layout(
        position: ChatTabPosition,
        buttonWidths: List<Int>,
        guiHeight: Int,
        chatWidth: Int,
        chatHeight: Int,
    ): List<ChatTabBounds> {
        val chatBottom = guiHeight - CHAT_BOTTOM_MARGIN
        return when (position) {
            ChatTabPosition.ABOVE -> {
                var x = CHAT_LEFT
                buttonWidths.map { width ->
                    ChatTabBounds(x, (chatBottom - chatHeight - TAB_HEIGHT - TAB_GAP).coerceAtLeast(0), width, TAB_HEIGHT)
                        .also { x += width + TAB_GAP }
                }
            }
            ChatTabPosition.RIGHT -> {
                val totalHeight = buttonWidths.size * TAB_HEIGHT + (buttonWidths.size - 1).coerceAtLeast(0) * TAB_GAP
                var y = (chatBottom - maxOf(chatHeight, totalHeight)).coerceAtLeast(0)
                buttonWidths.map { width ->
                    ChatTabBounds(CHAT_LEFT + chatWidth + TAB_GAP, y, width, TAB_HEIGHT)
                        .also { y += TAB_HEIGHT + TAB_GAP }
                }
            }
        }
    }

    internal fun debugSummary(): String =
        "enabled=${isEnabled()} selected=${activeChannel()} position=${position()} channels=${channels().joinToString()}"

    private fun updateFilter(minecraft: Minecraft) {
        updateFilter(minecraft, isForced = false)
    }

    private fun updateFilter(minecraft: Minecraft, isForced: Boolean) {
        val state = FilterState(isEnabled(), activeChannel())
        if (!isForced && state == appliedState) return
        val chat = MinecraftClient.chat(minecraft)
        if (state.isEnabled) {
            chat.setVisibleMessageFilter { message -> isVisible(state.channel, message) }
            chat.resetChatScroll()
        } else if (appliedState?.isEnabled == true) {
            chat.setVisibleMessageFilter { true }
            chat.resetChatScroll()
        }
        appliedState = state
    }

    private data class FilterState(val isEnabled: Boolean, val channel: ChatTabChannel)

    private const val SKYSOFT_PREFIX = "[Skysoft] "
    private const val CHAT_LEFT = 4
    private const val CHAT_BOTTOM_MARGIN = 40
    private const val TAB_HEIGHT = 18
    private const val TAB_GAP = 2
}

data class ChatTabBounds(val x: Int, val y: Int, val width: Int, val height: Int)
